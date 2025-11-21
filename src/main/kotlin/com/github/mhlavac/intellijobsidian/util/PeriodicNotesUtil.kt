package com.github.mhlavac.intellijobsidian.util

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields
import kotlin.io.path.exists

object PeriodicNotesUtil {

    /**
     * Converts Obsidian/moment.js date format to Java DateTimeFormatter pattern
     */
    fun convertObsidianFormatToJava(obsidianFormat: String): String {
        var javaFormat = obsidianFormat

        // Handle ISO week year and week number
        javaFormat = javaFormat.replace("GGGG", "YYYY")  // ISO week year
        javaFormat = javaFormat.replace("WW", "ww")      // ISO week number

        // Handle literals in square brackets [W] -> 'W'
        javaFormat = javaFormat.replace(Regex("\\[([^\\]]+)\\]")) { matchResult ->
            "'${matchResult.groupValues[1]}'"
        }

        // Handle quarter
        javaFormat = javaFormat.replace("Q", "Q")  // Quarter is Q in both

        // Year, Month, Day are the same
        // YYYY -> yyyy (but we already converted GGGG to YYYY above, so map YYYY to yyyy)
        javaFormat = javaFormat.replace("YYYY", "yyyy")
        javaFormat = javaFormat.replace("MM", "MM")
        javaFormat = javaFormat.replace("DD", "dd")

        return javaFormat
    }

    /**
     * Format date for daily note
     */
    fun formatDaily(date: LocalDate, format: String): String {
        val javaFormat = convertObsidianFormatToJava(format)
        return try {
            val formatter = DateTimeFormatter.ofPattern(javaFormat)
            date.format(formatter)
        } catch (e: Exception) {
            // Fallback to ISO date
            date.toString()
        }
    }

    /**
     * Format date for weekly note using ISO week standard (Monday start)
     */
    fun formatWeekly(date: LocalDate, format: String): String {
        val weekFields = WeekFields.of(DayOfWeek.MONDAY, 4)  // ISO week standard
        val weekYear = date.get(weekFields.weekBasedYear())
        val weekNumber = date.get(weekFields.weekOfWeekBasedYear())

        // Replace YYYY with week year and ww with week number
        var result = format
        result = result.replace("GGGG", weekYear.toString())
        result = result.replace("WW", weekNumber.toString().padStart(2, '0'))

        // Handle literals in square brackets
        result = result.replace(Regex("\\[([^\\]]+)\\]")) { matchResult ->
            matchResult.groupValues[1]
        }

        return result.ifBlank { "$weekYear-W${weekNumber.toString().padStart(2, '0')}" }
    }

    /**
     * Format date for monthly note
     */
    fun formatMonthly(date: LocalDate, format: String): String {
        val javaFormat = convertObsidianFormatToJava(format)
        return try {
            val formatter = DateTimeFormatter.ofPattern(javaFormat)
            date.format(formatter)
        } catch (e: Exception) {
            // Fallback to YYYY-MM
            "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
        }
    }

    /**
     * Format date for quarterly note
     */
    fun formatQuarterly(date: LocalDate, format: String): String {
        val quarter = date.get(IsoFields.QUARTER_OF_YEAR)
        val year = date.year

        // Store literals and replace with safe placeholders
        val literals = mutableListOf<String>()
        var result = format.replace(Regex("\\[([^\\]]+)\\]")) { matchResult ->
            literals.add(matchResult.groupValues[1])
            "{{LITERAL_${literals.size - 1}}}"
        }

        // Replace YYYY with year and Q with quarter
        result = result.replace("YYYY", year.toString())
        result = result.replace("Q", quarter.toString())

        // Restore literals
        literals.forEachIndexed { index, literal ->
            result = result.replace("{{LITERAL_$index}}", literal)
        }

        return result.ifBlank { "$year-Q$quarter" }
    }

    /**
     * Format date for yearly note
     */
    fun formatYearly(date: LocalDate, format: String): String {
        val javaFormat = convertObsidianFormatToJava(format)
        return try {
            val formatter = DateTimeFormatter.ofPattern(javaFormat)
            date.format(formatter)
        } catch (e: Exception) {
            // Fallback to YYYY
            date.year.toString()
        }
    }

    /**
     * Create a periodic note file, optionally from a template
     */
    fun createPeriodicNote(
        project: Project,
        folder: String,
        filename: String,
        templatePath: String?
    ): VirtualFile? {
        val basePath = project.basePath ?: return null
        val folderPath = Path.of(basePath, folder)
        val filePath = folderPath.resolve("$filename.md")

        return WriteAction.computeAndWait<VirtualFile?, Exception> {
            try {
                // Create folder if it doesn't exist
                if (!folderPath.exists()) {
                    Files.createDirectories(folderPath)
                }

                // Get content from template or create empty
                val content = if (!templatePath.isNullOrBlank()) {
                    readTemplate(project, templatePath) ?: ""
                } else {
                    ""
                }

                // Create file
                if (!filePath.exists()) {
                    Files.writeString(filePath, content)
                }

                // Refresh and return VirtualFile
                VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Create a periodic note file in a specific vault, optionally from a template
     */
    fun createPeriodicNoteInVault(
        vaultPath: Path,
        folder: String,
        filename: String,
        templatePath: String?
    ): VirtualFile? {
        val folderPath = vaultPath.resolve(folder)
        val filePath = folderPath.resolve("$filename.md")

        return WriteAction.computeAndWait<VirtualFile?, Exception> {
            try {
                // Create folder if it doesn't exist
                if (!folderPath.exists()) {
                    Files.createDirectories(folderPath)
                }

                // Get content from template or create empty
                val content = if (!templatePath.isNullOrBlank()) {
                    readTemplateFromPath(Path.of(templatePath)) ?: ""
                } else {
                    ""
                }

                // Create file
                if (!filePath.exists()) {
                    Files.writeString(filePath, content)
                }

                // Refresh and return VirtualFile
                VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Read template content from the specified path
     */
    private fun readTemplate(project: Project, templatePath: String): String? {
        val basePath = project.basePath ?: return null
        val fullPath = Path.of(basePath, templatePath)

        return try {
            if (fullPath.exists()) {
                Files.readString(fullPath)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read template content from an absolute path
     */
    private fun readTemplateFromPath(templatePath: Path): String? {
        return try {
            if (templatePath.exists()) {
                Files.readString(templatePath)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Find an existing periodic note file
     */
    fun findPeriodicNote(project: Project, folder: String, filename: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val filePath = Path.of(basePath, folder, "$filename.md")
        return VirtualFileManager.getInstance().findFileByNioPath(filePath)
    }

    /**
     * Check if a periodic note exists
     */
    fun periodicNoteExists(project: Project, folder: String, filename: String): Boolean {
        return findPeriodicNote(project, folder, filename) != null
    }
}
