package com.fadcam.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fadcam.R
import com.fadcam.SharedPreferencesManager

/** Compose pages for Automation, Location, Notifications, Security and Storage. */

/** Lets the Java host ask a page to re-read prefs after a dialog / permission result. */
class Refresher {
    var tick by mutableIntStateOf(0)
    fun bump() { tick++ }
}

private fun host(view: ComposeView, content: @Composable () -> Unit) {
    view.setContent { JustCamAppTheme { content() } }
}

// ---------- Automation ----------
fun setAutomationContent(view: ComposeView, prefs: SharedPreferencesManager, onBack: () -> Unit) = host(view) {
    var on by remember { mutableStateOf(prefs.isVolumeShutterEnabled()) }
    SettingsPage(stringResource(R.string.automation_title), onBack) {
        item {
            SwitchRow(
                stringResource(R.string.setting_volume_shutter_title), stringResource(R.string.setting_volume_shutter_summary), on,
            ) { on = it; prefs.setVolumeShutterEnabled(it) }
        }
        item { NoteText(stringResource(R.string.setting_volume_shutter_footer)) }
    }
}

// ---------- Location ----------
fun setLocationContent(
    view: ComposeView, prefs: SharedPreferencesManager, refresher: Refresher,
    onToggle: () -> Unit, onBack: () -> Unit,
) = host(view) {
    val on = refresher.tick.let { prefs.isLocationEmbeddingEnabled() }
    SettingsPage(stringResource(R.string.settings_location_privacy_title), onBack) {
        item { SwitchRow(stringResource(R.string.settings_embed_location_metadata), null, on) { onToggle() } }
    }
}

// ---------- Notifications ----------
fun setNotificationContent(view: ComposeView, prefs: SharedPreferencesManager, onBack: () -> Unit) = host(view) {
    var preset by remember { mutableStateOf(prefs.getNotificationPreset() ?: SharedPreferencesManager.NOTIFICATION_PRESET_DEFAULT) }
    var hideStop by remember { mutableStateOf(prefs.isNotificationStopButtonHidden()) }
    var title by remember { mutableStateOf(prefs.getCustomNotificationTitle() ?: "") }
    var text by remember { mutableStateOf(prefs.getCustomNotificationText() ?: "") }
    val custom = preset == SharedPreferencesManager.NOTIFICATION_PRESET_CUSTOM
    val presets = listOf(
        SharedPreferencesManager.NOTIFICATION_PRESET_DEFAULT to stringResource(R.string.notification_preset_default),
        SharedPreferencesManager.NOTIFICATION_PRESET_SYSTEM_UPDATE to stringResource(R.string.notification_preset_system_update),
        SharedPreferencesManager.NOTIFICATION_PRESET_DOWNLOADING to stringResource(R.string.notification_preset_downloading),
        SharedPreferencesManager.NOTIFICATION_PRESET_SYNCING to stringResource(R.string.notification_preset_syncing),
        SharedPreferencesManager.NOTIFICATION_PRESET_CUSTOM to stringResource(R.string.notification_preset_custom),
    )
    val pTitle: String
    val pText: String
    when (preset) {
        SharedPreferencesManager.NOTIFICATION_PRESET_SYSTEM_UPDATE -> {
            pTitle = stringResource(R.string.notification_title_system_update); pText = stringResource(R.string.notification_text_system_update_progress)
        }
        SharedPreferencesManager.NOTIFICATION_PRESET_DOWNLOADING -> {
            pTitle = stringResource(R.string.notification_title_downloading); pText = stringResource(R.string.notification_text_downloading_progress)
        }
        SharedPreferencesManager.NOTIFICATION_PRESET_SYNCING -> {
            pTitle = stringResource(R.string.notification_title_syncing); pText = stringResource(R.string.notification_text_syncing_progress)
        }
        SharedPreferencesManager.NOTIFICATION_PRESET_CUSTOM -> {
            pTitle = title.ifEmpty { stringResource(R.string.notification_title_custom_default) }
            pText = text.ifEmpty { stringResource(R.string.notification_text_custom_default) }
        }
        else -> {
            pTitle = stringResource(R.string.notification_video_recording); pText = stringResource(R.string.notification_video_recording_progress_description)
        }
    }
    SettingsPage(stringResource(R.string.recording_notification_title), onBack) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.notification_preview_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(pTitle, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                    Text(pText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!hideStop) Text(stringResource(R.string.notification_stop), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            ChipRow(stringResource(R.string.notification_style_row_title), presets, preset, summary = stringResource(R.string.notification_preset_helper)) {
                preset = it; prefs.setNotificationPreset(it)
            }
        }
        if (custom) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it; prefs.setCustomNotificationTitle(it) },
                        label = { Text(stringResource(R.string.notification_title_custom_default)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = text, onValueChange = { text = it; prefs.setCustomNotificationText(it) },
                        label = { Text(stringResource(R.string.notification_text_custom_default)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
        item {
            SwitchRow(
                stringResource(R.string.notification_hide_stop_button), stringResource(R.string.notification_hide_stop_button_description), hideStop,
            ) { hideStop = it; prefs.setNotificationStopButtonHidden(it) }
        }
    }
}

// ---------- Security ----------
fun setSecurityContent(
    view: ComposeView, prefs: SharedPreferencesManager, refresher: Refresher,
    onAppLock: () -> Unit, onCloak: () -> Unit, onPrivacy: () -> Unit, onBack: () -> Unit,
) = host(view) {
    val t = refresher.tick
    val en = stringResource(R.string.universal_enable)
    val dis = stringResource(R.string.universal_disable)
    SettingsPage(stringResource(R.string.settings_security_title), onBack) {
        item { ActionRow(stringResource(R.string.setting_applock_title), if (t >= 0 && prefs.isAppLockEnabled()) en else dis, onAppLock) }
        item { ActionRow(stringResource(R.string.setting_cloak_recents_title), if (t >= 0 && prefs.isCloakRecentsEnabled()) en else dis, onCloak) }
        item { ActionRow(stringResource(R.string.privacy_black_mode_title), if (t >= 0 && prefs.isPrivacyBlackModeEnabled()) en else dis, onPrivacy) }
    }
}

// ---------- Storage ----------
fun setStorageContent(view: ComposeView, refresher: Refresher, summary: () -> String, onOpen: () -> Unit, onBack: () -> Unit) = host(view) {
    val s = refresher.tick.let { summary() }
    SettingsPage(stringResource(R.string.storage_location_title), onBack) {
        item { ActionRow(stringResource(R.string.storage_location_title), s, onOpen) }
    }
}
