package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitDoneEntity
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitWithDoneEntities
import com.tatoli.habittracker.ui.theme.HabitPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitXmlTest {

    private fun entry(
        name: String = "Lesen",
        color: String = "#F2B450",
        group: String = "",
        freq: String = "daily",
        createdAt: Long = 1_723_000_000_000L,
        doneKeys: List<String> = emptyList()
    ) = HabitWithDoneEntities(
        habit = HabitEntity(id = 1, name = name, color = color, freq = freq, group = group, createdAt = createdAt),
        doneEntries = doneKeys.map { HabitDoneEntity(habitId = 1, dateKey = it) }
    )

    @Test
    fun buildHabitsXml_containsRootElementWithVersionAndExportedAttribute() {
        val xml = buildHabitsXml(emptyList())
        assertTrue(xml.contains("<habits exported=\""))
        assertTrue(xml.contains("version=\"2\">"))
        assertTrue(xml.contains("</habits>"))
    }

    @Test
    fun buildHabitsXml_usesCreatedAtAsIdAttribute() {
        val xml = buildHabitsXml(listOf(entry(createdAt = 1_723_000_000_000L)))
        assertTrue(xml.contains("id=\"1723000000000\""))
    }

    @Test
    fun buildHabitsXml_escapesSpecialCharactersInNameAndGroup() {
        val xml = buildHabitsXml(listOf(entry(name = "A & B <C>", group = "\"Q\"")))
        assertTrue(xml.contains("name=\"A &amp; B &lt;C&gt;\""))
        assertTrue(xml.contains("group=\"&quot;Q&quot;\""))
    }

    @Test
    fun buildHabitsXml_sortsDayElementsByDateKey() {
        val xml = buildHabitsXml(listOf(entry(doneKeys = listOf("2026-08-10", "2026-08-01", "2026-08-05"))))
        val firstIndex = xml.indexOf("2026-08-01")
        val secondIndex = xml.indexOf("2026-08-05")
        val thirdIndex = xml.indexOf("2026-08-10")
        assertTrue(firstIndex in 0 until secondIndex)
        assertTrue(secondIndex in 0 until thirdIndex)
    }

    @Test
    fun buildHabitsXml_marksWeeklyFreqCorrectly() {
        val xml = buildHabitsXml(listOf(entry(freq = "weekly")))
        assertTrue(xml.contains("freq=\"weekly\""))
    }

    @Test
    fun parseHabitsXml_roundTripsBuildHabitsXmlOutput() {
        val original = listOf(
            entry(name = "Lesen", color = "#F2B450", group = "Bildung", freq = "daily", createdAt = 111L, doneKeys = listOf("2026-08-01", "2026-08-02")),
            entry(name = "Sport", color = "#4FC98A", group = "", freq = "weekly", createdAt = 222L, doneKeys = listOf("2026-W31"))
        )
        val xml = buildHabitsXml(original)
        val parsed = parseHabitsXml(xml, importedAt = 999L)

        assertEquals(2, parsed.size)
        assertEquals("Lesen", parsed[0].name)
        assertEquals("#F2B450", parsed[0].color)
        assertEquals("Bildung", parsed[0].group)
        assertEquals("daily", parsed[0].freq)
        assertEquals(111L, parsed[0].createdAt)
        assertEquals(listOf("2026-08-01", "2026-08-02"), parsed[0].doneKeys)
        assertEquals("weekly", parsed[1].freq)
        assertEquals(listOf("2026-W31"), parsed[1].doneKeys)
    }

    @Test
    fun parseHabitsXml_missingIdAttributeFallsBackToImportedAt() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit name="Ohne ID" color="#F2B450" group="" freq="daily"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 555L)
        assertEquals(555L, parsed[0].createdAt)
    }

    @Test
    fun parseHabitsXml_missingNameFallsBackToUnbenannt() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" color="#F2B450" group="" freq="daily"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals("Unbenannt", parsed[0].name)
    }

    @Test
    fun parseHabitsXml_missingColorFallsBackToFirstPaletteColor() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" name="X" group="" freq="daily"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals(HabitPalette.first(), parsed[0].color)
    }

    @Test
    fun parseHabitsXml_nonWeeklyFreqValueBecomesDaily() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" name="X" color="#F2B450" group="" freq="monthly"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals("daily", parsed[0].freq)
    }

    @Test
    fun parseHabitsXml_dropsDayElementsWithInvalidDateFormat() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" name="X" color="#F2B450" group="" freq="daily">
                <day date="2026-08-01"/>
                <day date="not-a-date"/>
                <day date="2026-W05"/>
              </habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals(listOf("2026-08-01", "2026-W05"), parsed[0].doneKeys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseHabitsXml_invalidXmlThrows() {
        parseHabitsXml("not xml at all <<<", importedAt = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseHabitsXml_noHabitElementsThrows() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><habits exported="2026-08-30" version="2"></habits>"""
        parseHabitsXml(xml, importedAt = 1L)
    }
}
