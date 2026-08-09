package com.tatoli.habittracker.ui.habitedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tatoli.habittracker.ui.theme.HabitPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditSheet(
    viewModel: HabitEditViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = if (viewModel.isEditing) "Habit bearbeiten" else "Habit anlegen",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Text("Farbe", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                HabitPalette.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(8.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (viewModel.color == hex) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { viewModel.onColorChange(hex) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Nur "Täglich" verfügbar: "Wöchentlich" hätte noch keine Wochen-Key-bewusste
            // Abfrage/Verarbeitung (ViewModel/DAO) hinter sich und würde still wie "daily"
            // behandelt. Kehrt zurück, sobald ein späterer Plan echte Wochensemantik liefert.
            Text("Rhythmus", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = viewModel.freq == "daily",
                    onClick = { viewModel.onFreqChange("daily") },
                    label = { Text("Täglich") }
                )
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.save(onDone = onSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.name.isNotBlank()
            ) {
                Text("Speichern")
            }

            if (viewModel.isEditing) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.delete(onDone = onSaved) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Löschen")
                }
            }
        }
    }
}
