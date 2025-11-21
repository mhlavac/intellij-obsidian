package com.github.mhlavac.intellijobsidian.actions

import com.github.mhlavac.intellijobsidian.services.PeriodicNotesConfigService
import com.github.mhlavac.intellijobsidian.services.PeriodType
import com.github.mhlavac.intellijobsidian.util.PeriodicNotesUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.Path

class OpenDailyNoteAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            project.service<PeriodicNotesConfigService>().isPeriodicNotesAvailable()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val configService = project.service<PeriodicNotesConfigService>()
        val editorManager = FileEditorManager.getInstance(project)

        // Get current file to determine context
        val currentFile = editorManager.selectedFiles.firstOrNull()
        val currentFilePath = currentFile?.path?.let { Path(it) }

        // Get the appropriate vault based on context
        val vault = configService.getContextualVault(currentFilePath)
        if (vault == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Obsidian Notifications")
                .createNotification(
                    "No vault found",
                    "Could not find any Obsidian vault with periodic notes configured",
                    NotificationType.WARNING
                )
                .notify(project)
            return
        }

        val config = vault.config.daily
        if (config == null || !config.enabled) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Obsidian Notifications")
                .createNotification(
                    "Daily notes not enabled",
                    "Daily notes are not enabled in vault: ${vault.name}",
                    NotificationType.WARNING
                )
                .notify(project)
            return
        }

        val today = LocalDate.now()
        val format = config.getEffectiveFormat(PeriodType.DAILY.defaultFormat)
        val filename = PeriodicNotesUtil.formatDaily(today, format)

        // Try to find existing note
        var noteFile = PeriodicNotesUtil.findPeriodicNote(project, config.folder, filename)

        // If not found, create it
        if (noteFile == null) {
            val templatePath = config.template?.takeIf { it.isNotBlank() }?.let {
                vault.vaultPath.resolve(it).toString()
            }

            noteFile = PeriodicNotesUtil.createPeriodicNoteInVault(
                vault.vaultPath,
                config.folder,
                filename,
                templatePath
            )
        }

        // Open the note or show error
        if (noteFile != null) {
            editorManager.openFile(noteFile, true)
        } else {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Obsidian Notifications")
                .createNotification(
                    "Failed to create daily note",
                    "Could not create note at ${config.folder}/$filename.md in vault ${vault.name}",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }
}
