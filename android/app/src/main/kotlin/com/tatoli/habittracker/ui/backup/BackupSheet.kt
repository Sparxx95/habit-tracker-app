package com.tatoli.habittracker.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(viewModel: BackupViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastBackupText by viewModel.lastBackupText.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var pendingImportXml by remember { mutableStateOf<String?>(null) }
    var pendingImportCount by remember { mutableStateOf(0) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val xml = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("Datei konnte nicht gelesen werden")
                val parsedCount = countHabitsForConfirmation(xml)
                if (viewModel.hasAnyHabits()) {
                    pendingImportXml = xml
                    pendingImportCount = parsedCount
                } else {
                    val imported = viewModel.importXml(xml)
                    errorMessage = null
                    successMessage = "Backup importiert: $imported Gewohnheit(en)."
                }
            } catch (e: Exception) {
                successMessage = null
                errorMessage = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(text = "Backup", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(text = lastBackupText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val xml = viewModel.buildExportXml()
                            val fileName = "habits-backup-${System.currentTimeMillis()}.xml"
                            val file = File(context.cacheDir, fileName)
                            file.writeText(xml)
                            val uri = FileProvider.getUriForFile(context, "com.tatoli.habittracker.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/xml"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, fileName))
                            viewModel.markBackupDone()
                        } catch (e: Exception) {
                            successMessage = null
                            errorMessage = "Export fehlgeschlagen: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exportieren")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { importLauncher.launch(arrayOf("application/xml", "text/xml", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Importieren")
            }
            errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            successMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(text = message, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    pendingImportXml?.let { xml ->
        AlertDialog(
            onDismissRequest = { pendingImportXml = null },
            title = { Text("Backup importieren?") },
            text = { Text("Backup enthält $pendingImportCount Gewohnheit(en).\n\nOK = aktuelle Daten ERSETZEN\nAbbrechen = nichts tun") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            val imported = viewModel.importXml(xml)
                            errorMessage = null
                            successMessage = "Backup importiert: $imported Gewohnheit(en)."
                        } catch (e: Exception) {
                            successMessage = null
                            errorMessage = "Import fehlgeschlagen: ${e.message}"
                        }
                        pendingImportXml = null
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                Button(onClick = { pendingImportXml = null }) { Text("Abbrechen") }
            }
        )
    }
}

private fun countHabitsForConfirmation(xml: String): Int =
    com.tatoli.habittracker.util.parseHabitsXml(xml, System.currentTimeMillis()).size
