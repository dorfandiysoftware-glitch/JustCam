package com.fadcam.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fadcam.R

/** One row of the settings list. */
data class SettingsItem(
    val key: String,
    val icon: Int,
    val title: Int,
    val subtitle: Int? = null,
)

data class SettingsSection(val title: Int, val items: List<SettingsItem>)

val settingsSections: List<SettingsSection> = listOf(
    SettingsSection(
        R.string.recording_quick_access, listOf(
            SettingsItem("video", R.drawable.play_button_rounded, R.string.settings_video_title, R.string.setting_video_bitrate_title),
            SettingsItem("audio", R.drawable.ic_audio, R.string.setting_audio_title, R.string.button_audio_settings),
            SettingsItem("watermark", R.drawable.ic_watermark, R.string.settings_watermark_title, R.string.settings_watermark_subtitle),
            SettingsItem("screen", R.drawable.screen_recorder, R.string.screen_rec_settings_title, R.string.screen_rec_settings_subtitle),
        )
    ),
    SettingsSection(
        R.string.general, listOf(
            SettingsItem("appearance", R.drawable.ic_theme, R.string.setting_theme_title, R.string.settings_option_theme),
            SettingsItem("player", R.drawable.play_button_rounded, R.string.video_player_settings_title, R.string.video_player_settings_subtitle),
            SettingsItem("storage", R.drawable.ic_custom_location, R.string.storage_location_title, R.string.storage_location_description),
            SettingsItem("notifications", R.drawable.ic_info, R.string.settings_notifications_title, R.string.settings_notifications_subtitle),
            SettingsItem("security", R.drawable.ic_lock, R.string.settings_security_title, R.string.setting_applock_title),
            SettingsItem("widgets", R.drawable.ic_home, R.string.shortcuts_widgets_screen_title, R.string.shortcuts_pin_to_home),
        )
    ),
    SettingsSection(
        R.string.settings_advanced_title, listOf(
            SettingsItem("haptics", R.drawable.ic_settings, R.string.settings_haptics_title, R.string.settings_haptics_subtitle),
            SettingsItem("automation", R.drawable.ic_settings, R.string.automation_title, R.string.automation_row_subtitle),
        )
    ),
    SettingsSection(
        R.string.settings_section_app, listOf(
            SettingsItem("trash", R.drawable.ic_delete, R.string.trash_settings_title, R.string.trash_settings_subtitle),
            SettingsItem("backup", R.drawable.ic_content_copy, R.string.prefs_import_export_title, R.string.prefs_import_export_subtitle),
            SettingsItem("debug", R.drawable.ic_debug, R.string.setting_debug_title),
            SettingsItem("about", R.drawable.ic_info, R.string.nav_about, R.string.about_version_update_licenses),
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    showTrash: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onHideTrash: () -> Unit = {},
) {
    var confirmHide by remember { mutableStateOf(false) }
    if (confirmHide) {
        AlertDialog(
            onDismissRequest = { confirmHide = false },
            title = { Text(stringResource(R.string.trash_settings_hide_dialog_title)) },
            text = { Text(stringResource(R.string.trash_settings_hide_dialog_message)) },
            confirmButton = { TextButton(onClick = { confirmHide = false; onHideTrash() }) { Text(stringResource(R.string.trash_settings_hide_dialog_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmHide = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.setup_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            for (section in settingsSections) {
                val items = section.items.filter { showTrash || it.key != "trash" }
                if (items.isEmpty()) continue
                item(key = "h_${section.title}") {
                    Text(
                        text = stringResource(section.title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                items.forEachIndexed { index, item ->
                    item(key = item.key) {
                        SettingsRow(
                            item = item,
                            first = index == 0,
                            last = index == items.lastIndex,
                            onClick = { onOpen(item.key) },
                            onTrailing = if (item.key == "trash") ({ confirmHide = true }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsItem, first: Boolean, last: Boolean, onClick: () -> Unit, onTrailing: (() -> Unit)? = null) {
    val outer = 24.dp
    val inner = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (first) outer else inner,
        topEnd = if (first) outer else inner,
        bottomStart = if (last) outer else inner,
        bottomEnd = if (last) outer else inner,
    )
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(item.subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            if (onTrailing != null) {
                IconButton(onClick = onTrailing) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.trash_settings_hide_button), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
