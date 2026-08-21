package com.tatoli.habittracker.ui.habitlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tatoli.habittracker.util.dateKeyOf
import com.tatoli.habittracker.util.firstDayOfWeekOffset
import com.tatoli.habittracker.util.weekKey
import java.time.YearMonth

private val MONTH_NAMES = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember"
)
private val DAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit
) {
    val habits by viewModel.habits.collectAsState()
    val viewMonth by viewModel.viewMonth.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            MonthHeader(
                viewMonth = viewMonth,
                onPrev = viewModel::prevMonth,
                onNext = viewModel::nextMonth
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(habits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        onToggleToday = { viewModel.toggleToday(habit) },
                        onToggleCell = { dateKey, currentlyDone ->
                            viewModel.toggleCell(habit.id, dateKey, currentlyDone)
                        },
                        onClick = { onEditHabit(habit.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(viewMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
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
fun HabitCard(
    habit: HabitDisplayState,
    onToggleToday: () -> Unit,
    onToggleCell: (dateKey: String, currentlyDone: Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(parseHexColor(habit.color), CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconToggleButton(checked = habit.doneToday, onCheckedChange = { onToggleToday() }) {
                    Icon(
                        imageVector = if (habit.doneToday) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (habit.doneToday) "Erledigt" else "Nicht erledigt",
                        tint = if (habit.doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (habit.freq == "weekly") {
                    "🔥 ${habit.streakCount} Wochen Serie · ${habit.monthTotal}/${habit.weekCells.size} diesen Monat"
                } else {
                    "🔥 ${habit.streakCount} Tage Serie · ${habit.monthTotal}/${habit.dayCells.size} diesen Monat"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            if (habit.freq == "weekly") {
                WeeklyStrip(habit.weekCells, onToggleCell)
            } else {
                DailyGrid(habit.dayCells, onToggleCell)
            }
        }
    }
}

@Composable
private fun DailyGrid(cells: List<DayCell>, onToggleCell: (String, Boolean) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val firstOffset = cells.firstOrNull()?.let { firstDayOfWeekOffset(YearMonth.from(it.date)) } ?: 0
        val paddedCells: List<DayCell?> = List(firstOffset) { null } + cells
        paddedCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(modifier = Modifier.weight(1f).size(36.dp), contentAlignment = Alignment.Center) {
                        if (cell != null) {
                            DayCellButton(cell, onToggleCell)
                        }
                    }
                }
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f).size(36.dp))
                }
            }
        }
    }
}

@Composable
private fun DayCellButton(cell: DayCell, onToggleCell: (String, Boolean) -> Unit) {
    TextButton(
        onClick = { onToggleCell(dateKeyOf(cell.date), cell.done) },
        enabled = !cell.isFuture,
        modifier = if (cell.isToday) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
        } else {
            Modifier
        },
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (cell.done) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text = cell.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WeeklyStrip(cells: List<WeekCell>, onToggleCell: (String, Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            TextButton(
                onClick = { onToggleCell(weekKey(cell.monday), cell.done) },
                enabled = !cell.isFuture,
                modifier = Modifier.weight(1f).let {
                    if (cell.isCurrentWeek) it.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else it
                },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (cell.done) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(text = "KW ${cell.isoWeekNumber}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun parseHexColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(hex))
