package com.github.mhlavac.intellijobsidian.toolWindow

import com.github.mhlavac.intellijobsidian.services.PeriodicNotesConfigService
import com.github.mhlavac.intellijobsidian.services.PeriodConfig
import com.github.mhlavac.intellijobsidian.services.PeriodType
import com.github.mhlavac.intellijobsidian.services.VaultInfo
import com.github.mhlavac.intellijobsidian.util.PeriodicNotesUtil
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.time.LocalDate
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator

class PeriodicNotesToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val periodicNotesToolWindow = PeriodicNotesToolWindow(project)
        val content = ContentFactory.getInstance().createContent(periodicNotesToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return project.service<PeriodicNotesConfigService>().isPeriodicNotesAvailable()
    }

    class PeriodicNotesToolWindow(private val project: Project) {

        private val configService = project.service<PeriodicNotesConfigService>()

        fun getContent(): JPanel {
            val panel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
                border = JBUI.Borders.empty(10)
            }

            val allVaults = configService.getAllVaults()

            if (allVaults.isEmpty()) {
                panel.add(JBLabel("Periodic Notes plugin not configured"), BorderLayout.CENTER)
                return panel
            }

            val contentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            // Title
            val titleLabel = JBLabel("<html><b>Periodic Notes</b></html>")
            titleLabel.border = JBUI.Borders.empty(0, 0, 10, 0)
            contentPanel.add(titleLabel)

            val today = LocalDate.now()

            // Add section for each vault
            allVaults.forEachIndexed { vaultIndex, vault ->
                // Vault header
                if (allVaults.size > 1) {
                    val vaultLabel = JBLabel("<html><b>📁 ${vault.name}</b></html>")
                    vaultLabel.border = JBUI.Borders.empty(10, 0, 5, 0)
                    contentPanel.add(vaultLabel)
                }

                // Add links for each period type in this vault
                PeriodType.values().forEach { periodType ->
                    val periodConfig = getPeriodConfigForVault(vault, periodType)
                    if (periodConfig?.enabled == true) {
                        val format = periodConfig.getEffectiveFormat(periodType.defaultFormat)
                        val filename = when (periodType) {
                            PeriodType.DAILY -> PeriodicNotesUtil.formatDaily(today, format)
                            PeriodType.WEEKLY -> PeriodicNotesUtil.formatWeekly(today, format)
                            PeriodType.MONTHLY -> PeriodicNotesUtil.formatMonthly(today, format)
                            PeriodType.QUARTERLY -> PeriodicNotesUtil.formatQuarterly(today, format)
                            PeriodType.YEARLY -> PeriodicNotesUtil.formatYearly(today, format)
                        }

                        val noteFile = PeriodicNotesUtil.findPeriodicNote(project, periodConfig.folder, filename)
                        val exists = noteFile != null
                        val linkPanel = createLinkPanel(
                            vault,
                            periodType,
                            periodConfig,
                            filename,
                            exists
                        )
                        contentPanel.add(linkPanel)
                    }
                }

                // Add separator between vaults
                if (vaultIndex < allVaults.size - 1) {
                    val separator = JSeparator()
                    separator.border = JBUI.Borders.empty(10, 0)
                    contentPanel.add(separator)
                }
            }

            // Buttons panel
            val buttonsPanel = JPanel().apply {
                layout = GridBagLayout()
            }

            val refreshButton = JButton("Refresh").apply {
                addActionListener {
                    configService.reloadConfig()
                    panel.removeAll()
                    panel.add(getContent())
                    panel.revalidate()
                    panel.repaint()
                }
            }

            val debugButton = JButton("Debug Info").apply {
                addActionListener {
                    val debugInfo = configService.getDebugInfo()
                    println(debugInfo) // Print to IDE console
                    com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("Obsidian Notifications")
                        .createNotification(
                            "Debug Info",
                            "Check IDE console for vault detection details",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            }

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insets(10, 0, 0, 5)
            }
            buttonsPanel.add(refreshButton, gbc)

            gbc.gridx = 1
            gbc.insets = JBUI.insets(10, 5, 0, 0)
            buttonsPanel.add(debugButton, gbc)

            contentPanel.add(buttonsPanel)

            panel.add(contentPanel, BorderLayout.NORTH)
            return panel
        }

        private fun getPeriodConfigForVault(vault: VaultInfo, periodType: PeriodType): PeriodConfig? {
            return when (periodType) {
                PeriodType.DAILY -> vault.config.daily
                PeriodType.WEEKLY -> vault.config.weekly
                PeriodType.MONTHLY -> vault.config.monthly
                PeriodType.QUARTERLY -> vault.config.quarterly
                PeriodType.YEARLY -> vault.config.yearly
            }
        }

        private fun createLinkPanel(
            vault: VaultInfo,
            periodType: PeriodType,
            periodConfig: PeriodConfig,
            filename: String,
            exists: Boolean
        ): JPanel {
            val panel = JPanel().apply {
                layout = GridBagLayout()
                border = JBUI.Borders.empty(5, 0)
            }

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }

            val icon = when (periodType) {
                PeriodType.DAILY -> "📅"
                PeriodType.WEEKLY -> "📆"
                PeriodType.MONTHLY -> "🗓️"
                PeriodType.QUARTERLY -> "📊"
                PeriodType.YEARLY -> "📖"
            }

            val statusText = if (exists) "[Open]" else "[Create]"
            val linkText = "<html>$icon <b>${periodType.displayName}:</b> " +
                "<a href='#'>$filename</a> <font color='gray'>$statusText</font></html>"

            val label = JBLabel(linkText).apply {
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent?) {
                        openOrCreateNote(vault, periodConfig, filename)
                    }
                })
            }

            panel.add(label, gbc)
            return panel
        }

        private fun openOrCreateNote(vault: VaultInfo, periodConfig: PeriodConfig, filename: String) {
            // Try to find existing note
            var noteFile = PeriodicNotesUtil.findPeriodicNote(project, periodConfig.folder, filename)

            // If not found, create it (template path is relative to vault)
            if (noteFile == null) {
                val templatePath = periodConfig.template?.takeIf { it.isNotBlank() }?.let {
                    vault.vaultPath.resolve(it).toString()
                }

                noteFile = PeriodicNotesUtil.createPeriodicNoteInVault(
                    vault.vaultPath,
                    periodConfig.folder,
                    filename,
                    templatePath
                )
            }

            // Open the note
            noteFile?.let {
                FileEditorManager.getInstance(project).openFile(it, true)
            }
        }
    }
}
