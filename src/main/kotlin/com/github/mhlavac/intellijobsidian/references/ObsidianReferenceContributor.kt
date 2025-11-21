package com.github.mhlavac.intellijobsidian.references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

/**
 * Contributes references for Obsidian-style [[WikiLinks]].
 *
 * This class detects patterns like:
 * - [[FileName]]
 * - [[Path/To/File]]
 * - [[FileName|Display Text]]
 *
 * And creates navigable references to the target files.
 *
 * NOTE: Markdown parses [[WikiLinks]] as SHORT_REFERENCE_LINK elements,
 * so we need to register on PARAGRAPH and check the text.
 */
class ObsidianReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register for Markdown PARAGRAPH elements
        // Markdown parses [[Link]] as SHORT_REFERENCE_LINK, so we check the full paragraph text
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(MarkdownElementTypes.PARAGRAPH),
            ObsidianReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY
        )

        // Also register for TEXT elements as a fallback for inline code or other contexts
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(MarkdownTokenTypes.TEXT),
            ObsidianReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY
        )
    }

    private class ObsidianReferenceProvider : PsiReferenceProvider() {
        // Pattern to match [[...]] with optional alias
        private val wikiLinkPattern = Regex("""\[\[([^\]]+)]]""")

        override fun getReferencesByElement(
            element: PsiElement,
            context: ProcessingContext
        ): Array<PsiReference> {
            val text = element.text ?: return PsiReference.EMPTY_ARRAY

            // Skip elements that definitely can't contain wiki links
            if (!text.contains("[[")) {
                return PsiReference.EMPTY_ARRAY
            }

            // Find all [[...]] patterns in the text
            val matches = wikiLinkPattern.findAll(text).toList()
            if (matches.isEmpty()) {
                return PsiReference.EMPTY_ARRAY
            }

            val references = mutableListOf<PsiReference>()

            matches.forEach { matchResult ->
                val linkContent = matchResult.groupValues[1]

                // Calculate the text range for the ENTIRE [[...]] including brackets
                // This makes the whole thing clickable
                val startOffset = matchResult.range.first  // Include opening [[
                val endOffset = matchResult.range.last + 1  // Include closing ]]

                val textRange = TextRange(startOffset, endOffset)

                // Create a reference for this link
                references.add(
                    ObsidianReference(
                        element = element,
                        textRange = textRange,
                        linkText = linkContent
                    )
                )
            }

            return references.toTypedArray()
        }
    }
}
