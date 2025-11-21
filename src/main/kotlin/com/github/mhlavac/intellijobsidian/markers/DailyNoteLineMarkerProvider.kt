package com.github.mhlavac.intellijobsidian.markers

import com.github.mhlavac.intellijobsidian.services.PeriodicNotesConfigService
import com.github.mhlavac.intellijobsidian.services.PeriodType
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import java.time.format.DateTimeParseException
import java.time.LocalDate

/**
 * Provides gutter icons for date strings that have corresponding Daily Notes.
 *
 * When a date in the format YYYY-MM-DD is found in the text, this provider:
 * 1. Checks if a Daily Note exists for that date
 * 2. Shows a calendar icon in the gutter if the note exists
 * 3. Clicking the icon opens the Daily Note
 */
class DailyNoteLineMarkerProvider : LineMarkerProvider {

    companion object {
        // Regex to match ISO date format: YYYY-MM-DD
        private val DATE_PATTERN = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val text = element.text ?: return null

        // Only process leaf elements (actual text nodes)
        if (element.firstChild != null) {
            return null
        }

        // Check if this element contains a date pattern
        val matchResult = DATE_PATTERN.find(text) ?: return null
        val dateString = matchResult.groupValues[1]

        // Validate that it's a real date
        if (!isValidDate(dateString)) {
            return null
        }

        // Check if Daily Note exists for this date
        val project = element.project
        val configService = project.service<PeriodicNotesConfigService>()

        // Only show markers if periodic notes are configured
        if (!configService.isPeriodicNotesAvailable() || !configService.isPeriodEnabled(PeriodType.DAILY)) {
            return null
        }

        val config = configService.getPeriodConfig(PeriodType.DAILY) ?: return null
        val baseDir = project.baseDir ?: return null
        val dailyNotePath = "${config.folder}/$dateString.md"
        val dailyNoteFile = baseDir.findFileByRelativePath(dailyNotePath) ?: return null

        // Only show marker if the file exists
        if (!dailyNoteFile.exists()) {
            return null
        }

        // Create line marker with icon
        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Actions.ListChanges,
            { "Open Daily Note for $dateString" },
            { _, _ ->
                // Open the daily note when clicked
                FileEditorManager.getInstance(project).openFile(dailyNoteFile, true)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { "Daily Note: $dateString" }
        )
    }

    /**
     * Validates that a string is a valid ISO date (YYYY-MM-DD).
     */
    private fun isValidDate(dateString: String): Boolean {
        return try {
            LocalDate.parse(dateString)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}
