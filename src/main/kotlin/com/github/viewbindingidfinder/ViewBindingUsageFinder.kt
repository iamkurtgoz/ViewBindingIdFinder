package com.github.viewbindingidfinder

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement

/**
 * Finds code usages of a ViewBinding field.
 *
 * Strategy (robust against generic/abstract binding wrappers):
 *  1. Index-backed word search for the field name (e.g. `bottomNavigation`) in code.
 *  2. For each hit, confirm it is a *qualified member access* like `something.bottomNavigation`
 *     (checked purely at the PSI level, so it does not depend on UAST type resolution).
 *  3. Accept it only if the enclosing file is bound to the expected binding class — i.e. the
 *     file imports or otherwise references the binding class by simple name
 *     (import `...databinding.ActivityMainBinding`, supertype arg `...<ActivityMainBinding>`,
 *     a declared field/variable of that type, `ActivityMainBinding::inflate`, etc.).
 *
 * This intentionally does NOT require the qualifier's static type to resolve to the concrete
 * binding class, because with abstract wrappers such as
 * `abstract class ViewBindingActivity<VB : ViewBinding> { val binding: VB }` the qualifier's
 * type is only `ViewBinding` / a type parameter, never `ActivityMainBinding`.
 */
class ViewBindingUsageFinder(
    private val bindingClassSimpleName: String,
    private val fieldName: String
) {

    private val bindingWordRegex = Regex("\\b" + Regex.escape(bindingClassSimpleName) + "\\b")

    fun find(scopeElement: PsiElement): List<BindingUsage> {
        val project = scopeElement.project
        val scope = GlobalSearchScope.projectScope(project)
        val results = ArrayList<BindingUsage>()
        val helper = PsiSearchHelper.getInstance(project)
        val fileBindingCache = HashMap<PsiFile, Boolean>()

        helper.processElementsWithWord(
            { element, _ ->
                ProgressManager.checkCanceled()
                processCandidate(element, fileBindingCache)?.let { results.add(it) }
                true
            },
            scope,
            fieldName,
            UsageSearchContext.IN_CODE,
            true
        )

        // De-duplicate by physical location (file + start offset). The index can surface the
        // same identifier more than once (e.g. as different PSI/UAST leaves), so identity-based
        // dedup is not enough — key on the actual source position instead.
        return results.distinctBy {
            val psi = it.navigationElement
            val path = psi.containingFile?.virtualFile?.path ?: psi.containingFile?.name ?: ""
            path to psi.textRange?.startOffset
        }
    }

    private fun processCandidate(
        leaf: PsiElement,
        fileBindingCache: HashMap<PsiFile, Boolean>
    ): BindingUsage? {
        // 1) Leaf text must be exactly the field name.
        if (leaf.text != fieldName) return null

        // 2) Must be a qualified member access `<receiver>.fieldName` (PSI-level check,
        //    independent of UAST type resolution).
        if (!isDotQualified(leaf)) return null

        // 3) The enclosing file must be bound to the expected binding class.
        val file = leaf.containingFile ?: return null
        val bound = fileBindingCache.getOrPut(file) { fileIsBoundToBindingClass(file) }
        if (!bound) return null

        val kind = classifyUsage(leaf)
        val text = enclosingLineText(leaf)
        return BindingUsage(leaf, kind, text)
    }

    /**
     * Pure-PSI check: is the previous meaningful token a dot, i.e. is this identifier the
     * `fieldName` in `receiver.fieldName`? Works for both Kotlin and Java without UAST.
     */
    private fun isDotQualified(leaf: PsiElement): Boolean {
        // Check previous sibling of the leaf, skipping whitespace/comments.
        var prev: PsiElement? = leaf.prevSibling
        while (prev != null && prev.text.isBlank()) prev = prev.prevSibling
        if (prev != null && prev.text == ".") return true

        // The dot may be a sibling of the parent reference expression instead.
        val parent = leaf.parent ?: return false
        var pprev: PsiElement? = parent.prevSibling
        while (pprev != null && pprev.text.isBlank()) pprev = pprev.prevSibling
        return pprev != null && pprev.text == "."
    }

    /**
     * Returns true if [file] references the expected binding class by simple name anywhere:
     * an import, a supertype type-argument, a declared field/variable, a `::inflate`
     * reference, etc. Word-boundary matched to avoid partial hits.
     */
    private fun fileIsBoundToBindingClass(file: PsiFile): Boolean {
        val text = file.text ?: return false
        return bindingWordRegex.containsMatchIn(text)
    }

    /**
     * Classify a `receiver.field` access as READ / WRITE / SET. Best-effort via UAST; if the
     * UAST shape is unavailable or unexpected, default to READ (the usage is still kept).
     */
    private fun classifyUsage(leaf: PsiElement): UsageKind {
        val uElement: UElement = leaf.toUElement() ?: return UsageKind.READ
        val nameRef = uElement as? USimpleNameReferenceExpression
            ?: uElement.getParentOfType(USimpleNameReferenceExpression::class.java, false)
            ?: return UsageKind.READ
        val access = nameRef.uastParent as? UQualifiedReferenceExpression ?: return UsageKind.READ
        val parent = access.uastParent

        // receiver.field = value  -> WRITE
        if (parent is UBinaryExpression &&
            parent.operator == UastBinaryOperator.ASSIGN &&
            parent.leftOperand === access
        ) {
            return UsageKind.WRITE
        }

        // receiver.field.something ...
        if (parent is UQualifiedReferenceExpression && parent.receiver === access) {
            val grand = parent.uastParent
            // receiver.field.prop = value -> SET
            if (grand is UBinaryExpression &&
                grand.operator == UastBinaryOperator.ASSIGN &&
                grand.leftOperand === parent
            ) {
                return UsageKind.SET
            }
            // receiver.field.setXxx(..) -> SET
            val call = parent.selector as? UCallExpression
            val calleeName = call?.methodName
            if (calleeName != null && calleeName.startsWith("set") && calleeName.length > 3) {
                return UsageKind.SET
            }
        }

        return UsageKind.READ
    }

    private fun enclosingLineText(leaf: PsiElement): String {
        var node: PsiElement? = leaf
        var best: PsiElement = leaf
        var guard = 0
        while (node != null && guard < 6) {
            val t = node.text
            val bestLen = best.text?.length ?: 0
            if (t != null && t.length in bestLen..200) best = node
            node = node.parent
            guard++
        }
        return (best.text ?: leaf.text ?: "").replace('\n', ' ').trim().take(160)
    }
}
