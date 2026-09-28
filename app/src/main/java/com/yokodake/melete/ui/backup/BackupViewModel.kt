package com.yokodake.melete.ui.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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
import com.yokodake.melete.data.plan.ImportCheck
import com.yokodake.melete.data.plan.ImportMode
import com.yokodake.melete.data.plan.ImportScope
import com.yokodake.melete.data.plan.PlanFile
import com.yokodake.melete.data.plan.PlanImporter
import com.yokodake.melete.data.plan.PlanUnreadable
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

/** A plan file read and checked, waiting for a mode, a scope and a yes. */
data class PendingPlan(
    val file: PlanFile,
    val source: String,
    val mode: ImportMode,
    val scope: ImportScope,
    /** The verdict for [mode] and [scope]: a name the library resolves when adding may not exist when replacing. */
    val check: ImportCheck,
)

/** A copy of the record saved automatically before a restore replaced it. */
data class SafetyCopy(val file: File, val label: String)

data class BackupUiState(
    val busy: Boolean = false,
    /** What is on the phone now, for comparing with what a restore would put back. */
    val current: BackupSummary? = null,
    val pending: PendingRestore? = null,
    val pendingPlan: PendingPlan? = null,
    /** Why the chosen file cannot be restored, one reason per line. Nothing was changed. */
    val problems: List<String> = emptyList(),
    /** What the problems are about, for the dialog's title. */
    val problemsTitle: String = "Can't restore this file",
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
    private val importer: PlanImporter,
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
        val (text, name) = read(uri)
        offer(service.decode(text), source = name ?: "Selected backup")
    }

    /** A picked file's text and display name. */
    private suspend fun read(uri: Uri): Pair<String, String?> = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw BackupUnreadable("That file could not be opened.")
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        text to name?.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ plans

    /** Reads a picked plan and shows what adding it would do. Nothing is written yet. */
    fun loadPlan(uri: Uri) = work(problemsTitle = "Can't import this file") {
        val (text, name) = read(uri)
        val file = importer.decode(text)
        val mode = ImportMode.ADD
        // The past stays as it was unless it is deliberately included.
        val scope = ImportScope.FROM_TODAY
        _state.update {
            it.copy(pendingPlan = PendingPlan(file, name ?: "Selected plan", mode, scope, importer.check(file, mode, scope)))
        }
    }

    /** Switches between adding and replacing, re-judging the file for the new mode. */
    fun setPlanMode(mode: ImportMode) = recheck { it.copy(mode = mode) }

    /** Switches between from-today and including the past, re-judging the file. */
    fun setPlanScope(scope: ImportScope) = recheck { it.copy(scope = scope) }

    private fun recheck(change: (PendingPlan) -> PendingPlan) {
        val pending = _state.value.pendingPlan ?: return
        val next = change(pending)
        if (next == pending) return
        work(problemsTitle = "Can't import this file") {
            val check = importer.check(next.file, next.mode, next.scope)
            _state.update { it.copy(pendingPlan = next.copy(check = check)) }
        }
    }

    fun cancelPlan() = _state.update { it.copy(pendingPlan = null) }

    /** Imports the pending plan in its chosen mode, atomically; a replace saves a copy first. */
    fun confirmPlan() {
        val pending = _state.value.pendingPlan ?: return
        if (pending.check.resolution.plan == null) return
        _state.update { it.copy(pendingPlan = null) }
        work(problemsTitle = "Can't import this file") {
            importer.import(pending.file, pending.mode, pending.scope, safetyDirectory)
            _state.update { it.copy(message = "Plan imported") }
            refresh()
        }
    }

    /** Offers a copy saved before an earlier restore — the way back from a restore. */
    fun loadSafetyCopy(copy: SafetyCopy) = work {
        val text = withContext(Dispatchers.IO) { copy.file.readText() }
        offer(service.decode(text), source = copy.file.name)
    }

    private fun offer(backup: MeleteBackup, source: String) {
        val problems = BackupValidator.problems(backup)
        _state.update {
            if (problems.isEmpty()) {
                it.copy(pending = PendingRestore(backup, service.summarise(backup), source), problems = emptyList())
            } else {
                it.copy(pending = null, problems = problems, problemsTitle = "Can't restore this file")
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
            service.writeSafetyCopy(safetyDirectory)
            service.restore(pending.backup)
            _state.update {
                it.copy(message = "Backup restored")
            }
            refresh()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Runs one step with the busy flag up, turning any failure into words rather than a crash. */
    private fun work(problemsTitle: String = "Can't restore this file", block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (unreadable: BackupUnreadable) {
                _state.update { it.copy(problems = listOf(unreadable.message.orEmpty()), problemsTitle = problemsTitle) }
            } catch (unreadable: PlanUnreadable) {
                _state.update { it.copy(problems = listOf(unreadable.message.orEmpty()), problemsTitle = problemsTitle) }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        problems = listOf("Nothing was changed: ${failure.message ?: "unexpected error"}."),
                        problemsTitle = problemsTitle,
                    )
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
                    importer = application.container.planImporter,
                    resolver = application.contentResolver,
                    safetyDirectory = File(application.filesDir, "backups"),
                )
            }
        }
    }
}
