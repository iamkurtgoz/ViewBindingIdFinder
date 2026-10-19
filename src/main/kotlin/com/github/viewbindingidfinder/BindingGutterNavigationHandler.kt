package com.github.viewbindingidfinder

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.PsiNavigateUtil
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.ListSelectionModel

/**
 * Handles clicks on the ViewBinding gutter icon. Resolves usages in a background task
 * (read action), then shows a popup list where READ and WRITE/SET usages have distinct icons.
 */
class BindingGutterNavigationHandler(
    private val bindingClassName: String,
    private val fieldName: String
) : GutterIconNavigationHandler<PsiElement> {

    override fun navigate(e: MouseEvent, anchor: PsiElement) {
        val project = anchor.project

        object : Task.Backgroundable(project, "Finding ViewBinding usages of $fieldName", true) {
            private var usages: List<BindingUsage> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                usages = ReadAction.compute<List<BindingUsage>, RuntimeException> {
                    if (!anchor.isValid) return@compute emptyList()
                    ViewBindingUsageFinder(bindingClassName, fieldName).find(anchor)
                }
            }

            override fun onSuccess() {
                showResults(e, usages)
            }
        }.queue()
    }

    private fun showResults(
        e: MouseEvent,
        rawUsages: List<BindingUsage>
    ) {
        // Collapse to one entry per (file, line): the user never wants the same line twice.
        val usages = rawUsages.distinctBy {
            val file = it.navigationElement.containingFile?.name ?: "?"
            file to lineNumber(it)
        }

        if (usages.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No usages found via $bindingClassName.$fieldName")
                .show(RelativePoint(e))
            return
        }

        if (usages.size == 1) {
            navigateTo(usages.first())
            return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(usages)
            .setTitle("ViewBinding usages of $bindingClassName.$fieldName")
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setRenderer { _, value, _, _, _ ->
                val label = javax.swing.JLabel()
                label.icon = iconFor(value.kind)
                val file = value.navigationElement.containingFile?.name ?: "?"
                val line = lineNumber(value)
                label.text = "$file:$line"
                label.isOpaque = true
                label
            }
            .setItemChosenCallback { usage -> navigateTo(usage) }
            .createPopup()
            .show(RelativePoint(e))
    }

    private fun iconFor(kind: UsageKind): Icon = when (kind) {
        UsageKind.READ -> AllIcons.Actions.Preview
        UsageKind.WRITE, UsageKind.SET -> AllIcons.Actions.Edit
    }

    private fun lineNumber(usage: BindingUsage): Int {
        val psi = usage.navigationElement
        val file = psi.containingFile ?: return -1
        val doc = PsiDocumentManager.getInstance(psi.project).getDocument(file) ?: return -1
        return doc.getLineNumber(psi.textOffset) + 1
    }

    /**
     * Navigate using the platform helper instead of invoking the @OverrideOnly
     * Navigatable.navigate(boolean) directly.
     */
    private fun navigateTo(usage: BindingUsage) {
        PsiNavigateUtil.navigate(usage.navigationElement)
    }
}
