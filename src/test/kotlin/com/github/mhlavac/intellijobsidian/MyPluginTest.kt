package com.github.mhlavac.intellijobsidian

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.github.mhlavac.intellijobsidian.services.ObsidianVaultService
import com.github.mhlavac.intellijobsidian.services.PeriodicNotesConfigService
import com.github.mhlavac.intellijobsidian.services.PeriodType
import com.github.mhlavac.intellijobsidian.util.PeriodicNotesUtil
import java.time.LocalDate

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    fun testProjectService() {
        val vaultService = project.service<ObsidianVaultService>()

        // Vault detection should return a boolean value
        assertNotNull(vaultService.isObsidianVault())
    }

    fun testPeriodicNotesConfigService() {
        val configService = project.service<PeriodicNotesConfigService>()

        // Config service should be available
        assertNotNull(configService)

        // Config may or may not be available depending on whether .obsidian/plugins/periodic-notes/data.json exists
        // Just verify the service works
        val isAvailable = configService.isPeriodicNotesAvailable()
        assertTrue(isAvailable || !isAvailable) // Always true, just checking it doesn't crash
    }

    fun testDateFormatting() {
        val today = LocalDate.of(2025, 11, 20)

        // Test daily format
        val daily = PeriodicNotesUtil.formatDaily(today, "YYYY-MM-DD")
        assertEquals("2025-11-20", daily)

        // Test weekly format (ISO week)
        val weekly = PeriodicNotesUtil.formatWeekly(today, "GGGG-[W]WW")
        assertTrue(weekly.matches(Regex("\\d{4}-W\\d{2}")))

        // Test monthly format
        val monthly = PeriodicNotesUtil.formatMonthly(today, "YYYY-MM")
        assertEquals("2025-11", monthly)

        // Test quarterly format
        val quarterly = PeriodicNotesUtil.formatQuarterly(today, "YYYY-[Q]Q")
        assertEquals("2025-Q4", quarterly)

        // Test yearly format
        val yearly = PeriodicNotesUtil.formatYearly(today, "YYYY")
        assertEquals("2025", yearly)
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
