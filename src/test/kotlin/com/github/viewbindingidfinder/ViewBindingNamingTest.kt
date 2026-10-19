package com.github.viewbindingidfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewBindingNamingTest {

    @Test
    fun layoutToBindingClass() {
        assertEquals("ActivityMainBinding", ViewBindingNaming.layoutFileNameToBindingClassName("activity_main.xml"))
        assertEquals("FragmentUserListBinding", ViewBindingNaming.layoutFileNameToBindingClassName("fragment_user_list.xml"))
        assertEquals("ActivityMainBinding", ViewBindingNaming.layoutFileNameToBindingClassName("activity_main"))
        assertEquals("ItemBinding", ViewBindingNaming.layoutFileNameToBindingClassName("item.xml"))
    }

    @Test
    fun layoutBlank() {
        assertNull(ViewBindingNaming.layoutFileNameToBindingClassName(""))
        assertNull(ViewBindingNaming.layoutFileNameToBindingClassName("   "))
    }

    @Test
    fun idToField() {
        assertEquals("textTitle", ViewBindingNaming.idValueToBindingFieldName("@+id/text_title"))
        assertEquals("textTitle", ViewBindingNaming.idValueToBindingFieldName("@id/text_title"))
        assertEquals("textTitle", ViewBindingNaming.idValueToBindingFieldName("text_title"))
        assertEquals("recyclerView", ViewBindingNaming.idValueToBindingFieldName("@+id/recycler_view"))
        assertEquals("button", ViewBindingNaming.idValueToBindingFieldName("@+id/button"))
    }

    @Test
    fun idBlank() {
        assertNull(ViewBindingNaming.idValueToBindingFieldName(""))
        assertNull(ViewBindingNaming.idValueToBindingFieldName("@+id/"))
    }

    @Test
    fun normalizeId() {
        assertEquals("text_title", ViewBindingNaming.normalizeId("@+id/text_title"))
        assertEquals("text_title", ViewBindingNaming.normalizeId("@android:id/text_title"))
        assertEquals("text_title", ViewBindingNaming.normalizeId("text_title"))
    }
}
