package com.github.mhlavac.intellijobsidian.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Utility for resolving Obsidian-style links to files.
 *
 * Implements Obsidian's path resolution algorithm:
 * 1. If the link includes a path (e.g., "Folder/Note"), match exact path structure
 * 2. If the link is just a filename, prefer the file with the shortest absolute path
 *
 * MULTI-VAULT SUPPORT:
 * Detects the Obsidian vault root from the current file to support multiple vaults in one IDE.
 */
object ObsidianPathResolver {

    /**
     * Detects the Obsidian vault root directory from a given file.
     *
     * Strategy:
     * 1. Look for .obsidian folder walking up from current file
     * 2. If not found, use a heuristic (directory with many .md files)
     * 3. Fallback: use parent directory
     *
     * @param file The current file being edited
     * @return The detected vault root directory, or null if can't determine
     */
    fun detectVaultRoot(file: VirtualFile?): VirtualFile? {
        if (file == null) return null

        var current: VirtualFile? = if (file.isDirectory) file else file.parent
        var levelsUp = 0
        val maxLevels = 10  // Don't go too far up

        // Walk up looking for .obsidian folder (Obsidian vault marker)
        while (current != null && levelsUp < maxLevels) {
            // Check if this directory has .obsidian folder
            val obsidianDir = current.findChild(".obsidian")
            if (obsidianDir != null && obsidianDir.isDirectory) {
                return current
            }

            current = current.parent
            levelsUp++
        }

        // Fallback: Find directory with significant number of .md files
        current = if (file.isDirectory) file else file.parent
        levelsUp = 0

        while (current != null && levelsUp < 5) {
            val mdCount = current.children?.count { !it.isDirectory && it.extension == "md" } ?: 0

            if (mdCount >= 5) {  // Arbitrary threshold
                return current
            }

            current = current.parent
            levelsUp++
        }

        // Last resort: use file's parent directory
        return if (file.isDirectory) file else file.parent
    }

    /**
     * Gets all markdown files within a specific vault directory.
     *
     * @param project The current project
     * @param vaultRoot The root directory of the Obsidian vault (or null to use project root)
     * @return List of .md files within this vault only
     */
    fun getAllMarkdownFilesInVault(project: Project, vaultRoot: VirtualFile?): List<VirtualFile> {
        val rootDir = vaultRoot ?: project.baseDir ?: return emptyList()

        val allFiles = mutableListOf<VirtualFile>()

        fun collectMarkdownFiles(dir: VirtualFile) {
            dir.children?.forEach { child ->
                if (child.isDirectory) {
                    collectMarkdownFiles(child)
                } else if (child.extension == "md") {
                    allFiles.add(child)
                }
            }
        }

        collectMarkdownFiles(rootDir)

        return allFiles
    }

    /**
     * Finds the best matching file for an Obsidian link (project-wide, may cross vaults).
     *
     * @param project The current project
     * @param linkText The text inside [[brackets]], e.g., "Note" or "Folder/Note" or "Note|Alias"
     * @return The best matching PsiFile, or null if no match found
     */
    fun findBestMatch(project: Project, linkText: String): PsiFile? {
        return findBestMatchInVault(project, linkText, vaultRoot = null)
    }

    /**
     * Finds the best matching file for an Obsidian link within a specific vault.
     *
     * @param project The current project
     * @param linkText The text inside [[brackets]]
     * @param vaultRoot The vault root directory (if null, searches project-wide)
     * @return The best matching PsiFile, or null if no match found
     */
    fun findBestMatchInVault(project: Project, linkText: String, vaultRoot: VirtualFile?): PsiFile? {
        // 1. Clean link - remove alias part (everything after |)
        val cleanLink = linkText.substringBefore("|").trim()

        if (cleanLink.isEmpty()) {
            return null
        }

        // 2. Extract filename from link (last part after /)
        val filename = cleanLink.substringAfterLast("/")
        val filenameWithExt = if (filename.endsWith(".md")) filename else "$filename.md"

        // 3. Find all PsiFiles with this name
        var potentialFiles: Array<out PsiFile> = FilenameIndex.getFilesByName(
            project,
            filenameWithExt,
            GlobalSearchScope.projectScope(project)
        )

        // FALLBACK: If FilenameIndex returns nothing (common with @ and special chars),
        // search through markdown files manually (vault-scoped if vaultRoot provided)
        if (potentialFiles.isEmpty()) {
            val allMarkdownFiles = if (vaultRoot != null) {
                getAllMarkdownFilesInVault(project, vaultRoot)
            } else {
                getAllMarkdownFiles(project)
            }

            // Filter files that match our filename
            val matchingFiles = allMarkdownFiles.mapNotNull { virtualFile ->
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null && psiFile.name == filenameWithExt) {
                    psiFile
                } else {
                    null
                }
            }.toTypedArray()

            potentialFiles = matchingFiles
        }

        if (potentialFiles.isEmpty()) {
            return null
        }

        // 4. Sort PsiFiles by best match first
        val sortedFiles: List<PsiFile> = potentialFiles.sortedWith(compareBy(
            { psiFile -> !matchesPath(psiFile.virtualFile, cleanLink) }, // Exact path matches first
            { psiFile -> psiFile.virtualFile.path.length }  // Then shortest path
        ))

        return sortedFiles.firstOrNull()
    }

    /**
     * Checks if the virtual file's path matches the given link path.
     *
     * @param file The virtual file to check
     * @param linkPath The link path (e.g., "Folder/Note" or "Note")
     * @return true if the file path ends with the link path structure
     */
    private fun matchesPath(file: VirtualFile, linkPath: String): Boolean {
        val normalizedLinkPath = linkPath.replace('\\', '/')
        val expectedSuffix = if (normalizedLinkPath.endsWith(".md")) {
            normalizedLinkPath
        } else {
            "$normalizedLinkPath.md"
        }

        val filePath = file.path.replace('\\', '/')
        return filePath.endsWith(expectedSuffix)
    }

    /**
     * Gets all markdown files in the project for auto-completion.
     *
     * IMPORTANT: FilenameIndex doesn't work well with special characters like @ or emoji,
     * so we do a proper VFS search instead.
     *
     * @param project The current project
     * @return List of all .md VirtualFiles
     */
    fun getAllMarkdownFiles(project: Project): List<VirtualFile> {
        val allFiles = mutableListOf<VirtualFile>()
        val baseDir = project.baseDir ?: return emptyList()

        // Recursively search for all .md files using VFS
        // This catches files with @ and special characters that FilenameIndex misses
        fun collectMarkdownFiles(dir: com.intellij.openapi.vfs.VirtualFile) {
            dir.children?.forEach { child ->
                if (child.isDirectory) {
                    collectMarkdownFiles(child)
                } else if (child.extension == "md") {
                    allFiles.add(child)
                }
            }
        }

        collectMarkdownFiles(baseDir)

        return allFiles
    }

    /**
     * Gets the relative path from project root to file, without the .md extension.
     * This is used for display in auto-completion.
     *
     * @param project The current project
     * @param file The virtual file
     * @return Relative path without .md extension, or null if not in project
     */
    fun getRelativePathForDisplay(project: Project, file: VirtualFile): String? {
        val baseDir = project.baseDir ?: return null
        val relativePath = file.path.removePrefix(baseDir.path).removePrefix("/")
        return relativePath.removeSuffix(".md")
    }
}
