package com.github.viewbindingidfinder

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JList
import javax.swing.DefaultListCellRenderer
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
        usages: List<BindingUsage>
    ) {
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

        val list = JList(usages.toTypedArray())
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                l: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean
            ): Component {
                val c = super.getListCellRendererComponent(l, value, index, selected, focus)
                val usage = value as? BindingUsage
                if (usage != null) {
                    icon = iconFor(usage.kind)
                    val file = usage.navigationElement.containingFile?.name ?: "?"
                    val line = lineNumber(usage)
                    text = "[${usage.kind}] $file:$line  —  ${usage.containingText}"
                }
                return c
            }
        }

        JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setTitle("ViewBinding usages of $bindingClassName.$fieldName")
            .setItemChoosenCallback {
                (list.selectedValue as? BindingUsage)?.let { navigateTo(it) }
            }
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
        val doc = com.intellij.psi.PsiDocumentManager.getInstance(psi.project).getDocument(file)
            ?: return -1
        return doc.getLineNumber(psi.textOffset) + 1
    }

    private fun navigateTo(usage: BindingUsage) {
        (usage.navigationElement as? Navigatable)?.let {
            if (it.canNavigate()) it.navigate(true)
        }
    }
}
