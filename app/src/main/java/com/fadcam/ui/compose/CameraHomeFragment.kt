package com.fadcam.ui.compose

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fadcam.CameraType
import com.fadcam.Constants
import com.fadcam.MainActivity
import com.fadcam.R
import com.fadcam.RecordingState
import com.fadcam.SharedPreferencesManager
import com.fadcam.dualcam.service.DualCameraRecordingService
import com.fadcam.fadrec.MediaProjectionHelper
import com.fadcam.fadrec.ScreenRecordingState
import com.fadcam.services.RecordingService
import com.fadcam.ui.HomeSidebarFragment
import com.fadcam.utils.ServiceStartPolicy
import kotlinx.coroutines.delay

/** New JustCam main screen: Compose Material 3 front-end for the existing recording services. */
class CameraHomeFragment : Fragment() {

    private lateinit var prefs: SharedPreferencesManager
    private lateinit var projection: MediaProjectionHelper

    // ---- UI state ----
    private var mode by mutableStateOf(0) // 0 FadCam, 1 FadRec, 2 FadMic
    private var camState by mutableStateOf(RecordingState.NONE)
    private var dualActive by mutableStateOf(false)
    private var screenState by mutableStateOf(ScreenRecordingState.NONE)
    private var previewOnly by mutableStateOf(false)
    private var torchOn by mutableStateOf(false)
    private var front by mutableStateOf(false)
    private var dualMode by mutableStateOf(false)
    private var muted by mutableStateOf(false)
    private var startElapsed by mutableLongStateOf(0L)    // SystemClock.elapsedRealtime at start
    private var pausedAccum by mutableLongStateOf(0L)
    private var pauseStartedAt by mutableLongStateOf(0L)

    private var texture: TextureView? = null
    private var surface: Surface? = null
    private var receiverRegistered = false

    private val camRecording get() = camState == RecordingState.IN_PROGRESS || camState == RecordingState.PAUSED || camState == RecordingState.STARTING
    private val screenRecording get() = screenState == ScreenRecordingState.IN_PROGRESS || screenState == ScreenRecordingState.PAUSED

    private val capturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            projection.startScreenRecording(Activity.RESULT_OK, data, forceNoAudioThisStart)
        }
        forceNoAudioThisStart = false
    }
    private var forceNoAudioThisStart = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        forceNoAudioThisStart = !granted
        launchCapture()
    }
    private val camPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startCamera() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Constants.BROADCAST_ON_RECORDING_STARTED, Constants.BROADCAST_ON_RECORDING_STATE_CALLBACK -> {
                    val st = i.getSerializableExtra(Constants.INTENT_EXTRA_RECORDING_STATE) as? RecordingState
                    if (st != null) camState = st
                    previewOnly = i.getBooleanExtra(Constants.EXTRA_PREVIEW_ONLY_ACTIVE, previewOnly)
                    val t = i.getLongExtra(Constants.INTENT_EXTRA_RECORDING_START_TIME, 0L)
                    if (t > 0) startElapsed = t
                    pauseStartedAt = i.getLongExtra(Constants.INTENT_EXTRA_RECORDING_PAUSE_STARTED_AT, 0L)
                    pausedAccum = i.getLongExtra(Constants.INTENT_EXTRA_RECORDING_ACCUMULATED_PAUSED_DURATION, 0L)
                    if (camRecording) pushSurface()
                }
                Constants.BROADCAST_ON_RECORDING_STOPPED -> { camState = RecordingState.NONE; startElapsed = 0 }
                Constants.BROADCAST_ON_RECORDING_PAUSED -> { camState = RecordingState.PAUSED; pauseStartedAt = SystemClock.elapsedRealtime() }
                Constants.BROADCAST_ON_RECORDING_RESUMED -> {
                    if (pauseStartedAt > 0) pausedAccum += SystemClock.elapsedRealtime() - pauseStartedAt
                    pauseStartedAt = 0; camState = RecordingState.IN_PROGRESS
                }
                Constants.BROADCAST_ON_PREVIEW_ONLY_STARTED -> { previewOnly = true; pushSurface() }
                Constants.BROADCAST_ON_PREVIEW_ONLY_STOPPED -> previewOnly = false
                Constants.BROADCAST_ON_DUAL_RECORDING_STARTED -> {
                    dualActive = true; camState = RecordingState.IN_PROGRESS
                    startElapsed = SystemClock.elapsedRealtime(); pausedAccum = 0; pauseStartedAt = 0
                }
                Constants.BROADCAST_ON_DUAL_RECORDING_STOPPED -> { dualActive = false; camState = RecordingState.NONE; startElapsed = 0 }
                Constants.BROADCAST_ON_DUAL_RECORDING_PAUSED -> { camState = RecordingState.PAUSED; pauseStartedAt = SystemClock.elapsedRealtime() }
                Constants.BROADCAST_ON_DUAL_RECORDING_RESUMED -> {
                    if (pauseStartedAt > 0) pausedAccum += SystemClock.elapsedRealtime() - pauseStartedAt
                    pauseStartedAt = 0; camState = RecordingState.IN_PROGRESS
                }
                Constants.BROADCAST_ON_TORCH_STATE_CHANGED ->
                    torchOn = i.getBooleanExtra(Constants.INTENT_EXTRA_TORCH_STATE, torchOn)
                Constants.BROADCAST_ON_CAMERA_SWITCH_COMPLETE -> front = prefs.cameraSelection == CameraType.FRONT
                Constants.BROADCAST_ON_SCREEN_RECORDING_STARTED -> {
                    screenState = ScreenRecordingState.IN_PROGRESS
                    startElapsed = SystemClock.elapsedRealtime(); pausedAccum = 0; pauseStartedAt = 0
                }
                Constants.BROADCAST_ON_SCREEN_RECORDING_STOPPED -> { screenState = ScreenRecordingState.NONE; startElapsed = 0 }
                Constants.BROADCAST_ON_SCREEN_RECORDING_PAUSED -> { screenState = ScreenRecordingState.PAUSED; pauseStartedAt = SystemClock.elapsedRealtime() }
                Constants.BROADCAST_ON_SCREEN_RECORDING_RESUMED -> {
                    if (pauseStartedAt > 0) pausedAccum += SystemClock.elapsedRealtime() - pauseStartedAt
                    pauseStartedAt = 0; screenState = ScreenRecordingState.IN_PROGRESS
                }
                Constants.BROADCAST_ON_SCREEN_RECORDING_STATE_CALLBACK -> {
                    i.getStringExtra("recordingState")?.let {
                        runCatching { ScreenRecordingState.valueOf(it) }.getOrNull()?.let { s ->
                            if (s != ScreenRecordingState.STOPPING) screenState = s
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SharedPreferencesManager.getInstance(requireContext())
        projection = MediaProjectionHelper(requireContext())
        mode = when (prefs.currentRecordingMode) {
            Constants.MODE_FADREC -> 1
            Constants.MODE_FADMIC -> 2
            else -> 0
        }
        front = prefs.cameraSelection == CameraType.FRONT
        dualMode = prefs.isDualCameraModeEnabled
        muted = prefs.isAudioMuted
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val amoled = prefs.sharedPreferences.getString(Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME) == "Faded Night"
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { JustCamTheme(amoled = amoled) { CameraScreen() } }
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            listOf(
                Constants.BROADCAST_ON_RECORDING_STARTED, Constants.BROADCAST_ON_RECORDING_STATE_CALLBACK,
                Constants.BROADCAST_ON_RECORDING_STOPPED, Constants.BROADCAST_ON_RECORDING_PAUSED,
                Constants.BROADCAST_ON_RECORDING_RESUMED, Constants.BROADCAST_ON_PREVIEW_ONLY_STARTED,
                Constants.BROADCAST_ON_PREVIEW_ONLY_STOPPED, Constants.BROADCAST_ON_DUAL_RECORDING_STARTED,
                Constants.BROADCAST_ON_DUAL_RECORDING_STOPPED, Constants.BROADCAST_ON_DUAL_RECORDING_PAUSED,
                Constants.BROADCAST_ON_DUAL_RECORDING_RESUMED, Constants.BROADCAST_ON_TORCH_STATE_CHANGED,
                Constants.BROADCAST_ON_CAMERA_SWITCH_COMPLETE, Constants.BROADCAST_ON_SCREEN_RECORDING_STARTED,
                Constants.BROADCAST_ON_SCREEN_RECORDING_STOPPED, Constants.BROADCAST_ON_SCREEN_RECORDING_PAUSED,
                Constants.BROADCAST_ON_SCREEN_RECORDING_RESUMED, Constants.BROADCAST_ON_SCREEN_RECORDING_STATE_CALLBACK,
            ).forEach { addAction(it) }
        }
        ContextCompat.registerReceiver(requireContext(), receiver, f, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
        // Ask running services for their state (never starts an idle one).
        if (prefs.isRecordingInProgress) {
            runCatching {
                ServiceStartPolicy.startRecordingAction(requireContext(),
                    Intent(requireContext(), RecordingService::class.java).setAction(Constants.BROADCAST_ON_RECORDING_STATE_REQUEST))
            }
        }
        if (prefs.screenRecordingState != "NONE") {
            runCatching {
                requireContext().startService(Intent(requireContext(), com.fadcam.fadrec.services.ScreenRecordingService::class.java)
                    .setAction(Constants.INTENT_ACTION_QUERY_SCREEN_RECORDING_STATE))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (receiverRegistered) { runCatching { requireContext().unregisterReceiver(receiver) }; receiverRegistered = false }
    }

    // ---- Called by MainActivity ----
    fun swipeMode(delta: Int): Boolean {
        val n = mode + delta
        if (n < 0 || n > 2) return false
        selectMode(n)
        return true
    }

    fun handleVolumeShutterLongPress() = primaryAction()
    fun handleVolumeShutterClick() { if (mode == 0) capturePhoto() }
    fun handleVolumeShutterCameraSwitch() { if (mode == 0) switchCamera() }

    private fun selectMode(m: Int) {
        mode = m
        prefs.currentRecordingMode = when (m) { 1 -> Constants.MODE_FADREC; 2 -> Constants.MODE_FADMIC; else -> Constants.MODE_FADCAM }
    }

    // ---- Actions ----
    private fun primaryAction() {
        when (mode) {
            0 -> if (camRecording) stopCamera() else startCamera()
            1 -> if (screenRecording) projection.stopScreenRecording() else startScreen()
        }
    }

    private var startingSince = 0L

    /** Safety net: keep UI in sync with persisted service state even if a broadcast is missed. */
    private fun syncFromPrefs(now: Long) {
        if (!dualActive) {
            val r = prefs.isRecordingInProgress
            if (r && camState == RecordingState.NONE) {
                camState = RecordingState.IN_PROGRESS
                if (startElapsed == 0L) startElapsed = now
            } else if (!r && (camState == RecordingState.IN_PROGRESS || camState == RecordingState.PAUSED)) {
                camState = RecordingState.NONE; startElapsed = 0
            } else if (camState == RecordingState.STARTING) {
                if (r) { camState = RecordingState.IN_PROGRESS; if (startElapsed == 0L) startElapsed = now }
                else if (now - startingSince > 8000) camState = RecordingState.NONE
            }
        }
        val sr = prefs.screenRecordingState
        if (sr == "IN_PROGRESS" || sr == "PAUSED") {
            if (screenState == ScreenRecordingState.NONE) {
                screenState = if (sr == "PAUSED") ScreenRecordingState.PAUSED else ScreenRecordingState.IN_PROGRESS
                if (startElapsed == 0L) startElapsed = now
            }
        } else if (screenState != ScreenRecordingState.NONE && screenState != ScreenRecordingState.STOPPING) {
            screenState = ScreenRecordingState.NONE; startElapsed = 0
        }
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (!hasPerm(Manifest.permission.CAMERA)) {
            camPermission.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)); return
        }
        val ctx = requireContext()
        if (dualMode) {
            ServiceStartPolicy.startRecordingAction(ctx,
                Intent(ctx, DualCameraRecordingService::class.java).setAction(Constants.INTENT_ACTION_START_DUAL_RECORDING))
            return
        }
        camState = RecordingState.STARTING
        startingSince = SystemClock.elapsedRealtime()
        startElapsed = 0
        pausedAccum = 0; pauseStartedAt = 0
        val i = Intent(ctx, RecordingService::class.java).setAction(Constants.INTENT_ACTION_START_RECORDING)
        i.putExtra(Constants.INTENT_EXTRA_INITIAL_TORCH_STATE, torchOn)
        i.putExtra("SURFACE", surface?.takeIf { it.isValid })
        ServiceStartPolicy.startRecordingAction(ctx, i)
    }

    private fun stopCamera() {
        val ctx = requireContext()
        val cls = if (dualActive) DualCameraRecordingService::class.java else RecordingService::class.java
        val action = if (dualActive) Constants.INTENT_ACTION_STOP_DUAL_RECORDING else Constants.INTENT_ACTION_STOP_RECORDING
        ctx.startService(Intent(ctx, cls).setAction(action))
    }

    private fun pauseResumeCamera() {
        val ctx = requireContext()
        val paused = camState == RecordingState.PAUSED
        val cls = if (dualActive) DualCameraRecordingService::class.java else RecordingService::class.java
        val action = when {
            dualActive && paused -> Constants.INTENT_ACTION_RESUME_DUAL_RECORDING
            dualActive -> Constants.INTENT_ACTION_PAUSE_DUAL_RECORDING
            paused -> Constants.INTENT_ACTION_RESUME_RECORDING
            else -> Constants.INTENT_ACTION_PAUSE_RECORDING
        }
        ctx.startService(Intent(ctx, cls).setAction(action))
    }

    private fun capturePhoto() {
        val ctx = requireContext()
        if (camRecording || previewOnly) {
            ctx.startService(Intent(ctx, if (dualActive) DualCameraRecordingService::class.java else RecordingService::class.java)
                .setAction(Constants.INTENT_ACTION_CAPTURE_PHOTO))
        } else {
            Toast.makeText(ctx, R.string.jc_photo_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchCamera() {
        val ctx = requireContext()
        val target = if (prefs.cameraSelection == CameraType.FRONT) CameraType.BACK else CameraType.FRONT
        if (camRecording || previewOnly) {
            ctx.startService(Intent(ctx, RecordingService::class.java)
                .setAction(Constants.INTENT_ACTION_SWITCH_CAMERA)
                .putExtra(Constants.INTENT_EXTRA_CAMERA_TYPE_SWITCH, target.toString()))
        } else {
            prefs.sharedPreferences.edit().putString(Constants.PREF_CAMERA_SELECTION, target.toString()).apply()
        }
        front = target == CameraType.FRONT
    }

    private fun toggleTorch() {
        val ctx = requireContext()
        if (camRecording || previewOnly) {
            ServiceStartPolicy.startRecordingAction(ctx,
                Intent(ctx, if (dualActive) DualCameraRecordingService::class.java else RecordingService::class.java)
                    .setAction(Constants.INTENT_ACTION_TOGGLE_RECORDING_TORCH))
        } else runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
        }
    }

    private fun togglePreviewOnly() {
        if (camRecording) return
        val ctx = requireContext()
        val i = Intent(ctx, RecordingService::class.java)
        if (previewOnly) {
            i.action = Constants.INTENT_ACTION_STOP_PREVIEW_ONLY
        } else {
            i.action = Constants.INTENT_ACTION_START_PREVIEW_ONLY
            surface?.takeIf { it.isValid }?.let {
                i.putExtra("SURFACE", it)
                i.putExtra("SURFACE_WIDTH", texture?.width ?: 0)
                i.putExtra("SURFACE_HEIGHT", texture?.height ?: 0)
            }
        }
        ServiceStartPolicy.startRecordingAction(ctx, i)
    }

    private fun toggleMute() {
        muted = !muted
        prefs.setAudioMuted(muted)
        val ctx = requireContext()
        if (camRecording) {
            ctx.startService(Intent(Constants.INTENT_ACTION_SET_AUDIO_MUTED)
                .setClass(ctx, if (dualActive) DualCameraRecordingService::class.java else RecordingService::class.java)
                .putExtra(Constants.EXTRA_AUDIO_MUTED, muted))
        }
    }

    private fun startScreen() {
        val src = prefs.screenRecordingAudioSource
        if (src == Constants.AUDIO_SOURCE_MIC && !hasPerm(Manifest.permission.RECORD_AUDIO)) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else launchCapture()
    }

    private fun launchCapture() {
        projection.createScreenCaptureIntent()?.let { capturePermission.launch(it) }
    }

    private fun pauseResumeScreen() {
        if (screenState == ScreenRecordingState.PAUSED) projection.resumeScreenRecording() else projection.pauseScreenRecording()
    }

    private fun pushSurface() {
        val s = surface?.takeIf { it.isValid } ?: return
        val ctx = context ?: return
        ctx.startService(Intent(ctx, RecordingService::class.java)
            .setAction(Constants.INTENT_ACTION_CHANGE_SURFACE).putExtra("SURFACE", s))
    }

    private fun openSidebar() {
        HomeSidebarFragment.newInstance().show(parentFragmentManager, "HomeSidebar")
    }

    private fun openSettings() { (activity as? MainActivity)?.switchFragment(4, true) }

    // ---- Compose UI ----
    @Composable
    private fun CameraScreen() {
        var now by mutableLongStateOf(SystemClock.elapsedRealtime())
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                syncFromPrefs(now)
                delay(400)
            }
        }
        val active = if (mode == 0) camRecording else if (mode == 1) screenRecording else false
        val paused = camState == RecordingState.PAUSED && mode == 0 || screenState == ScreenRecordingState.PAUSED && mode == 1
        val elapsed = if (active && startElapsed > 0) {
            val p = if (pauseStartedAt > 0) now - pauseStartedAt else 0
            (now - startElapsed - pausedAccum - p).coerceAtLeast(0)
        } else 0L

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AnimatedContent(targetState = mode, label = "mode") { m ->
                when (m) {
                    0 -> Viewfinder()
                    1 -> ModePlaceholder(R.drawable.screen_recorder, R.string.jc_fadrec_hint)
                    else -> ModePlaceholder(R.drawable.ic_mic, R.string.jc_fadmic_soon)
                }
            }
            // Top bar
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { openSidebar() }) { Icon(Icons.Filled.Menu, null, tint = Color.White) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (active) {
                        Surface(shape = RoundedCornerShape(50), color = Color(0x99000000)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(if (paused) Color(0xFFFFB300) else Color(0xFFFF3B30)))
                                Spacer(Modifier.width(8.dp))
                                Text(format(elapsed), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                IconButton(onClick = { openSettings() }) { Icon(Icons.Filled.Settings, null, tint = Color.White) }
            }
            // Bottom controls
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModeTabs(enabled = !active)
                Spacer(Modifier.size(20.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        when {
                            mode == 0 -> RoundButton(if (torchOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off, torchOn) { toggleTorch() }
                            mode == 1 && screenRecording -> {}
                        }
                    }
                    if (active) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundButton(if (paused) R.drawable.play_button_rounded else R.drawable.pause_rounded, false) {
                                if (mode == 0) pauseResumeCamera() else pauseResumeScreen()
                            }
                            Spacer(Modifier.width(16.dp))
                            Shutter(recording = true) { primaryAction() }
                            Spacer(Modifier.width(16.dp))
                            if (mode == 0) RoundButton(R.drawable.ic_photo, false) { capturePhoto() } else Spacer(Modifier.size(56.dp))
                        }
                    } else if (mode != 2) {
                        Shutter(recording = false) { primaryAction() }
                    } else Spacer(Modifier.size(76.dp))
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        if (mode == 0) RoundButton(R.drawable.cam_switch, false) { switchCamera() }
                    }
                }
                if (mode == 0 && !camRecording) {
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip(stringResource(R.string.jc_preview), previewOnly) { togglePreviewOnly() }
                        Chip(stringResource(R.string.jc_dual), dualMode) {
                            dualMode = !dualMode; prefs.setDualCameraModeEnabled(dualMode)
                        }
                        Chip(stringResource(R.string.jc_mute), muted) { toggleMute() }
                    }
                }
            }
        }
    }

    @Composable
    private fun Viewfinder() {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 60.dp, bottom = 190.dp, start = 12.dp, end = 12.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF101010))) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        texture = tv
                        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                surface = Surface(st); pushSurface()
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                surface = null
                                context?.let { c -> if (camRecording || previewOnly) c.startService(Intent(c, RecordingService::class.java).setAction(Constants.INTENT_ACTION_CHANGE_SURFACE)) }
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
            )
            if (!camRecording && !previewOnly) {
                Text(stringResource(R.string.jc_tap_preview), color = Color(0x99FFFFFF), modifier = Modifier.align(Alignment.Center).clickable { togglePreviewOnly() }.padding(16.dp))
            }
        }
    }

    @Composable
    private fun ModePlaceholder(icon: Int, text: Int) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), null, tint = Color(0x66FFFFFF), modifier = Modifier.size(72.dp))
            Spacer(Modifier.size(16.dp))
            Text(stringResource(text), color = Color(0xB3FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 40.dp))
        }
    }

    @Composable
    private fun ModeTabs(enabled: Boolean) {
        val names = listOf("FadCam", "FadRec", "FadMic")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, n ->
                val sel = i == mode
                Text(
                    n,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else Color.White,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(enabled = enabled) { selectMode(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    @Composable
    private fun Shutter(recording: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.size(76.dp).border(BorderStroke(4.dp, Color.White), CircleShape).padding(7.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize().clip(if (recording) RoundedCornerShape(14.dp) else CircleShape)
                    .background(if (recording) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary)
            )
        }
    }

    @Composable
    private fun RoundButton(icon: Int, selected: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color(0x66000000))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White, modifier = Modifier.size(24.dp))
        }
    }

    @Composable
    private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color(0x66000000))
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }

    private fun format(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }
}
