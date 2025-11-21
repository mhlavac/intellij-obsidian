package com.github.mhlavac.intellijobsidian.navigation

import com.github.mhlavac.intellijobsidian.util.ObsidianPathResolver
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Handles Cmd+Click (Ctrl+Click) navigation for Obsidian WikiLinks.
 *
 * This handler intercepts click events and provides navigation to target files
 * when the user clicks on [[WikiLink]] text.
 */
class ObsidianGotoDeclarationHandler : GotoDeclarationHandler {

    private val wikiLinkPattern = Regex("""\[\[([^\]]+)]]""")

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null || editor == null) return null

        // Get the current file to detect vault root
        val currentFile = sourceElement.containingFile?.virtualFile

        // Detect vault root for multi-vault support
        val vaultRoot = ObsidianPathResolver.detectVaultRoot(currentFile)

        // Strategy: Get the full line text from the editor and check if cursor is inside [[...]]
        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))

        // Find all [[...]] patterns in the line
        val matches = wikiLinkPattern.findAll(lineText).toList()

        // Check if the cursor offset is inside any [[...]] pattern
        for (match in matches) {
            val matchStartInLine = match.range.first
            val matchEndInLine = match.range.last + 1
            val matchStartAbsolute = lineStartOffset + matchStartInLine
            val matchEndAbsolute = lineStartOffset + matchEndInLine

            if (offset >= matchStartAbsolute && offset <= matchEndAbsolute) {
                val linkText = match.groupValues[1]

                // Resolve the link within the detected vault
                val targetFile = ObsidianPathResolver.findBestMatchInVault(
                    sourceElement.project,
                    linkText,
                    vaultRoot
                )

                if (targetFile != null) {
                    return arrayOf(targetFile)
                }
            }
        }

        return null
    }
}
