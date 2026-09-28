package com.yokodake.melete.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.BuildConfig
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.R
import com.yokodake.melete.data.AppSettings
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.KeepAwake
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.ChoiceField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val bodyweightTracked: Boolean = false,
    val reminderMonths: Int = 6,
    val keepAwake: KeepAwake = KeepAwake.WHILE_TIMING,
)

class SettingsViewModel(
    private val diary: DiaryRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        diary.observeBodyweightTracked(),
        settings.benchmarkReminderMonths,
        settings.keepAwake,
    ) { bodyweight, months, awake -> SettingsUiState(bodyweight, months, awake) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setBodyweightTracked(value: Boolean) {
        viewModelScope.launch { diary.setBodyweightTracked(value) }
    }

    fun setReminderMonths(months: Int) = settings.setBenchmarkReminderMonths(months)

    fun setKeepAwake(value: KeepAwake) = settings.setKeepAwake(value)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                SettingsViewModel(application.container.diaryRepository, application.container.settings)
            }
        }
    }
}

/**
 * Settings: how the app behaves, and where its record goes in and out. Preferences live here and
 * nowhere else; Import / export has its permanent place here too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onOpenImportExport: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Heading("Training") }
            item {
                SettingRow("Track bodyweight", "A Bodyweight field in the daily notes") {
                    Switch(
                        checked = state.bodyweightTracked,
                        onCheckedChange = viewModel::setBodyweightTracked,
                        modifier = Modifier.semantics { contentDescription = "Track bodyweight" },
                    )
                }
            }
            item {
                SettingRow("Benchmark reminders", null) {
                    ChoiceField(
                        value = state.reminderMonths,
                        options = AppSettings.REMINDER_CHOICES,
                        optionLabel = { if (it == 0) "Off" else "After $it months" },
                        onSelect = viewModel::setReminderMonths,
                        modifier = Modifier.width(170.dp),
                    )
                }
            }

            item { Heading("Display") }
            item {
                // A placeholder until appearance is built: the app follows the system for now.
                SettingRow("Appearance", "Follows the system", enabled = false) {
                    Text("System", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SettingRow("Keep screen awake", null) {
                    ChoiceField(
                        value = state.keepAwake,
                        options = KeepAwake.entries,
                        optionLabel = KeepAwake::label,
                        onSelect = viewModel::setKeepAwake,
                        modifier = Modifier.width(170.dp),
                    )
                }
            }

            item { Heading("Data") }
            item {
                Surface(
                    onClick = onOpenImportExport,
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_import_export),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Import / export", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Heading("About") }
            item { About() }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        control()
    }
}

/** Which app this is, plainly: the debug build says so first, since it holds disposable data. */
@Composable
private fun About() {
    val debug = BuildConfig.DEBUG
    Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (debug) "Melete Debug" else "Melete",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (debug) FontWeight.SemiBold else FontWeight.Normal,
            color = if (debug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = BuildConfig.APPLICATION_ID,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
