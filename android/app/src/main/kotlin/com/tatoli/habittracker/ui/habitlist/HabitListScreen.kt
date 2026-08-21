package com.tatoli.habittracker.ui.habitlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(habits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        onToggleToday = { viewModel.toggleToday(habit) },
                        onToggleCell = { dateKey, currentlyDone ->
                            viewModel.toggleCell(habit.id, dateKey, currentlyDone)
                        },
                        onEdit = { onEditHabit(habit.id) }
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
    onEdit: () -> Unit
) {
    val habitColor = parseHexColor(habit.color)
    Card(
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
                        .background(habitColor, CircleShape)
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
                        contentDescription = if (habit.doneToday) "Heute erledigt" else "Heute nicht erledigt",
                        tint = if (habit.doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Bearbeiten")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (habit.freq == "weekly") {
                    "🔥 ${habit.streakCount} Wochen Serie · ${habit.monthTotal}/${habit.weekCells.size} diesen Monat"
                } else {
                    "🔥 ${habit.streakCount} Tage Serie · ${habit.monthTotal}/${habit.dayCells.size} diesen Monat"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (habit.freq == "weekly") {
                WeeklyStrip(habit.weekCells, habitColor, onToggleCell)
            } else {
                DailyGrid(habit.dayCells, habitColor, onToggleCell)
            }
        }
    }
}

@Composable
private fun DailyGrid(cells: List<DayCell>, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val firstOffset = cells.firstOrNull()?.let { firstDayOfWeekOffset(YearMonth.from(it.date)) } ?: 0
        val paddedCells: List<DayCell?> = List(firstOffset) { null } + cells
        paddedCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(modifier = Modifier.weight(1f).size(36.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (cell != null) {
                            DayCellButton(cell, habitColor, onToggleCell)
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
private fun DayCellButton(cell: DayCell, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    val description = "${cell.date.dayOfMonth}. ${MONTH_NAMES[cell.date.monthValue - 1]}" +
        if (cell.done) " erledigt" else ""
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (cell.done) habitColor else Color.Transparent)
            .then(
                if (cell.isToday) Modifier.border(1.dp, habitColor, RoundedCornerShape(9.dp)) else Modifier
            )
            .clickable(enabled = !cell.isFuture) { onToggleCell(dateKeyOf(cell.date), cell.done) }
            .alpha(if (cell.isFuture) 0.28f else 1f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WeeklyStrip(cells: List<WeekCell>, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Box(modifier = Modifier.weight(1f).size(36.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                WeekCellButton(cell, habitColor, onToggleCell)
            }
        }
    }
}

@Composable
private fun WeekCellButton(cell: WeekCell, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    val description = "Woche ${cell.isoWeekNumber}" + if (cell.done) " erledigt" else ""
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (cell.done) habitColor else Color.Transparent)
            .then(
                if (cell.isCurrentWeek) Modifier.border(1.dp, habitColor, RoundedCornerShape(9.dp)) else Modifier
            )
            .clickable(enabled = !cell.isFuture) { onToggleCell(weekKey(cell.monday), cell.done) }
            .alpha(if (cell.isFuture) 0.28f else 1f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "KW${cell.isoWeekNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun parseHexColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(hex))
