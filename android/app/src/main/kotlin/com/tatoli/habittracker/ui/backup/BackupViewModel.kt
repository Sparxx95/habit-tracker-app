package com.tatoli.habittracker.ui.backup

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.buildHabitsXml
import com.tatoli.habittracker.util.parseHabitsXml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private const val KEY_LAST_BACKUP_AT = "lastBackupAt"

class BackupViewModel(
    private val repository: HabitRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _lastBackupText = MutableStateFlow(computeLastBackupText())
    val lastBackupText: StateFlow<String> = _lastBackupText.asStateFlow()

    suspend fun buildExportXml(): String =
        buildHabitsXml(repository.observeHabitsWithDone().first())

    suspend fun hasAnyHabits(): Boolean =
        repository.observeHabitsWithDone().first().isNotEmpty()

    suspend fun importXml(xml: String): Int {
        val parsed = parseHabitsXml(xml, System.currentTimeMillis())
        repository.replaceAllHabits(parsed)
        return parsed.size
    }

    fun markBackupDone() {
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis()).apply()
        _lastBackupText.value = computeLastBackupText()
    }

    private fun computeLastBackupText(): String {
        val lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, -1L)
        if (lastBackupAt < 0) return "Datensicherung · noch kein Backup"
        val days = (System.currentTimeMillis() - lastBackupAt) / 86_400_000L
        val text = when (days) {
            0L -> "heute"
            1L -> "gestern"
            else -> "vor $days Tagen"
        }
        return "Datensicherung · letztes Backup: $text"
    }
}
