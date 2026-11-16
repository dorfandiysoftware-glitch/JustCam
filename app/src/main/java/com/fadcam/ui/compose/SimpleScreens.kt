package com.fadcam.ui.compose

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fadcam.Constants
import com.fadcam.R
import com.fadcam.SharedPreferencesManager

/** Compose entry points for the small settings pages (Audio, Behavior, Advanced). */

fun setAudioContent(view: ComposeView, prefs: SharedPreferencesManager, onBack: () -> Unit) {
    view.setContent { JustCamAppTheme { AudioScreen(prefs, onBack) } }
}

fun setBehaviorContent(view: ComposeView, prefs: SharedPreferencesManager, onBack: () -> Unit) {
    view.setContent { JustCamAppTheme { BehaviorScreen(prefs, onBack) } }
}

fun setAdvancedContent(view: ComposeView, onBack: () -> Unit) {
    view.setContent { JustCamAppTheme { AdvancedScreen(onBack) } }
}

// ---------------- Audio ----------------

private fun typeLabel(ctx: Context, type: Int): String = ctx.getString(
    when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.audio_device_type_wired_headset
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> R.string.audio_device_type_wired_headphones
        AudioDeviceInfo.TYPE_USB_DEVICE -> R.string.audio_device_type_usb
        AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.audio_device_type_usb_headset
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.string.audio_device_type_bt_sco
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> R.string.audio_device_type_bt_a2dp
        AudioDeviceInfo.TYPE_BLE_HEADSET -> R.string.audio_device_type_ble_headset
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> R.string.audio_device_type_ble_broadcast
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> R.string.audio_device_type_ble_speaker
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> R.string.audio_device_type_builtin_mic
        else -> R.string.audio_device_type_external
    },
)

/** Phone mic first, then every external input; (label, device or null for the phone mic). */
private fun micList(ctx: Context): List<Pair<String, AudioDeviceInfo?>> {
    val out = mutableListOf<Pair<String, AudioDeviceInfo?>>(ctx.getString(R.string.audio_input_source_phone) to null)
    try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        for (d in am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            when (d.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> out += ctx.getString(R.string.audio_input_source_headphones_no_mic) to null
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_BLE_SPEAKER -> out += typeLabel(ctx, d.type) to d
            }
        }
    } catch (_: Exception) {}
    return out
}

@Composable
private fun AudioScreen(prefs: SharedPreferencesManager, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val rec = remember(tick) { prefs.isRecordAudioEnabled() }
    val raw = remember(tick) { prefs.isRawAudioEnabled() }
    val bitrate = remember(tick) { prefs.getAudioBitrate() }
    val sampling = remember(tick) { prefs.getAudioSamplingRate() }
    val mics = remember(tick) { micList(ctx) }
    val wired = remember(tick) { prefs.getAudioInputSource() == SharedPreferencesManager.AUDIO_INPUT_SOURCE_WIRED }
    val selectedMic = remember(tick, mics) {
        if (!wired) mics.first().first
        else mics.firstOrNull { it.second?.type == prefs.getAudioInputDeviceType() }?.first ?: mics.first().first
    }
    val unprocessed = remember {
        try {
            (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED).equals("true", true)
        } catch (_: Exception) { false }
    }

    SettingsPage(stringResource(R.string.settings_audio_title), onBack) {
        item { SwitchRow(stringResource(R.string.row_record_audio_title), stringResource(R.string.helper_record_audio), rec) { prefs.setRecordAudioEnabled(it); tick++ } }
        item {
            SwitchRow(
                stringResource(R.string.row_raw_audio_title),
                stringResource(R.string.helper_raw_audio) + if (unprocessed) "" else "\n\n" + stringResource(R.string.raw_audio_unprocessed_note),
                raw, enabled = rec,
            ) { prefs.setRawAudioEnabled(it); tick++ }
        }
        item {
            ChoiceRow(
                stringResource(R.string.row_input_source_title), selectedMic, mics.map { it.first to it.first }, selectedMic,
            ) { label ->
                val dev = mics.firstOrNull { it.first == label }?.second
                if (dev == null) {
                    prefs.setAudioInputSource(SharedPreferencesManager.AUDIO_INPUT_SOURCE_PHONE)
                    prefs.setAudioInputDeviceType(-1)
                    prefs.setAudioInputDeviceName(null)
                } else {
                    prefs.setAudioInputSource(SharedPreferencesManager.AUDIO_INPUT_SOURCE_WIRED)
                    prefs.setAudioInputDeviceType(dev.type)
                    prefs.setAudioInputDeviceName(dev.productName?.toString())
                }
                tick++
            }
        }
        item { SectionLabel(stringResource(R.string.settings_advanced_title)) }
        item {
            SliderRow(
                stringResource(R.string.dialog_audio_bitrate_label), "${bitrate / 1000} kbps", bitrate.toFloat(), 64000f..384000f, steps = 19,
            ) { prefs.setAudioBitrate(Math.round(it / 16000f) * 16000); tick++ }
        }
        item { NoteText(stringResource(R.string.helper_audio_bitrate_detail) + "\n" + stringResource(R.string.audio_bitrate_recommended_line)) }
        item {
            ChipRow(
                stringResource(R.string.dialog_audio_sampling_rate_label),
                listOf(44100 to "44100 Hz", 48000 to "48000 Hz · " + stringResource(R.string.label_recommended)), sampling,
                summary = stringResource(R.string.helper_audio_sampling_detail),
            ) { prefs.setAudioSamplingRate(it); tick++ }
        }
    }
}

// ---------------- Behavior ----------------

@Composable
private fun BehaviorScreen(prefs: SharedPreferencesManager, onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val onboarding = remember(tick) { prefs.isShowOnboarding() }
    val autoUpdate = remember(tick) { prefs.sharedPreferences.getBoolean("auto_update_check_enabled", true) }
    SettingsPage(stringResource(R.string.settings_behavior_title), onBack) {
        item {
            SwitchRow(stringResource(R.string.settings_onboarding_title), null, onboarding) { on ->
                // Re-enabling onboarding also clears the "first install checked" flag so it shows again.
                if (on) prefs.sharedPreferences.edit().putBoolean(Constants.FIRST_INSTALL_CHECKED_KEY, false).apply()
                prefs.setShowOnboarding(on); tick++
            }
        }
        item {
            SwitchRow(stringResource(R.string.settings_auto_update_check), null, autoUpdate) {
                prefs.sharedPreferences.edit().putBoolean("auto_update_check_enabled", it).apply(); tick++
            }
        }
    }
}

// ---------------- Advanced ----------------

@Composable
private fun AdvancedScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val soon = { Toast.makeText(ctx, R.string.mini_app_coming_soon_desc, Toast.LENGTH_SHORT).show() }
    val badge = stringResource(R.string.remote_coming_soon_badge)
    SettingsPage(stringResource(R.string.settings_advanced_title), onBack) {
        soonRow(R.string.thermal_guardian_title, R.string.thermal_guardian_row_subtitle, badge, soon)
        soonRow(R.string.audio_vision_title, R.string.audio_vision_row_subtitle, badge, soon)
        soonRow(R.string.scheduled_recording_title, R.string.scheduled_recording_row_subtitle, badge, soon)
        soonRow(R.string.profiles_title, R.string.profiles_row_subtitle, badge, soon)
        soonRow(R.string.fadcrypt_title, R.string.fadcrypt_row_subtitle, badge, soon)
    }
}

private fun LazyListScope.soonRow(title: Int, subtitle: Int, badge: String, onClick: () -> Unit) {
    item { ActionRow(androidx.compose.ui.res.stringResource(title), androidx.compose.ui.res.stringResource(subtitle) + " · " + badge, onClick) }
}
