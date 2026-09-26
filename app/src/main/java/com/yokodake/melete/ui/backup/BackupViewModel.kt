package com.yokodake.melete.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.backup.BackupSummary
import com.yokodake.melete.data.backup.BackupUnreadable
import com.yokodake.melete.data.backup.BackupValidator
import com.yokodake.melete.data.backup.MeleteBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A backup read and validated, waiting for the yes that replaces the record with it. */
data class PendingRestore(
    val backup: MeleteBackup,
    val summary: BackupSummary,
    /** Where it came from, in words: a chosen file, or a copy saved before an earlier restore. */
    val source: String,
)

/** A copy of the record saved automatically before a restore replaced it. */
data class SafetyCopy(val file: File, val label: String)

data class BackupUiState(
    val busy: Boolean = false,
    /** What is on the phone now, for comparing with what a restore would put back. */
    val current: BackupSummary? = null,
    val pending: PendingRestore? = null,
    /** Why the chosen file cannot be restored, one reason per line. Nothing was changed. */
    val problems: List<String> = emptyList(),
    val safetyCopies: List<SafetyCopy> = emptyList(),
    val message: String? = null,
)

/**
 * Export and restore, behind one screen.
 *
 * A restore never starts on a file that has not been read and validated whole, never without an
 * explicit yes, and never without first saving what it is about to replace.
 */
class BackupViewModel(
    private val service: BackupService,
    private val resolver: ContentResolver,
    private val safetyDirectory: File,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val current = service.summarise(service.export())
            _state.update { it.copy(current = current, safetyCopies = listSafetyCopies()) }
        }
    }

    private suspend fun listSafetyCopies(): List<SafetyCopy> = withContext(Dispatchers.IO) {
        safetyDirectory.listFiles { file -> file.name.endsWith(".json") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { SafetyCopy(it, it.nameWithoutExtension.removePrefix("melete-before-restore-")) }
    }

    /** Writes the whole record to the file the user picked. */
    fun exportTo(uri: Uri) = work {
        val text = service.encode(service.export())
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                ?: throw BackupUnreadable("That location could not be written to.")
        }
        _state.update { it.copy(message = "Backup saved") }
    }

    /** Reads and validates a picked file; offers it for restore only if it is sound. */
    fun load(uri: Uri) = work {
        val text = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } ?: throw BackupUnreadable("That file could not be opened.")
        offer(service.decode(text), source = "the chosen file")
    }

    /** Offers a copy saved before an earlier restore — the way back from a restore. */
    fun loadSafetyCopy(copy: SafetyCopy) = work {
        val text = withContext(Dispatchers.IO) { copy.file.readText() }
        offer(service.decode(text), source = "the copy saved ${copy.label}")
    }

    private fun offer(backup: MeleteBackup, source: String) {
        val problems = BackupValidator.problems(backup)
        _state.update {
            if (problems.isEmpty()) {
                it.copy(pending = PendingRestore(backup, service.summarise(backup), source), problems = emptyList())
            } else {
                it.copy(pending = null, problems = problems)
            }
        }
    }

    fun cancelRestore() = _state.update { it.copy(pending = null) }

    fun dismissProblems() = _state.update { it.copy(problems = emptyList()) }

    /** Saves the current record, then replaces it with the pending backup, atomically. */
    fun confirmRestore() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        work {
            val saved = service.writeSafetyCopy(safetyDirectory)
            service.restore(pending.backup)
            _state.update {
                it.copy(message = "Restored. What was here before is kept below as ${saved.nameWithoutExtension}.")
            }
            refresh()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Runs one step with the busy flag up, turning any failure into words rather than a crash. */
    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (unreadable: BackupUnreadable) {
                _state.update { it.copy(problems = listOf(unreadable.message.orEmpty())) }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(problems = listOf("Nothing was changed: ${failure.message ?: "unexpected error"}."))
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                BackupViewModel(
                    service = application.container.backupService,
                    resolver = application.contentResolver,
                    safetyDirectory = File(application.filesDir, "backups"),
                )
            }
        }
    }
}
