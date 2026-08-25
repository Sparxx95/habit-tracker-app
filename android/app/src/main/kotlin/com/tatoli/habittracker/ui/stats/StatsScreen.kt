package com.tatoli.habittracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.YearMonth

private val MONTH_NAMES = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val viewMonth by viewModel.viewMonth.collectAsState()
    val freq by viewModel.freq.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val table by viewModel.table.collectAsState()
    val weeklyStrips by viewModel.weeklyStrips.collectAsState()
    val legend by viewModel.legend.collectAsState()
    val hasDailyHabits by viewModel.hasDailyHabits.collectAsState()
    val hasWeeklyHabits by viewModel.hasWeeklyHabits.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ToggleRow(
                options = listOf("daily" to "Täglich", "weekly" to "Wöchentlich"),
                selected = freq,
                onSelect = viewModel::selectFreq
            )
            Spacer(Modifier.size(8.dp))
            ToggleRow(
                options = listOf("table" to "Tabelle", "circle" to "Kreis"),
                selected = mode,
                onSelect = viewModel::selectMode
            )
            MonthNavRow(viewMonth, onPrev = viewModel::prevMonth, onNext = viewModel::nextMonth)

            when {
                freq == "daily" && !hasDailyHabits ->
                    EmptyMessage("Keine täglichen Gewohnheiten angelegt.")
                freq == "daily" && mode == "table" -> {
                    StatsTableView(table)
                    SectionTitle("Erfolgsquote (gesamt)")
                    LegendView(legend)
                }
                freq == "daily" && mode == "circle" -> {
                    // Kreis-Ansicht: siehe Task 5 (RingStatsChart)
                }
                freq == "weekly" && !hasWeeklyHabits ->
                    EmptyMessage("Keine wöchentlichen Gewohnheiten angelegt.")
                freq == "weekly" && mode == "table" -> {
                    weeklyStrips.forEach { row -> WeeklyStripView(row) }
                    SectionTitle("Erfolgsquote (gesamt)")
                    LegendView(legend)
                }
                freq == "weekly" && mode == "circle" -> {
                    // Kreis-Ansicht (KW-Sektoren): siehe Task 5
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun MonthNavRow(viewMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val isCurrentMonth = viewMonth == YearMonth.now()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Monat")
        }
        Text(
            text = "${MONTH_NAMES[viewMonth.monthValue - 1]} ${viewMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext, enabled = !isCurrentMonth) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Monat")
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun StatsTableView(table: StatsTable) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Column {
            Text("Tag", style = MaterialTheme.typography.labelSmall, modifier = Modifier.size(38.dp, 24.dp))
            table.rows.forEach { row ->
                Box(
                    modifier = Modifier.size(38.dp, 28.dp).padding(top = 4.dp, end = 2.dp)
                        .then(
                            if (row.isToday) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = row.day.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
        table.columns.forEachIndexed { colIndex, column ->
            Column {
                Text(
                    text = column.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = parseHexColor(column.color),
                    modifier = Modifier.width(60.dp).padding(horizontal = 4.dp)
                )
                table.rows.forEach { row ->
                    val state = row.states[colIndex]
                    Box(
                        modifier = Modifier.size(60.dp, 28.dp).padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (state) {
                            DayState.DONE -> Box(
                                Modifier.size(14.dp).background(parseHexColor(column.color), CircleShape)
                            )
                            DayState.MISS -> Box(
                                Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                            )
                            DayState.OFF -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyStripView(row: WeeklyStripRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text = row.name, style = MaterialTheme.typography.titleSmall, color = parseHexColor(row.color))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            row.cells.forEach { cell ->
                Box(
                    modifier = Modifier.weight(1f).size(32.dp).padding(2.dp)
                        .background(
                            if (cell.active && cell.done) parseHexColor(row.color) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (cell.active) {
                        Text(
                            text = "KW${cell.isoWeekNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "KW${cell.isoWeekNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendView(entries: List<StatsLegendEntry>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).background(parseHexColor(entry.color), CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(text = entry.name, modifier = Modifier.weight(1f))
                Text(text = "🔥 ${entry.streak}", modifier = Modifier.padding(end = 8.dp))
                Text(text = "${entry.successRatePct}%")
            }
        }
    }
}

private fun parseHexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
