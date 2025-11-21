package com.github.mhlavac.intellijobsidian.services

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString

data class PeriodConfig(
    val format: String?,
    val template: String?,
    val folder: String,
    val enabled: Boolean
) {
    fun getEffectiveFormat(defaultFormat: String): String {
        return if (format.isNullOrBlank()) defaultFormat else format
    }
}

data class PeriodicNotesData(
    @SerializedName("showGettingStartedBanner") val showGettingStartedBanner: Boolean? = null,
    @SerializedName("hasMigratedDailyNoteSettings") val hasMigratedDailyNoteSettings: Boolean? = null,
    @SerializedName("hasMigratedWeeklyNoteSettings") val hasMigratedWeeklyNoteSettings: Boolean? = null,
    val daily: PeriodConfig? = null,
    val weekly: PeriodConfig? = null,
    val monthly: PeriodConfig? = null,
    val quarterly: PeriodConfig? = null,
    val yearly: PeriodConfig? = null
)

enum class PeriodType(val defaultFormat: String, val displayName: String) {
    DAILY("YYYY-MM-DD", "Daily"),
    WEEKLY("GGGG-[W]WW", "Weekly"),
    MONTHLY("YYYY-MM", "Monthly"),
    QUARTERLY("YYYY-[Q]Q", "Quarterly"),
    YEARLY("YYYY", "Yearly");
}

data class VaultInfo(
    val name: String,
    val vaultPath: Path,
    val config: PeriodicNotesData
)

@Service(Service.Level.PROJECT)
class PeriodicNotesConfigService(private val project: Project) {

    private val gson = Gson()
    private var cachedVaults: List<VaultInfo>? = null

    companion object {
        private const val CONFIG_PATH = ".obsidian/plugins/periodic-notes/data.json"
        private const val OBSIDIAN_DIR = ".obsidian"
    }

    /**
     * Get all vaults with periodic notes configuration
     */
    fun getAllVaults(): List<VaultInfo> {
        if (cachedVaults == null) {
            loadAllVaults()
        }
        return cachedVaults ?: emptyList()
    }

    /**
     * Get config for the first/primary vault (backward compatibility)
     */
    fun getConfig(): PeriodicNotesData? {
        return getAllVaults().firstOrNull()?.config
    }

    /**
     * Get period config for the first/primary vault (backward compatibility)
     */
    fun getPeriodConfig(periodType: PeriodType): PeriodConfig? {
        val config = getConfig() ?: return null
        return when (periodType) {
            PeriodType.DAILY -> config.daily
            PeriodType.WEEKLY -> config.weekly
            PeriodType.MONTHLY -> config.monthly
            PeriodType.QUARTERLY -> config.quarterly
            PeriodType.YEARLY -> config.yearly
        }
    }

    /**
     * Check if period is enabled in the first/primary vault (backward compatibility)
     */
    fun isPeriodEnabled(periodType: PeriodType): Boolean {
        return getPeriodConfig(periodType)?.enabled == true
    }

    /**
     * Check if any vault has periodic notes configured
     */
    fun isPeriodicNotesAvailable(): Boolean {
        return getAllVaults().isNotEmpty()
    }

    /**
     * Scan project for all Obsidian vaults with periodic notes config
     */
    private fun loadAllVaults() {
        val vaults = mutableListOf<VaultInfo>()
        val processedPaths = mutableSetOf<String>()

        // Get all content roots (multiple folders can be open in the project)
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots

        // Also include base path as fallback
        val basePath = project.basePath
        val searchPaths = mutableListOf<Path>()

        // Add all content roots
        contentRoots.forEach { root ->
            root.path?.let { searchPaths.add(Path.of(it)) }
        }

        // Add base path if not already included
        if (basePath != null) {
            val basePathObj = Path.of(basePath)
            if (searchPaths.none { it == basePathObj }) {
                searchPaths.add(basePathObj)
            }
        }

        // Scan each search path for .obsidian directories
        searchPaths.forEach { searchPath ->
            findObsidianDirs(searchPath).forEach { obsidianDir ->
                val configPath = obsidianDir.resolve("plugins/periodic-notes/data.json")
                val config = loadConfigFromPath(configPath)

                if (config != null) {
                    val vaultPath = obsidianDir.parent
                    val vaultPathStr = vaultPath.pathString

                    // Avoid duplicates
                    if (!processedPaths.contains(vaultPathStr)) {
                        processedPaths.add(vaultPathStr)
                        val vaultName = getVaultDisplayName(vaultPath, searchPaths)
                        vaults.add(VaultInfo(vaultName, vaultPath, config))
                    }
                }
            }
        }

        cachedVaults = vaults
    }

    /**
     * Find all .obsidian directories in the project (recursively, max depth 3)
     */
    private fun findObsidianDirs(startPath: Path, depth: Int = 0): List<Path> {
        if (depth > 3 || !startPath.isDirectory()) return emptyList()

        val obsidianDirs = mutableListOf<Path>()

        try {
            Files.list(startPath).use { stream ->
                stream.forEach { path ->
                    when {
                        path.name == OBSIDIAN_DIR && path.isDirectory() -> {
                            obsidianDirs.add(path)
                        }
                        path.isDirectory() && !path.name.startsWith(".") -> {
                            obsidianDirs.addAll(findObsidianDirs(path, depth + 1))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore inaccessible directories
        }

        return obsidianDirs
    }

    /**
     * Get a display name for a vault (relative to content roots or folder name)
     */
    private fun getVaultDisplayName(vaultPath: Path, searchPaths: List<Path>): String {
        // Try to find the shortest relative path from any search path
        var shortestRelativePath: String? = null
        var shortestLength = Int.MAX_VALUE

        searchPaths.forEach { searchPath ->
            try {
                if (vaultPath.startsWith(searchPath)) {
                    val relativePath = searchPath.relativize(vaultPath)
                    val relativeStr = relativePath.toString()

                    if (relativeStr.isEmpty() || relativeStr == ".") {
                        // Vault is at the root of this search path
                        val rootName = searchPath.fileName?.toString() ?: "Root"
                        if (rootName.length < shortestLength) {
                            shortestRelativePath = rootName
                            shortestLength = rootName.length
                        }
                    } else if (relativeStr.length < shortestLength) {
                        shortestRelativePath = relativeStr
                        shortestLength = relativeStr.length
                    }
                }
            } catch (e: Exception) {
                // Ignore and try next search path
            }
        }

        return shortestRelativePath ?: vaultPath.fileName?.toString() ?: "Unknown"
    }

    /**
     * Load config from a specific path
     */
    private fun loadConfigFromPath(configPath: Path): PeriodicNotesData? {
        val file = VirtualFileManager.getInstance().findFileByNioPath(configPath)
        if (file == null) {
            // Config file not found at path: $configPath
            return null
        }

        return try {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            val config = gson.fromJson(content, PeriodicNotesData::class.java)
            // Successfully loaded config from: $configPath
            config
        } catch (e: Exception) {
            // Error parsing config at $configPath: ${e.message}
            null
        }
    }

    /**
     * Find which vault contains a given file path
     */
    fun findVaultForFile(filePath: Path): VaultInfo? {
        return getAllVaults().firstOrNull { vault ->
            filePath.startsWith(vault.vaultPath)
        }
    }

    /**
     * Get the appropriate vault for the current context (based on open file)
     * Falls back to first vault if no file is open or file is not in any vault
     */
    fun getContextualVault(currentFilePath: Path?): VaultInfo? {
        val vaults = getAllVaults()
        if (vaults.isEmpty()) return null

        // If we have a current file, try to find its vault
        if (currentFilePath != null) {
            val vault = findVaultForFile(currentFilePath)
            if (vault != null) return vault
        }

        // Fallback to first vault
        return vaults.firstOrNull()
    }

    /**
     * Get debug information about vault detection
     */
    fun getDebugInfo(): String {
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots
        val basePath = project.basePath

        val info = StringBuilder()
        info.appendLine("=== Periodic Notes Debug Info ===")
        info.appendLine("Content Roots (${contentRoots.size}):")
        contentRoots.forEach { root ->
            info.appendLine("  - ${root.path}")
        }
        info.appendLine("Base Path: $basePath")
        info.appendLine("\nDetected Vaults (${getAllVaults().size}):")
        getAllVaults().forEach { vault ->
            info.appendLine("  - Name: ${vault.name}")
            info.appendLine("    Path: ${vault.vaultPath}")
            info.appendLine("    Daily enabled: ${vault.config.daily?.enabled}")
            info.appendLine("    Daily folder: ${vault.config.daily?.folder}")
            info.appendLine("    Daily template: ${vault.config.daily?.template}")
        }

        return info.toString()
    }

    /**
     * Reload all vault configurations
     */
    fun reloadConfig() {
        cachedVaults = null
        loadAllVaults()
    }
}
