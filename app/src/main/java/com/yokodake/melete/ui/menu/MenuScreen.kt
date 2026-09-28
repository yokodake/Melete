package com.yokodake.melete.ui.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yokodake.melete.R

/**
 * One place in the menu. [onOpen] is null for the entries whose phases have not built them yet —
 * Profile and Benchmarks (7), Settings — which are listed now so the menu has its final shape.
 */
private data class MenuEntry(
    val label: String,
    @param:DrawableRes val icon: Int,
    val onOpen: (() -> Unit)?,
)

/**
 * The Menu tab: everything that is not a day of training — the library, the record's files and,
 * later, the profile, benchmarks and settings.
 */
@Composable
fun MenuRoute(
    onOpenLibrary: () -> Unit,
    onOpenImportExport: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    MenuScreen(
        entries = listOf(
            MenuEntry("Profile", R.drawable.ic_menu_profile, null),
            MenuEntry("Benchmarks", R.drawable.ic_menu_benchmarks, null),
            MenuEntry("Library", R.drawable.ic_nav_library, onOpenLibrary),
            MenuEntry("Import / export", R.drawable.ic_menu_import_export, onOpenImportExport),
            MenuEntry("Settings", R.drawable.ic_menu_settings, null),
        ),
        bottomBar = bottomBar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuScreen(entries: List<MenuEntry>, bottomBar: @Composable () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Menu", style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp),
        ) {
            items(items = entries, key = { it.label }) { entry -> MenuRow(entry) }
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry) {
    // Not-yet-built entries still take a tap and simply do nothing, as agreed, until their phase.
    Surface(
        onClick = entry.onOpen ?: {},
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                painter = painterResource(entry.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
