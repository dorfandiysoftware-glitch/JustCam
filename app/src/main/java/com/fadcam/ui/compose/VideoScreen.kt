package com.fadcam.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fadcam.CameraType
import com.fadcam.Constants
import com.fadcam.MaximumRecordingDuration
import com.fadcam.R
import com.fadcam.SharedPreferencesManager
import com.fadcam.ui.VideoSettingsFragment
import java.util.Locale

private val DURATIONS = listOf(
    MaximumRecordingDuration.OPTION_NO_LIMIT to R.string.maximum_recording_duration_no_limit,
    MaximumRecordingDuration.OPTION_1_MINUTE to R.string.maximum_recording_duration_1_minute,
    MaximumRecordingDuration.OPTION_2_MINUTES to R.string.maximum_recording_duration_2_minutes,
    MaximumRecordingDuration.OPTION_3_MINUTES to R.string.maximum_recording_duration_3_minutes,
    MaximumRecordingDuration.OPTION_4_MINUTES to R.string.maximum_recording_duration_4_minutes,
    MaximumRecordingDuration.OPTION_5_MINUTES to R.string.maximum_recording_duration_5_minutes,
    MaximumRecordingDuration.OPTION_10_MINUTES to R.string.maximum_recording_duration_10_minutes,
    MaximumRecordingDuration.OPTION_30_MINUTES to R.string.maximum_recording_duration_30_minutes,
    MaximumRecordingDuration.OPTION_1_HOUR to R.string.maximum_recording_duration_1_hour,
    MaximumRecordingDuration.OPTION_2_HOURS to R.string.maximum_recording_duration_2_hours,
    MaximumRecordingDuration.OPTION_3_HOURS to R.string.maximum_recording_duration_3_hours,
    MaximumRecordingDuration.OPTION_4_HOURS to R.string.maximum_recording_duration_4_hours,
    MaximumRecordingDuration.OPTION_5_HOURS to R.string.maximum_recording_duration_5_hours,
)
private val SPLIT_PRESETS = listOf(500 to "500 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB")

/** Entry point used from [VideoSettingsFragment]. */
fun setVideoContent(
    view: ComposeView,
    frag: VideoSettingsFragment,
    prefs: SharedPreferencesManager,
    register: (Runnable) -> Unit,
    onBack: () -> Unit,
    onDual: () -> Unit,
) {
    view.setContent { JustCamAppTheme { VideoScreen(frag, prefs, register, onBack, onDual) } }
}

@Composable
private fun VideoScreen(
    frag: VideoSettingsFragment,
    prefs: SharedPreferencesManager,
    register: (Runnable) -> Unit,
    onBack: () -> Unit,
    onDual: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    remember { register(Runnable { tick++ }); 0 }
    val sp = prefs.sharedPreferences
    fun bump() { tick++ }

    // Reading `tick` makes everything below recompute after a change.
    val cam = remember(tick) { prefs.getCameraSelection() }
    val resolutions = remember(tick) { frag.resolutionOptions() }
    val fpsList = remember(tick) { frag.frameRates() }
    val codecs = remember(tick) { frag.codecs() }
    val lenses = remember(tick) { frag.lensOptions() }
    val zooms = remember(tick) { frag.zoomOptions() }

    SettingsPage(stringResource(R.string.settings_video_title), onBack) {
        // ---------------- Camera ----------------
        item { SectionLabel(stringResource(R.string.jc_video_sec_camera)) }
        item {
            val opts = buildList {
                add(CameraType.FRONT.toString() to stringResource(R.string.button_settings_cam_front))
                add(CameraType.BACK.toString() to stringResource(R.string.button_settings_cam_back))
                if (frag.isDualSupported) add(CameraType.DUAL_PIP.toString() to stringResource(R.string.button_settings_cam_dual))
            }
            ChipRow(stringResource(R.string.setting_cam_title), opts, cam.toString()) { sel ->
                frag.onCameraSelected(sel)
                bump()
                if (sel == CameraType.DUAL_PIP.toString()) onDual()
            }
        }
        if (cam == CameraType.DUAL_PIP) {
            item {
                ActionRow(stringResource(R.string.dual_cam_settings_row_title), stringResource(R.string.dual_cam_settings_row_subtitle), onDual)
            }
        }
        if (cam == CameraType.BACK && lenses.size > 1) {
            item {
                val saved = prefs.selectedBackCameraId
                ChoiceRow(
                    title = stringResource(R.string.setting_back_lens_title),
                    value = lenses.firstOrNull { it.id == saved }?.label ?: lenses.first().label,
                    options = lenses.map { it.id to it.label },
                    selected = saved ?: lenses.first().id,
                ) { frag.onLensSelected(it); bump() }
            }
        }

        // ---------------- Quality ----------------
        item { SectionLabel(stringResource(R.string.jc_video_sec_quality)) }
        item {
            val cur = prefs.getCameraResolution()
            val curId = "${cur.width}x${cur.height}"
            val bothLabel = stringResource(R.string.resolution_badge_both)
            ChoiceRow(
                title = stringResource(R.string.setting_quailty_title),
                value = frag.resolutionLabel(cur),
                options = resolutions.map { it.id to (if (it.both) "${it.label} · $bothLabel" else it.label) },
                selected = curId,
            ) { sel ->
                val (w, h) = sel.split("x").map { it.toInt() }
                sp.edit().putInt(Constants.PREF_VIDEO_RESOLUTION_WIDTH, w).putInt(Constants.PREF_VIDEO_RESOLUTION_HEIGHT, h).apply()
                bump()
            }
        }
        if (fpsList.isNotEmpty()) {
            item {
                ChipRow(
                    stringResource(R.string.setting_framerate_title),
                    fpsList.map { it to "$it fps" },
                    prefs.getSpecificVideoFrameRate(cam),
                ) { prefs.setSpecificVideoFrameRate(cam, it); bump() }
            }
        }
        if (codecs.isNotEmpty()) {
            item {
                ChipRow(
                    stringResource(R.string.setting_codec_title),
                    codecs.map { it to it },
                    prefs.getVideoCodec().toString(),
                    summary = stringResource(R.string.jc_video_codec_tip),
                ) { sp.edit().putString(Constants.PREF_VIDEO_CODEC, it).apply(); bump() }
            }
        }
        bitrate(frag, ::bump, tick)
        if (zooms.size > 1) {
            item {
                val cur = prefs.getSpecificZoomRatio(cam).coerceIn(zooms.first(), zooms.last())
                SliderRow(
                    title = stringResource(R.string.setting_zoom_ratio_title),
                    valueLabel = String.format(Locale.getDefault(), "%.1fx", cur),
                    value = cur, range = zooms.first()..zooms.last(), steps = zooms.size - 2,
                ) { prefs.setSpecificZoomRatio(cam, (Math.round(it * 10) / 10f)); bump() }
            }
        }

        // ---------------- Recording ----------------
        item { SectionLabel(stringResource(R.string.jc_video_sec_recording)) }
        item {
            ChipRow(
                stringResource(R.string.setting_video_orientation_title),
                listOf(
                    SharedPreferencesManager.ORIENTATION_PORTRAIT to stringResource(R.string.video_orientation_portrait),
                    SharedPreferencesManager.ORIENTATION_LANDSCAPE to stringResource(R.string.video_orientation_landscape),
                ),
                prefs.getVideoOrientation(),
            ) { prefs.setVideoOrientation(it); bump() }
        }
        item {
            val supported = frag.isStabilizationSupported
            SwitchRow(
                stringResource(R.string.setting_video_stabilization_title),
                if (supported) stringResource(R.string.video_stabilization_footer) else stringResource(R.string.setting_not_supported),
                checked = supported && prefs.isVideoStabilizationEnabled(), enabled = supported,
            ) { frag.setStabilization(it); bump() }
        }
        item {
            SwitchRow(
                stringResource(R.string.video_setting_location_embed_title),
                stringResource(R.string.helper_location_embed),
                checked = prefs.isLocationEmbeddingEnabled(),
            ) { frag.setLocationEmbedding(it); bump() }
        }
        duration(prefs, ::bump, tick)
        splitting(prefs, ::bump, tick)
    }
}

private fun LazyListScope.bitrate(frag: VideoSettingsFragment, bump: () -> Unit, tick: Int) {
    item {
        val custom = frag.bitrateCustom()
        SwitchRow(
            stringResource(R.string.jc_video_bitrate_auto),
            stringResource(R.string.bitrate_explanation_text),
            checked = !custom,
        ) { auto ->
            frag.setBitrate(!auto, if (auto) 0 else frag.bitrateKbps())
            bump()
        }
    }
    if (frag.bitrateCustom()) {
        item {
            val mbps = (frag.bitrateKbps() / 1000).coerceIn(1, 200)
            SliderRow(
                title = stringResource(R.string.setting_video_bitrate_title),
                valueLabel = "$mbps Mbps",
                value = mbps.toFloat(), range = 1f..200f, steps = 198,
            ) { frag.setBitrate(true, it.toInt() * 1000); bump() }
        }
        item {
            val mbps = frag.bitrateKbps() / 1000
            if (mbps < 3) NoteText(stringResource(R.string.bitrate_info_warning_low))
            else if (mbps > 100) NoteText(stringResource(R.string.bitrate_info_warning_high))
        }
    }
}

private fun LazyListScope.duration(prefs: SharedPreferencesManager, bump: () -> Unit, tick: Int) {
    item {
        var dialog by remember { mutableStateOf(false) }
        val option = prefs.getMaximumRecordingDurationOption()
        val label = if (option == MaximumRecordingDuration.OPTION_CUSTOM) prefs.getCustomMaximumRecordingDurationHuman()
        else stringResource(DURATIONS.firstOrNull { it.first == option }?.second ?: R.string.maximum_recording_duration_no_limit)
        ChoiceRow(
            title = stringResource(R.string.maximum_recording_duration_title),
            value = label,
            options = DURATIONS.map { it.first to stringResource(it.second) } +
                (MaximumRecordingDuration.OPTION_CUSTOM to stringResource(R.string.jc_video_custom)),
            selected = option,
        ) { sel ->
            if (sel == MaximumRecordingDuration.OPTION_CUSTOM) dialog = true
            else { prefs.setMaximumRecordingDurationOption(sel); bump() }
        }
        if (dialog) {
            val init = MaximumRecordingDuration.splitToHms(prefs.getCustomMaximumRecordingDurationSeconds())
            var h by remember { mutableFloatStateOf(init[0].toFloat()) }
            var m by remember { mutableFloatStateOf(init[1].toFloat()) }
            var s by remember { mutableFloatStateOf(init[2].toFloat()) }
            AlertDialog(
                onDismissRequest = { dialog = false },
                title = { Text(stringResource(R.string.maximum_recording_duration_custom_title)) },
                text = {
                    Column {
                        HmsSlider("h", h, 0f..24f) { h = it }
                        HmsSlider("min", m, 0f..59f) { m = it }
                        HmsSlider("s", s, 0f..59f) { s = it }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val total = MaximumRecordingDuration.combineFromHms(h.toInt(), m.toInt(), s.toInt())
                            .coerceAtMost(MaximumRecordingDuration.MAX_CUSTOM_SECONDS)
                        if (total <= 0) prefs.setMaximumRecordingDurationOption(MaximumRecordingDuration.OPTION_NO_LIMIT)
                        else if (prefs.setCustomMaximumRecordingDurationSeconds(total))
                            prefs.setMaximumRecordingDurationOption(MaximumRecordingDuration.OPTION_CUSTOM)
                        dialog = false; bump()
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
    }
    item { NoteText(stringResource(R.string.maximum_recording_duration_summary)) }
}

@Composable
private fun HmsSlider(unit: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("${value.toInt()} $unit", Modifier.padding(end = 12.dp).weight(0.3f), style = MaterialTheme.typography.bodyLarge)
        Slider(value, onChange, Modifier.weight(0.7f), valueRange = range, steps = (range.endInclusive - range.start).toInt() - 1)
    }
}

private fun LazyListScope.splitting(prefs: SharedPreferencesManager, bump: () -> Unit, tick: Int) {
    item {
        SwitchRow(
            stringResource(R.string.video_splitting_title),
            stringResource(R.string.video_splitting_description),
            checked = prefs.isVideoSplittingEnabled(),
        ) { prefs.setVideoSplittingEnabled(it); bump() }
    }
    if (prefs.isVideoSplittingEnabled()) {
        item {
            val mb = prefs.getVideoSplitSizeMb()
            val isPreset = SPLIT_PRESETS.any { it.first == mb }
            ChipRow(
                title = stringResource(R.string.jc_video_split_size),
                options = SPLIT_PRESETS.map { it.first to it.second } + (-1 to stringResource(R.string.jc_video_custom)),
                selected = if (isPreset) mb else -1,
            ) { sel -> if (sel > 0) { prefs.setVideoSplitSizeMb(sel); bump() } else if (isPreset) { prefs.setVideoSplitSizeMb(3072); bump() } }
        }
        item {
            val mb = prefs.getVideoSplitSizeMb()
            if (SPLIT_PRESETS.none { it.first == mb }) {
                SliderRow(
                    title = stringResource(R.string.video_split_custom_title),
                    valueLabel = if (mb >= 1024) String.format(Locale.getDefault(), "%.1f GB", mb / 1024f) else "$mb MB",
                    value = mb.toFloat().coerceIn(100f, 16384f), range = 100f..16384f, steps = 0,
                ) { prefs.setVideoSplitSizeMb((it / 50f).toInt() * 50); bump() }
            }
        }
    }
}
