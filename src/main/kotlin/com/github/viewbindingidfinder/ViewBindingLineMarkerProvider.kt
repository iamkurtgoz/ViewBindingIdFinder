package com.github.viewbindingidfinder

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType

/**
 * Adds a gutter icon next to every `android:id` attribute value in a layout XML file.
 * Clicking the icon lists only the code usages that access the view through the matching
 * generated ViewBinding class, marking read vs write/set usages with distinct icons.
 */
class ViewBindingLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Anchor on the leaf token inside the attribute value (platform requires leaf elements).
        if (element !is XmlToken || element.tokenType != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return null
        }

        val attrValue = element.parent as? XmlAttributeValue ?: return null
        val attribute = attrValue.parent as? XmlAttribute ?: return null
        if (attribute.name != "android:id") return null
        if (!isLayoutFile(element)) return null

        val idValue = attrValue.value ?: return null
        val fieldName = ViewBindingNaming.idValueToBindingFieldName(idValue) ?: return null

        val layoutFileName = element.containingFile?.name ?: return null
        val bindingClassName =
            ViewBindingNaming.layoutFileNameToBindingClassName(layoutFileName) ?: return null

        val tooltip = "ViewBinding usages via $bindingClassName.$fieldName"

        return LineMarkerInfo(
            element,
            element.textRange,
            PluginIcons.Binding,
            { tooltip },
            BindingGutterNavigationHandler(bindingClassName, fieldName),
            GutterIconRenderer.Alignment.LEFT,
            { tooltip }
        )
    }

    private fun isLayoutFile(element: PsiElement): Boolean {
        val vFile = element.containingFile?.virtualFile ?: return true // in-memory/test files
        val parentDir = vFile.parent?.name ?: return false
        return parentDir == "layout" || parentDir.startsWith("layout-")
    }
}
