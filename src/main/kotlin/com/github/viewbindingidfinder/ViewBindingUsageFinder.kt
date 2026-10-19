package com.github.viewbindingidfinder

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.toUElement

/**
 * Finds code usages of a ViewBinding field, keeping ONLY those whose qualifier expression
 * is typed as the expected binding class.
 *
 * Example: for layout activity_main.xml and id text_title we look for `*.textTitle`
 * expressions where the receiver's type is `...ActivityMainBinding`.
 */
class ViewBindingUsageFinder(
    private val bindingClassSimpleName: String,
    private val fieldName: String
) {

    fun find(scopeElement: PsiElement): List<BindingUsage> {
        val project = scopeElement.project
        val scope = GlobalSearchScope.projectScope(project)
        val results = ArrayList<BindingUsage>()
        val helper = PsiSearchHelper.getInstance(project)

        // Fast, index-backed text search for the field name as an identifier in code,
        // then verify each candidate structurally via UAST.
        helper.processElementsWithWord(
            { element, _ ->
                ProgressManager.checkCanceled()
                processCandidate(element)?.let { results.add(it) }
                true
            },
            scope,
            fieldName,
            UsageSearchContext.IN_CODE,
            true
        )

        // De-duplicate by navigation element identity + kind.
        return results.distinctBy { it.navigationElement to it.kind }
    }

    private fun processCandidate(leaf: PsiElement): BindingUsage? {
        val uElement: UElement = leaf.toUElement() ?: return null

        // Find the simple-name reference that refers to our field.
        val nameRef = uElement as? USimpleNameReferenceExpression
            ?: uElement.getParentOfType(USimpleNameReferenceExpression::class.java, false)
            ?: return null

        if (nameRef.identifier != fieldName) return null

        // The access must be qualified: something.fieldName
        val qualified = nameRef.getQualifiedParentOrThis() as? UQualifiedReferenceExpression
            ?: return null

        // The selector of that qualified expression must be our field reference.
        if (qualified.selector !== nameRef) return null

        // Verify the receiver (qualifier) resolves to the expected binding type.
        if (!receiverMatchesBindingType(qualified.receiver)) return null

        val navElement = nameRef.sourcePsi ?: qualified.sourcePsi ?: return null
        val kind = classifyUsage(qualified)
        val text = navElement.containingFile?.let { enclosingLineText(qualified) } ?: navElement.text

        return BindingUsage(navElement, kind, text)
    }

    /** Check whether the qualifier expression's type is the expected binding class. */
    private fun receiverMatchesBindingType(receiver: UExpression?): Boolean {
        val type = receiver?.getExpressionType() ?: return false
        val canonical = type.canonicalText.substringBefore('<')
        val simple = canonical.substringAfterLast('.')
        return simple == bindingClassSimpleName
    }

    /**
     * Classify a `binding.field` access as READ / WRITE / SET by inspecting its parent.
     * - If the whole `binding.field` is the left side of an assignment -> WRITE.
     * - If the access is chained into a mutating call (`setX(..)`) or an assignment to a
     *   further property (`binding.field.text = ..`) -> SET.
     * - Otherwise -> READ.
     */
    private fun classifyUsage(access: UQualifiedReferenceExpression): UsageKind {
        val parent = access.uastParent

        // binding.field = value  -> WRITE (direct assignment of the field, rare)
        if (parent is UBinaryExpression &&
            parent.operator == UastBinaryOperator.ASSIGN &&
            parent.leftOperand === access
        ) {
            return UsageKind.WRITE
        }

        // binding.field.something ...
        if (parent is UQualifiedReferenceExpression && parent.receiver === access) {
            val grand = parent.uastParent
            // binding.field.prop = value -> SET
            if (grand is UBinaryExpression &&
                grand.operator == UastBinaryOperator.ASSIGN &&
                grand.leftOperand === parent
            ) {
                return UsageKind.SET
            }
            // binding.field.setXxx(..) -> SET
            val call = parent.selector as? UCallExpression
            val calleeName = call?.methodName
            if (calleeName != null && calleeName.startsWith("set") && calleeName.length > 3) {
                return UsageKind.SET
            }
        }

        return UsageKind.READ
    }

    private fun enclosingLineText(access: UElement): String {
        val psi = access.sourcePsi ?: return ""
        return psi.text.replace('\n', ' ').trim().take(120)
    }
}
