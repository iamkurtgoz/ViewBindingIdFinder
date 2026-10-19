package com.github.viewbindingidfinder

import com.intellij.psi.PsiElement

/** Whether a binding field access reads the view or writes/sets something on it. */
enum class UsageKind {
    /** The expression value is read, e.g. `binding.textTitle.text`. */
    READ,

    /** The binding field itself is assigned, e.g. (rare) `binding.textTitle = x`. */
    WRITE,

    /** A setter / mutating call is invoked on the view, e.g. `binding.textTitle.setText(..)`
     *  or `binding.textTitle.text = "..."`. */
    SET
}

/** A single resolved usage of a view through its ViewBinding field. */
data class BindingUsage(
    val navigationElement: PsiElement,
    val kind: UsageKind,
    val containingText: String
)
