package com.github.viewbindingidfinder

import com.intellij.openapi.util.IconLoader

object PluginIcons {
    /** Generic marker shown on the XML id line. */
    @JvmField
    val Binding = IconLoader.getIcon("/icons/binding.svg", PluginIcons::class.java)

    /** Marker emphasising read usages. */
    @JvmField
    val Read = IconLoader.getIcon("/icons/read.svg", PluginIcons::class.java)

    /** Marker emphasising write / set usages. */
    @JvmField
    val Write = IconLoader.getIcon("/icons/write.svg", PluginIcons::class.java)
}
