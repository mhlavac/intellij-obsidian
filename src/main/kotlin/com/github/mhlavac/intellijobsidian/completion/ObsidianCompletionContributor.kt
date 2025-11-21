package com.github.mhlavac.intellijobsidian.completion

import com.github.mhlavac.intellijobsidian.util.ObsidianPathResolver
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.openapi.project.Project

/**
 * Provides auto-completion for Obsidian-style [[WikiLinks]].
 *
 * When a user types [[, this contributor suggests all markdown files in the project.
 * Performance optimized: only scans when actually inside [[, with result caching.
 */
class ObsidianCompletionContributor : CompletionContributor() {

    init {
        // Trigger completion after typing [[
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            WikiLinkCompletionProvider()
        )
    }

    companion object {
        // Simple cache to avoid rescanning files on every keystroke
        private var cachedFiles: List<VirtualFile>? = null
        private var cacheProject: Project? = null

        fun getCachedMarkdownFiles(project: Project): List<VirtualFile> {
            // Invalidate cache if project changed
            if (cacheProject != project) {
                cachedFiles = null
                cacheProject = project
            }

            // Return cached result if available
            if (cachedFiles != null) {
                return cachedFiles!!
            }

            // Scan and cache using VFS search (catches @ and emoji files)
            val files = ObsidianPathResolver.getAllMarkdownFiles(project)
            cachedFiles = files
            return files
        }

        fun invalidateCache() {
            cachedFiles = null
        }
    }

    private class WikiLinkCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val position = parameters.position
            val text = position.containingFile.text
            val offset = parameters.offset

            // PERFORMANCE: Early exit if we're not inside [[ ]] brackets
            if (!isInsideWikiLink(text, offset)) {
                return
            }

            val project = position.project

            // Get the actual file being edited (use originalFile for the real path)
            val currentFile = parameters.originalFile.virtualFile

            // IMPORTANT: Detect the vault root from the current file, not from project.baseDir
            // This fixes multi-vault setups where project.baseDir != vault root
            val vaultRoot = ObsidianPathResolver.detectVaultRoot(currentFile)

            // PERFORMANCE: Use cached file list for THIS VAULT only
            val markdownFiles = ObsidianPathResolver.getAllMarkdownFilesInVault(project, vaultRoot)

            // PERFORMANCE: Limit results to prevent UI slowdown with large projects
            // IntelliJ will filter based on what the user types anyway
            val maxResults = 500
            var count = 0

            // Add each file as a completion option
            for (file in markdownFiles) {
                if (count >= maxResults) break

                val relativePath = ObsidianPathResolver.getRelativePathForDisplay(project, file)
                if (relativePath != null) {
                    val filename = file.nameWithoutExtension

                    // Create lookup element with filename (what gets inserted) and path (what's displayed as detail)
                    val lookupElement = LookupElementBuilder
                        .create(filename)  // This is what gets inserted: just the filename
                        .withPresentableText(filename)  // What's shown in bold in the popup
                        .withTailText("  $relativePath", true)  // Path shown in gray
                        .withIcon(AllIcons.FileTypes.Any_type)
                        .withCaseSensitivity(false)

                    result.addElement(lookupElement)
                    count++

                    // SPECIAL CHARACTER SUPPORT: If filename starts with @ or emoji,
                    // add another entry without the prefix so "Ar" matches "@Artur"
                    if (filename.isNotEmpty()) {
                        val firstChar = filename[0]
                        // Check if first char is @ or emoji/special unicode
                        if (firstChar == '@' || firstChar.code > 127 || !firstChar.isLetterOrDigit()) {
                            val filenameWithoutPrefix = filename.substring(1)
                            if (filenameWithoutPrefix.isNotEmpty() && count < maxResults) {
                                // Add alternate entry that matches without the prefix
                                val alternateLookup = LookupElementBuilder
                                    .create(filename)  // Still insert the FULL name with @
                                    .withPresentableText(filename)  // Show full name in popup
                                    .withLookupString(filenameWithoutPrefix)  // But ALSO match "Artur" for "@Artur"
                                    .withTailText("  $relativePath", true)
                                    .withIcon(AllIcons.FileTypes.Any_type)
                                    .withCaseSensitivity(false)

                                result.addElement(alternateLookup)
                                count++
                            }
                        }
                    }
                }
            }
        }

        /**
         * Checks if the current cursor position is inside a [[ ]] pair.
         */
        private fun isInsideWikiLink(text: String, offset: Int): Boolean {
            if (offset < 2) return false

            // Look backwards to find [[
            val beforeCursor = text.substring(0, offset)
            val lastOpenBracket = beforeCursor.lastIndexOf("[[")

            if (lastOpenBracket == -1) return false

            // Check if there's a closing ]] after the opening [[
            val afterOpen = text.substring(lastOpenBracket)
            val closeBracket = afterOpen.indexOf("]]")

            // We're inside if we found [[ and either no ]] or ]] is after the cursor
            return closeBracket == -1 || (lastOpenBracket + closeBracket) >= offset
        }
    }
}
