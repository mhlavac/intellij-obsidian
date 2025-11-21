package com.github.mhlavac.intellijobsidian.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Provides documentation tooltips for Obsidian wiki links.
 *
 * This prevents crashes when hovering over links to files with emoji
 * by stripping emoji characters that cause Java Swing rendering issues.
 */
class ObsidianDocumentationProvider : AbstractDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        // This method is called for Cmd+hover hints
        if (element !is PsiFile) {
            return null
        }

        // Return plain text (no HTML) to avoid Swing rendering issues with emoji
        val safeFileName = element.name.stripEmoji().trim()
        return safeFileName.ifEmpty { "Obsidian Note" }
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        // This method is called for F1 documentation popups
        if (element !is PsiFile) {
            return null
        }

        // Strip emoji to prevent Swing HTML rendering crashes
        val safeFileName = element.name.stripEmoji().trim()
        val safePath = element.virtualFile?.path?.stripEmoji() ?: ""

        // Build simple, well-formed HTML without emoji
        return buildString {
            append("<html><body>")
            append("<b>")
            append(safeFileName.htmlEscape())
            append("</b>")

            if (safePath.isNotEmpty()) {
                append("<br/>")
                append("<span style='color: gray;'>")
                append(safePath.htmlEscape())
                append("</span>")
            }

            append("</body></html>")
        }
    }

    private fun String.stripEmoji(): String {
        // Remove emoji and other problematic Unicode characters
        // This regex matches emoji, symbols, and other non-basic characters
        return this.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Zs}\\-_./]"), "")
    }

    private fun String.htmlEscape(): String {
        return this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
