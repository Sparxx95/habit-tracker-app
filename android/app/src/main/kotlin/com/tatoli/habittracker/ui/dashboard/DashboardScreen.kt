package com.tatoli.habittracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val dashMode by viewModel.dashMode.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val weekdayPattern by viewModel.weekdayPattern.collectAsState()
    val groupComparison by viewModel.groupComparison.collectAsState()
    val trend by viewModel.trend.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("daily" to "Täglich", "weekly" to "Wöchentlich", "all" to "Alle").forEach { (value, label) ->
                    FilterChip(selected = dashMode == value, onClick = { viewModel.selectDashMode(value) }, label = { Text(label) })
                }
            }

            DashboardSection(title = "Gesamtübersicht", entries = overview, emptyText = "Keine Gewohnheiten angelegt.")
            DashboardSection(title = "Wochentags-Muster", entries = weekdayPattern, emptyText = "Nur bei täglichen Gewohnheiten verfügbar.")
            DashboardSection(
                title = "Gruppen-Vergleich",
                entries = if (groupComparison.size >= 2) groupComparison else emptyList(),
                emptyText = "Lege Gruppen an, um sie hier zu vergleichen."
            )
            TrendSection(trend)
        }
    }
}

@Composable
private fun DashboardSection(title: String, entries: List<BarEntry>, emptyText: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (entries.isEmpty()) {
            Text(text = emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            entries.forEach { entry -> BarRow(entry) }
        }
    }
}

@Composable
private fun BarRow(entry: BarEntry) {
    val barColor = entry.color?.let { parseHexColor(it) } ?: MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = entry.label, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier.weight(1f).height(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction = (entry.pct / 100f).coerceIn(0.02f, 1f))
                    .height(16.dp)
                    .background(barColor, RoundedCornerShape(8.dp))
            )
        }
        Text(text = "${entry.pct}%", modifier = Modifier.width(44.dp).padding(start = 8.dp))
    }
}

@Composable
private fun TrendSection(entries: List<BarEntry>) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "Verlaufs-Trend (6 Monate)", style = MaterialTheme.typography.titleSmall)
        if (entries.isEmpty()) return
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)
        ) {
            entries.forEach { entry ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${entry.pct}%", style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height((entry.pct.coerceIn(2, 100) / 100f * 90).dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                    Text(text = entry.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
