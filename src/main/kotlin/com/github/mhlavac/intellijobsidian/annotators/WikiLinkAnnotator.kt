package com.github.mhlavac.intellijobsidian.annotators

import com.github.mhlavac.intellijobsidian.util.ObsidianPathResolver
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Annotator that highlights Obsidian-style [[WikiLinks]] in the editor.
 *
 * This makes WikiLinks:
 * - Appear as hyperlinks (blue, underlined) when they resolve to a file
 * - Show as errors (red, squiggly) if they don't resolve
 * - Clickable with Cmd+Click (handled by GotoDeclarationHandler)
 */
class WikiLinkAnnotator : Annotator {

    companion object {
        // Pattern to match [[...]] with optional alias
        private val WIKI_LINK_PATTERN = Regex("""\[\[([^\]]+)]]""")
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text ?: return

        // Only process elements that might contain wiki links
        if (!text.contains("[[") || !text.contains("]]")) {
            return
        }

        // Check if this is a leaf element or contains a complete pattern
        val isLeafElement = element.firstChild == null
        val hasCompletePattern = WIKI_LINK_PATTERN.containsMatchIn(text)

        if (!isLeafElement && !hasCompletePattern) {
            return
        }

        // Find all [[...]] patterns in the text
        WIKI_LINK_PATTERN.findAll(text).forEach { matchResult ->
            val linkContent = matchResult.groupValues[1]

            // Calculate the text range for the entire link including [[ and ]]
            val startOffset = element.textRange.startOffset + matchResult.range.first
            val endOffset = element.textRange.startOffset + matchResult.range.last + 1

            val textRange = TextRange(startOffset, endOffset)

            // Try to resolve the link
            val resolved = ObsidianPathResolver.findBestMatch(element.project, linkContent)

            if (resolved != null) {
                // Link is valid - highlight as hyperlink (blue, underlined)
                // Use enforcedTextAttributes to override Markdown's styling
                val hyperlinkAttributes = TextAttributes()
                hyperlinkAttributes.foregroundColor = JBColor(
                    Color(0x00, 0x66, 0xCC),  // Light theme: bright blue
                    Color(0x58, 0x9D, 0xF6)   // Dark theme: lighter blue
                )
                hyperlinkAttributes.effectType = EffectType.LINE_UNDERSCORE
                hyperlinkAttributes.effectColor = hyperlinkAttributes.foregroundColor

                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(textRange)
                    .enforcedTextAttributes(hyperlinkAttributes)
                    .create()
            } else {
                // Link is broken - show as error (red with wave underline)
                holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve wiki link: [[$linkContent]]")
                    .range(textRange)
                    .create()
            }
        }
    }
}
