package com.tatoli.habittracker.ui.habitlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit
) {
    val habits by viewModel.habits.collectAsState()
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(habits, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    onToggle = { viewModel.toggleToday(habit) },
                    onClick = { onEditHabit(habit.id) }
                )
            }
        }
    }
}

@Composable
fun HabitCard(
    habit: HabitDisplayState,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
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
            IconToggleButton(checked = habit.doneToday, onCheckedChange = { onToggle() }) {
                Icon(
                    imageVector = if (habit.doneToday) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (habit.doneToday) "Heute erledigt" else "Heute nicht erledigt",
                    tint = if (habit.doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

private fun parseHexColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(hex))
