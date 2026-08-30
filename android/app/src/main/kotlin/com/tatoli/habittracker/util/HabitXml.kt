package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitWithDoneEntities
import com.tatoli.habittracker.ui.theme.HabitPalette
import java.io.StringReader
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class ParsedHabit(
    val name: String,
    val color: String,
    val group: String,
    val freq: String,
    val createdAt: Long,
    val doneKeys: List<String>
)

private fun escXml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

fun buildHabitsXml(habits: List<HabitWithDoneEntities>): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<habits exported=\"").append(escXml(Instant.now().toString())).append("\" version=\"2\">\n")
    habits.forEach { entry ->
        val h = entry.habit
        sb.append("  <habit id=\"").append(h.createdAt)
            .append("\" name=\"").append(escXml(h.name))
            .append("\" color=\"").append(escXml(h.color))
            .append("\" group=\"").append(escXml(h.group))
            .append("\" freq=\"").append(if (h.freq == "weekly") "weekly" else "daily")
            .append("\">\n")
        entry.doneEntries.map { it.dateKey }.sorted().forEach { key ->
            sb.append("    <day date=\"").append(escXml(key)).append("\"/>\n")
        }
        sb.append("  </habit>\n")
    }
    sb.append("</habits>\n")
    return sb.toString()
}

private val DAY_KEY_PATTERN = Regex("^\\d{4}(-\\d{2}-\\d{2}|-W\\d{2})$")

fun parseHabitsXml(xml: String, importedAt: Long): List<ParsedHabit> {
    val factory = DocumentBuilderFactory.newInstance()
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (e: Exception) {
        // Feature not supported by this platform's XML parser (e.g. Android's Harmony/Expat-based
        // DocumentBuilderFactory rejects Xerces-specific feature URIs) — parsing still proceeds
        // without this specific hardening. Acceptable here: the imported file is chosen by the
        // device owner from local storage via the system file picker, not untrusted network input.
    }
    val document = try {
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    } catch (e: Exception) {
        throw IllegalArgumentException("XML ungültig", e)
    }
    val habitNodes = document.getElementsByTagName("habit")
    if (habitNodes.length == 0) throw IllegalArgumentException("Keine Gewohnheiten in der Datei")

    return (0 until habitNodes.length).map { i ->
        val habitElement = habitNodes.item(i) as Element
        val name = habitElement.getAttribute("name").ifEmpty { "Unbenannt" }
        val color = habitElement.getAttribute("color").ifEmpty { HabitPalette.first() }
        val group = habitElement.getAttribute("group").trim()
        val freq = if (habitElement.getAttribute("freq") == "weekly") "weekly" else "daily"
        val createdAt = habitElement.getAttribute("id").toLongOrNull() ?: importedAt
        val dayNodes = habitElement.getElementsByTagName("day")
        val doneKeys = (0 until dayNodes.length).mapNotNull { j ->
            val date = (dayNodes.item(j) as Element).getAttribute("date")
            if (DAY_KEY_PATTERN.matches(date)) date else null
        }
        ParsedHabit(name = name, color = color, group = group, freq = freq, createdAt = createdAt, doneKeys = doneKeys)
    }
}
