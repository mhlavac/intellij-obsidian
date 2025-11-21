package com.github.mhlavac.intellijobsidian.conditions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.github.mhlavac.intellijobsidian.services.ObsidianVaultService

/**
 * Condition class used in plugin.xml to enable/disable features based on Obsidian vault detection
 */
class ObsidianVaultCondition : Condition<Project> {
    override fun value(project: Project?): Boolean {
        return project?.service<ObsidianVaultService>()?.isObsidianVault() ?: false
    }
}
