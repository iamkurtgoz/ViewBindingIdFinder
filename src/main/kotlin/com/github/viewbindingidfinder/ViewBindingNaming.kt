package com.github.viewbindingidfinder

/**
 * Pure naming logic that mirrors how the Android ViewBinding generator names things.
 *
 * Layout file name -> binding class name:
 *   activity_main.xml       -> ActivityMainBinding
 *   fragment_user_list.xml  -> FragmentUserListBinding
 *
 * Android id -> binding field name (lowerCamelCase of the id, '_' separated):
 *   text_title -> textTitle
 *   my_RecyclerView -> myRecyclerView  (each '_' separated segment is camel-joined)
 *
 * References:
 *  - View Binding generates a binding class per layout, named by converting the layout
 *    file name to Pascal case and appending "Binding".
 *  - View ids are exposed as fields using camelCase of the id.
 */
object ViewBindingNaming {

    /**
     * Convert a layout file name (with or without the ".xml" extension) to the generated
     * ViewBinding class simple name. Returns null if the name is blank.
     */
    fun layoutFileNameToBindingClassName(fileName: String): String? {
        val base = fileName.substringBeforeLast('.').trim()
        if (base.isEmpty()) return null
        val pascal = toPascalCase(base)
        if (pascal.isEmpty()) return null
        return pascal + "Binding"
    }

    /**
     * Convert an android:id value (e.g. "@+id/text_title", "@id/text_title" or "text_title")
     * to the generated binding field name (e.g. "textTitle"). Returns null if blank.
     */
    fun idValueToBindingFieldName(idValue: String): String? {
        val raw = normalizeId(idValue) ?: return null
        return toCamelCase(raw)
    }

    /**
     * Strip the "@+id/", "@id/", "@android:id/" prefixes from an id attribute value,
     * returning just the id name. Returns null if blank.
     */
    fun normalizeId(idValue: String): String? {
        var v = idValue.trim()
        if (v.isEmpty()) return null
        val slash = v.lastIndexOf('/')
        if (slash >= 0) {
            v = v.substring(slash + 1)
        }
        v = v.trim()
        return v.ifEmpty { null }
    }

    private fun toPascalCase(underscored: String): String {
        return underscored.split('_')
            .filter { it.isNotEmpty() }
            .joinToString("") { capitalizeFirst(it) }
    }

    private fun toCamelCase(underscored: String): String {
        val parts = underscored.split('_').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        val first = parts.first()
        // The first segment keeps its original casing except the very first char is lowercased.
        val head = lowercaseFirst(first)
        val tail = parts.drop(1).joinToString("") { capitalizeFirst(it) }
        return head + tail
    }

    private fun capitalizeFirst(s: String): String {
        if (s.isEmpty()) return s
        return s[0].uppercaseChar() + s.substring(1)
    }

    private fun lowercaseFirst(s: String): String {
        if (s.isEmpty()) return s
        return s[0].lowercaseChar() + s.substring(1)
    }
}
