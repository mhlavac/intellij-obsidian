package com.github.mhlavac.intellijobsidian.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.github.mhlavac.intellijobsidian.services.ObsidianVaultService

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Trigger vault detection when project opens
        project.service<ObsidianVaultService>().detectVault()
    }
}