package com.github.mhlavac.intellijobsidian.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class ObsidianVaultService(private val project: Project) {

    @Volatile
    private var isVaultDetected: Boolean = false

    init {
        detectVault()
    }

    /**
     * Detects if the current project is an Obsidian vault by checking for .obsidian directory
     */
    fun detectVault(): Boolean {
        isVaultDetected = project.basePath?.let { basePath ->
            val obsidianDir = File(basePath, ".obsidian")
            obsidianDir.exists() && obsidianDir.isDirectory
        } ?: false

        if (isVaultDetected) {
            thisLogger().info("Obsidian vault detected in project: ${project.name}")
        }

        return isVaultDetected
    }

    /**
     * Returns true if the current project is an Obsidian vault
     */
    fun isObsidianVault(): Boolean = isVaultDetected

    /**
     * Returns the .obsidian directory if it exists
     */
    fun getObsidianDir(): File? {
        return if (isVaultDetected) {
            project.basePath?.let { File(it, ".obsidian") }
        } else {
            null
        }
    }
}
