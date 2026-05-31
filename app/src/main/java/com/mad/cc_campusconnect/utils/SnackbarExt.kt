package com.mad.cc_campusconnect.utils

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.mad.cc_campusconnect.R

/**
 * Themed Snackbar helpers. Call on any view that's part of the visible hierarchy
 * (e.g. binding.root). Pass [anchor] (such as a FAB) so the bar floats above it
 * instead of behind it.
 */
fun View.showSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    anchor: View? = null,
    actionText: String? = null,
    action: ((View) -> Unit)? = null
): Snackbar {
    val bar = Snackbar.make(this, message, duration)
    anchor?.let { bar.anchorView = it }                 // anchor above the FAB, etc.
    if (actionText != null && action != null) {
        bar.setAction(actionText, action)
        bar.setActionTextColor(ContextCompat.getColor(context, R.color.accent_teal))
    }
    bar.view.setBackgroundColor(ContextCompat.getColor(context, R.color.bg_card))
    bar.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
    bar.show()
    return bar
}

/** Success: teal accent text. */
fun View.showSuccess(message: String, anchor: View? = null) =
    showSnackbar(message, Snackbar.LENGTH_SHORT, anchor).also {
        it.setTextColor(ContextCompat.getColor(context, R.color.accent_teal))
    }

/** Error: longer, with a Dismiss action. */
fun View.showError(message: String, anchor: View? = null) =
    showSnackbar(
        message, Snackbar.LENGTH_LONG, anchor,
        actionText = "Dismiss", action = { /* auto-dismisses */ }
    )