package com.github.viewbindingidfinder

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.util.PsiNavigateUtil
import javax.swing.ListSelectionModel

/**
 * Right-click action on a layout XML file: finds the classes that actually use the layout's
 * generated ViewBinding class (e.g. `ActivityMainBinding`) and navigates to them.
 *
 * - If exactly one class uses the binding, it opens directly.
 * - If several do, a chooser popup lists them.
 *
 * "Uses" means the file references the binding class by simple name (import or otherwise),
 * which covers direct fields, delegated bindings and generic/abstract base-class wrappers.
 * The layout file itself and generated binding sources are excluded.
 *
 * Available both from the editor popup (inside an open layout) and from the Project view
 * popup (right-clicking the .xml file in the layout list).
 */
class FindBindingParentsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = resolveLayoutFile(e)
        e.presentation.isEnabledAndVisible = file != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vFile = resolveLayoutFile(e) ?: return

        val bindingClass = ViewBindingNaming.layoutFileNameToBindingClassName(vFile.name) ?: return
        val contextElement = e.getData(CommonDataKeys.PSI_FILE) as PsiElement?

        object : Task.Backgroundable(project, "Finding classes using $bindingClass", true) {
            private var parents: List<PsiFile> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                parents = ReadAction.compute<List<PsiFile>, RuntimeException> {
                    findBindingParents(project, bindingClass, vFile)
                }
            }

            override fun onSuccess() {
                showResults(e, bindingClass, parents, contextElement)
            }
        }.queue()
    }

    private fun findBindingParents(
        project: com.intellij.openapi.project.Project,
        bindingClass: String,
        layoutFile: VirtualFile
    ): List<PsiFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        val found = LinkedHashSet<PsiFile>()

        helper.processElementsWithWord(
            { element, _ ->
                val file = element.containingFile ?: return@processElementsWithWord true
                val vf = file.virtualFile
                // Exclude non-code files (the layout XML itself) and generated binding sources.
                if (vf != null && isCodeFile(vf) && !isGeneratedBinding(vf, bindingClass)) {
                    found.add(file)
                }
                true
            },
            scope,
            bindingClass,
            UsageSearchContext.IN_CODE,
            true
        )
        return found.toList()
    }

    private fun showResults(
        e: AnActionEvent,
        bindingClass: String,
        parents: List<PsiFile>,
        contextElement: PsiElement?
    ) {
        val project = e.project ?: return
        if (parents.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No classes use $bindingClass")
                .showInBestPositionFor(e.dataContext)
            return
        }
        if (parents.size == 1) {
            PsiNavigateUtil.navigate(parents.first())
            return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(parents)
            .setTitle("Classes using $bindingClass")
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setRenderer { _, value, _, _, _ ->
                val label = javax.swing.JLabel()
                label.icon = PluginIcons.Binding
                label.text = value.name
                label.isOpaque = true
                label
            }
            .setItemChosenCallback { file -> PsiNavigateUtil.navigate(file) }
            .createPopup()
            .showInBestPositionFor(e.dataContext)
    }

    /**
     * Resolve the target layout file from either the editor context (single VIRTUAL_FILE)
     * or the Project view context (which may expose VIRTUAL_FILE_ARRAY). Returns the file
     * only if it is a single layout XML; returns null otherwise (so the action hides).
     */
    private fun resolveLayoutFile(e: AnActionEvent): VirtualFile? {
        e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { if (isLayoutXml(it)) return it }
        val array = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (array != null && array.size == 1 && isLayoutXml(array[0])) return array[0]
        return null
    }

    private fun isLayoutXml(file: VirtualFile): Boolean {
        if (file.extension?.lowercase() != "xml") return false
        val parent = file.parent?.name ?: return false
        return parent == "layout" || parent.startsWith("layout-")
    }

    private fun isCodeFile(file: VirtualFile): Boolean {
        return when (file.extension?.lowercase()) {
            "kt", "java" -> true
            else -> false
        }
    }

    /** Skip the generated binding source file itself (e.g. ActivityMainBinding.java). */
    private fun isGeneratedBinding(file: VirtualFile, bindingClass: String): Boolean {
        val name = file.nameWithoutExtension
        return name == bindingClass
    }
}
