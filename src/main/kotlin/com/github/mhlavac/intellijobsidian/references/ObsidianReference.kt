package com.github.mhlavac.intellijobsidian.references

import com.github.mhlavac.intellijobsidian.util.ObsidianPathResolver
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

/**
 * Represents a reference from an Obsidian-style [[WikiLink]] to a target file.
 *
 * This class handles:
 * - Resolving the link to the actual file
 * - Navigation (Ctrl+Click or Cmd+Click)
 * - Reference highlighting
 */
class ObsidianReference(
    element: PsiElement,
    textRange: TextRange,
    private val linkText: String
) : PsiReferenceBase<PsiElement>(element, textRange) {

    /**
     * Resolves the reference to the target PSI element (file).
     *
     * @return The resolved PsiFile, or null if not found
     */
    override fun resolve(): PsiElement? {
        return ObsidianPathResolver.findBestMatch(element.project, linkText)
    }

    /**
     * Gets all possible variants for auto-completion.
     * This is not used here as we have a dedicated CompletionContributor.
     */
    override fun getVariants(): Array<Any> {
        return emptyArray()
    }

    /**
     * Determines if the reference resolves to a valid target.
     * Used for highlighting unresolved references.
     */
    override fun isSoft(): Boolean {
        // Return false to show unresolved links as errors
        return false
    }
}
