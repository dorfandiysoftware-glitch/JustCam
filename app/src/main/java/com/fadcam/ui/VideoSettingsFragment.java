package com.fadcam.ui;

import com.fadcam.Log;
import com.fadcam.FLog;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.Range;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.fadcam.CameraType;
import com.fadcam.Constants;
import com.fadcam.MainActivity;
import com.fadcam.MaximumRecordingDuration;
import com.fadcam.R;
import com.fadcam.SharedPreferencesManager;
import com.fadcam.VideoCodec;
import com.fadcam.dualcam.DualCameraCapability;
import com.fadcam.dualcam.ui.DualCameraSettingsFragment;
import com.fadcam.services.RecordingService;
import com.fadcam.ui.OverlayNavUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Collections;
import android.hardware.camera2.params.StreamConfigurationMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.fadcam.Utils;
import com.fadcam.utils.CameraXFrameRateUtil;
import com.fadcam.utils.DeviceHelper;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import java.util.Collections;
import java.util.Arrays;

/**
 * VideoSettingsFragment
 * Row based extraction of video recording related settings from monolithic
 * SettingsFragment.
 * Keeps preference keys and semantics identical; only UI container changes.
 */
public class VideoSettingsFragment extends Fragment {

    private static final String TAG = "VideoSettingsFragment";

    private SharedPreferencesManager prefs;

    private boolean videoStabilizationSupported = true;

    // Cached dynamic data
    private List<Size> cachedResolutionsFront = new ArrayList<>();
    private List<Size> cachedResolutionsBack = new ArrayList<>();
    private List<Integer> cachedFpsFront = new ArrayList<>();
    private List<Integer> cachedFpsBack = new ArrayList<>();
    private List<VideoCodec> cachedCodecs = new ArrayList<>();
    private List<CameraIdInfo> availableBackCameras = new ArrayList<>();

    // Bitrate preference keys (mirroring legacy helper logic)
    private static final String PREF_BITRATE_MODE_CUSTOM = "bitrate_mode_custom"; // false default
    private static final String PREF_BITRATE_CUSTOM_VALUE = "bitrate_custom_value"; // stored in kbps

    /** One selectable value for the Compose screen. */
    public static final class Option {
        public final String id;
        public final String label;
        public final boolean both;
        Option(String id, String label, boolean both) { this.id = id; this.label = label; this.both = both; }
    }

    private Runnable onDataChanged = () -> {};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        prefs = SharedPreferencesManager.getInstance(requireContext());
        videoStabilizationSupported = isVideoStabilizationHardwareSupported();
        detectAvailableBackCameras();
        cachedCodecs = getCompatiblesVideoCodecs();
        androidx.compose.ui.platform.ComposeView v = new androidx.compose.ui.platform.ComposeView(requireContext());
        v.setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        com.fadcam.ui.compose.VideoScreenKt.setVideoContent(v, this, prefs,
                r -> { onDataChanged = r; return kotlin.Unit.INSTANCE; },
                () -> { OverlayNavUtil.dismiss(requireActivity()); return kotlin.Unit.INSTANCE; },
                () -> { OverlayNavUtil.show(requireActivity(), new DualCameraSettingsFragment(),
                        "DualCameraSettingsFragment"); return kotlin.Unit.INSTANCE; });
        return v;
    }

    // ---------------- API used by the Compose screen ----------------

    public boolean isStabilizationSupported() { return videoStabilizationSupported; }

    public boolean isDualSupported() { return new DualCameraCapability(requireContext()).isSupported(); }

    public List<Option> lensOptions() {
        if (availableBackCameras.isEmpty()) detectAvailableBackCameras();
        List<Option> out = new ArrayList<>();
        for (CameraIdInfo i : availableBackCameras) out.add(new Option(i.id, i.displayName, false));
        return out;
    }

    public List<Option> resolutionOptions() {
        List<Option> out = new ArrayList<>();
        try {
            for (ResolutionInfo ri : getCompatiblesVideoResolutionsWithFlags(prefs.getCameraSelection())) {
                out.add(new Option(ri.size.getWidth() + "x" + ri.size.getHeight(),
                        buildResolutionLabel(ri.size), ri.supportedByBothCameras));
            }
        } catch (Exception e) {
            FLog.w(TAG, "resolutionOptions failed", e);
        }
        return out;
    }

    public String resolutionLabel(Size size) { return buildResolutionLabel(size); }

    public List<Integer> frameRates() {
        try { return getHardwareSupportedFrameRates(prefs.getCameraSelection()); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    public List<String> codecs() {
        List<String> out = new ArrayList<>();
        for (VideoCodec c : getCompatiblesVideoCodecs()) out.add(c.toString());
        return out;
    }

    public List<Float> zoomOptions() { return buildZoomRatioOptions(prefs.getCameraSelection()); }

    public boolean bitrateCustom() { return getBitrateMode(); }
    public int bitrateKbps() { return getCurrentBitrate(); }
    public int defaultBitrateKbps() { return getDefaultBitrate(); }

    public void setBitrate(boolean custom, int kbps) {
        setBitrateMode(custom);
        if (custom) setBitrateCustomValue(kbps);
        else setBitrateCustomValue(getDefaultBitrate());
    }

    public void onCameraSelected(String sel) {
        CameraType t = CameraType.valueOf(sel);
        prefs.setDualCameraModeEnabled(t == CameraType.DUAL_PIP);
        prefs.sharedPreferences.edit().putString(Constants.PREF_CAMERA_SELECTION, sel).apply();
        cachedResolutionsFront.clear();
        cachedResolutionsBack.clear();
        videoStabilizationSupported = isVideoStabilizationHardwareSupported();
    }

    public void onLensSelected(String id) {
        prefs.setSelectedBackCameraId(id);
        cachedResolutionsBack.clear();
        videoStabilizationSupported = isVideoStabilizationHardwareSupported();
    }

    public void setStabilization(boolean state) {
        prefs.setVideoStabilizationEnabled(state);
        try {
            Intent i = new Intent(Constants.INTENT_ACTION_SET_VIDEO_STABILIZATION);
            i.setClass(requireContext(), RecordingService.class);
            i.putExtra(Constants.EXTRA_VIDEO_STABILIZATION_ENABLED, state);
            requireContext().startService(i);
        } catch (Exception e) {
            FLog.w("FadCamEIS", "UI: failed to dispatch stabilization change: " + e.getMessage());
        }
    }

    /** Turns location embedding on/off; asks for the permission first when enabling. */
    public void setLocationEmbedding(boolean enabled) {
        if (enabled) {
            ensureLocationPermissionThen(() -> {
                prefs.sharedPreferences.edit().putBoolean(Constants.PREF_EMBED_LOCATION_DATA, true).apply();
                onDataChanged.run();
            });
        } else {
            prefs.sharedPreferences.edit().putBoolean(Constants.PREF_EMBED_LOCATION_DATA, false).apply();
        }
    }

    /**
     * Opens the Video Stabilization bottom sheet: an AvatarToggleView switch plus
     * the explanation footer. On devices without EIS/OIS the switch is shown but
     * disabled with a clear "not supported" note — we never hide capability info.
     */


    /**
     * Checks whether the currently selected camera advertises EIS (video
     * stabilization) and/or OIS. Fully guarded — any failure (permissions,
     * camera busy, no characteristics) is treated as "not supported" rather
     * than crashing, and is logged for diagnosis.
     */
    private boolean isVideoStabilizationHardwareSupported() {
        try {
            CameraManager cm = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return false;

            CameraType cam = prefs.getCameraSelection();
            String cameraId = null;
            if (cam == CameraType.BACK) {
                cameraId = prefs.getSelectedBackCameraId();
            } else {
                for (String id : cm.getCameraIdList()) {
                    CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                    Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        cameraId = id;
                        break;
                    }
                }
            }
            if (cameraId == null) return false;

            CameraCharacteristics chars = cm.getCameraCharacteristics(cameraId);

            boolean eis = false;
            int[] stabModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
            if (stabModes != null) {
                for (int m : stabModes) {
                    if (m == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                        eis = true;
                        break;
                    }
                }
            }

            boolean ois = false;
            int[] oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
            if (oisModes != null) {
                for (int m : oisModes) {
                    if (m == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) {
                        ois = true;
                        break;
                    }
                }
            }
            FLog.i("FadCamEIS", "UI: camera=" + cameraId + " eis=" + eis + " ois=" + ois);
            return eis || ois;
        } catch (Exception e) {
            FLog.w("FadCamEIS", "UI: stabilization capability check failed, treated as unsupported: "
                    + e.getMessage());
            return false;
        }
    }





    private Runnable pendingLocationGrantedAction;

    private final androidx.activity.result.ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> {
                        Runnable action = pendingLocationGrantedAction;
                        pendingLocationGrantedAction = null;
                        if (granted && action != null) action.run();
                    });



    private void ensureLocationPermissionThen(Runnable onGranted) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onGranted.run();
        } else {
            pendingLocationGrantedAction = onGranted;
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }
    // -------------- Additional methods for location embedding end -----------















    // Removed legacy orientation dialog + feature flag wrapper; always show bottom
    // sheet picker



    // -------- Legacy logic helpers (migrated/adapted) --------

    private void detectAvailableBackCameras() {
        // method(detectAvailableBackCameras)-----------
        availableBackCameras.clear();
        Context ctx = getContext();
        if (ctx == null)
            return;
        CameraManager manager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        try {
            String defaultId = Constants.DEFAULT_BACK_CAMERA_ID != null ? Constants.DEFAULT_BACK_CAMERA_ID : "0";

            // Track camera types to avoid duplicates
            java.util.Set<String> addedCameraTypes = new java.util.HashSet<>();

            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics ch = manager.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    float[] focalLengths = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    Float focal = null;
                    if (focalLengths != null && focalLengths.length > 0)
                        focal = focalLengths[0];

                    StringBuilder display = new StringBuilder();
                    String cameraType;
                    boolean isDefault = id.equals(defaultId);

                    // Determine camera type based on focal length, but be conservative
                    // Only classify as wide-angle if there are multiple cameras with different
                    // focal lengths
                    String lensType = null;
                    if (focal != null && availableBackCameras.size() > 0) {
                        // Only classify as special lens types if we have multiple cameras
                        // This prevents single-camera phones from being mislabeled
                        boolean hasMultipleCameras = false;
                        try {
                            // Quick check if there are multiple back cameras
                            int backCameraCount = 0;
                            for (String checkId : manager.getCameraIdList()) {
                                CameraCharacteristics checkCh = manager.getCameraCharacteristics(checkId);
                                Integer checkFacing = checkCh.get(CameraCharacteristics.LENS_FACING);
                                if (checkFacing != null && checkFacing == CameraCharacteristics.LENS_FACING_BACK) {
                                    backCameraCount++;
                                }
                            }
                            hasMultipleCameras = backCameraCount > 1;
                        } catch (Exception e) {
                            // If we can't determine, assume single camera
                            hasMultipleCameras = false;
                        }

                        if (hasMultipleCameras) {
                            // Only classify lens types if there are multiple cameras
                            if (focal <= 3f) {
                                lensType = "Ultra-Wide";
                            } else if (focal <= 6f) {
                                lensType = "Wide-Angle";
                            } else if (focal >= 70f) {
                                lensType = "Telephoto";
                            } else if (focal >= 50f) {
                                lensType = "Portrait";
                            }
                            // For focal lengths between 6-50mm, don't classify (likely standard)
                        }
                        // For single camera phones, don't classify by focal length
                    }

                    if (isDefault) {
                        // For default camera, show both "Main" and lens type if it's not standard
                        if (lensType != null && !lensType.equals("Standard")) {
                            display.append("Main (").append(lensType).append(")");
                            cameraType = "Main_" + lensType;
                        } else {
                            display.append("Main");
                            cameraType = "Main";
                        }
                    } else if (lensType != null) {
                        display.append(lensType);
                        cameraType = lensType;
                    } else {
                        display.append("Camera");
                        cameraType = "Camera_" + id;
                    }

                    // Skip duplicate camera types (except Main camera which is always included)
                    if (!isDefault && addedCameraTypes.contains(cameraType)) {
                        FLog.d(TAG, "Skipping duplicate camera type: " + cameraType + " (ID: " + id + ")");
                        continue;
                    }

                    addedCameraTypes.add(cameraType);
                    display.append(" (").append(id).append(")");
                    if (focal != null)
                        display.append(" ").append(Math.round(focal)).append("mm");
                    availableBackCameras.add(new CameraIdInfo(id, display.toString()));

                    FLog.d(TAG, "Added camera: " + display.toString() + " with focal length: " + focal);
                }
            }
            // Sort: default first then numeric id
            java.util.Collections.sort(availableBackCameras, (a, b) -> {
                if (a.id.equals(defaultId))
                    return -1;
                if (b.id.equals(defaultId))
                    return 1;
                try {
                    return Integer.parseInt(a.id) - Integer.parseInt(b.id);
                } catch (Exception e) {
                    return a.id.compareTo(b.id);
                }
            });
            if (availableBackCameras.isEmpty()) {
                availableBackCameras.add(new CameraIdInfo(defaultId, "Default Camera (" + defaultId + ")"));
            }
            String saved = prefs.getSelectedBackCameraId();
            boolean found = false;
            for (CameraIdInfo info : availableBackCameras) {
                if (info.id.equals(saved)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                prefs.setSelectedBackCameraId(availableBackCameras.get(0).id);
            }
        } catch (Exception e) {
            FLog.e(TAG, "Error detecting back cameras", e);
        }
        // method(detectAvailableBackCameras)-----------
    }

    private List<Size> getCompatiblesVideoResolutions(CameraType type) {
        // method(getCompatiblesVideoResolutions)-----------
        // DUAL_PIP inherits BACK camera resolutions
        if (type != null && type.isDual()) {
            type = CameraType.BACK;
        }

        // Build the resolution list from the INTERSECTION of BOTH cameras'
        // SurfaceTexture output sizes.  FadCam uses SurfaceTexture for the
        // GL pipeline, not MediaRecorder, so we query SurfaceTexture.class.
        // Intersection guarantees that every listed resolution works on both
        // cameras, making live switching during recording safe.
        //
        // Cache the result once — it's the same for both cameras.
        if (!cachedResolutionsBack.isEmpty())
            return cachedResolutionsBack;

        final String[] CANONICAL = {
                "7680x4320", // 8K
                "3840x2160", // 4K
                "2560x1440", // 2K
                "1920x1080", // FHD
                "1280x720", // HD
                "854x480",
                "720x480",
                "640x480",
                "480x360",
                "426x240",
                "352x288",
                "320x240"
        };
        Set<String> canonicalSet = new HashSet<>(java.util.Arrays.asList(CANONICAL));

        // ── 1. Gather SurfaceTexture output sizes for both cameras ──
        Set<String> backSizes = new HashSet<>();
        Set<String> frontSizes = new HashSet<>();
        try {
            for (CameraType ct : new CameraType[]{CameraType.BACK, CameraType.FRONT}) {
                String camId = getActualCameraIdForType(ct);
                if (camId == null) continue;
                CameraManager mgr = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics chars = mgr.getCameraCharacteristics(camId);
                StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) continue;
                Size[] sizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);
                if (sizes == null) continue;
                for (Size s : sizes) {
                    if (isReasonableVideoSize(s)) {
                        String key = s.getWidth() + "x" + s.getHeight();
                        (ct == CameraType.BACK ? backSizes : frontSizes).add(key);
                    }
                }
            }
        } catch (Exception e) {
            FLog.w(TAG, "Resolution intersection: error reading camera sizes", e);
        }

        // ── 2. Intersection = sizes both cameras can output via SurfaceTexture ──
        Set<String> commonSizes = new HashSet<>(backSizes);
        commonSizes.retainAll(frontSizes);

        // ── 3. Canonical sizes that appear in the intersection ──
        List<Size> curated = new ArrayList<>();
        for (String dim : CANONICAL) {
            if (!commonSizes.contains(dim)) continue;
            try {
                String[] parts = dim.split("x");
                curated.add(new Size(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                FLog.d(TAG, "Resolution: " + dim + " — both cameras (SurfaceTexture)");
            } catch (Exception ignored) {
            }
        }

        // ── 4. Non-canonical sizes (>=1080p) that appear in the intersection ──
        for (String key : commonSizes) {
            if (canonicalSet.contains(key)) continue;
            try {
                String[] parts = key.split("x");
                int w = Integer.parseInt(parts[0]);
                int h = Integer.parseInt(parts[1]);
                if (w >= 1920 && h >= 1080) {
                    curated.add(new Size(w, h));
                    FLog.d(TAG, "Resolution: " + key + " — both cameras (non-canonical)");
                }
            } catch (Exception ignored) {
            }
        }

        if (!curated.isEmpty()) {
            Collections.sort(curated, (a, b) -> Long.compare(
                    (long) b.getWidth() * b.getHeight(), (long) a.getWidth() * a.getHeight()));

            // Auto-correct saved preference if it became invalid
            try {
                Size saved = prefs.getCameraResolution();
                boolean found = curated.stream().anyMatch(
                        s -> s.getWidth() == saved.getWidth() && s.getHeight() == saved.getHeight());
                if (!found) {
                    Size fallback = curated.get(0);
                    prefs.sharedPreferences.edit()
                            .putInt(Constants.PREF_VIDEO_RESOLUTION_WIDTH, fallback.getWidth())
                            .putInt(Constants.PREF_VIDEO_RESOLUTION_HEIGHT, fallback.getHeight())
                            .apply();
                }
            } catch (Exception ignored) {
            }

            // Same list for both cameras
            cachedResolutionsBack = curated;
            cachedResolutionsFront = curated;
            return curated;
        }

        // Fallback to legacy profile-based approach
        List<CamcorderProfile> profiles = getCamcorderProfilesForTypeInternal(type);
        List<Size> sizes = new ArrayList<>();
        if (profiles != null) {
            for (CamcorderProfile p : profiles) {
                if (p != null) sizes.add(new Size(p.videoFrameWidth, p.videoFrameHeight));
            }
        }
        Set<Size> uniq = new HashSet<>(sizes);
        List<Size> list = new ArrayList<>(uniq);
        Collections.sort(list, (a, b) -> Long.compare(
                (long) b.getWidth() * b.getHeight(), (long) a.getWidth() * a.getHeight()));
        cachedResolutionsBack = list;
        cachedResolutionsFront = list;
        return list;
        // method(getCompatiblesVideoResolutions)-----------
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ResolutionInfo — pairs a Size with a flag indicating whether both cameras
    // support it (safe for live camera switching during recording).
    // ────────────────────────────────────────────────────────────────────────────
    private static class ResolutionInfo {
        final Size size;
        final boolean supportedByBothCameras;

        ResolutionInfo(Size size, boolean supportedByBothCameras) {
            this.size = size;
            this.supportedByBothCameras = supportedByBothCameras;
        }
    }

    /**
     * Returns ALL supported resolutions for the current camera, each tagged with
     * whether BOTH cameras support it (safe for live switching) or the current
     * camera only.
     */
    private List<ResolutionInfo> getCompatiblesVideoResolutionsWithFlags(CameraType type) {
        // method(getCompatiblesVideoResolutionsWithFlags)-----------
        if (type != null && type.isDual()) {
            type = CameraType.BACK;
        }

        final String[] CANONICAL = {
                "7680x4320", // 8K
                "3840x2160", // 4K
                "2560x1440", // 2K
                "1920x1080", // FHD
                "1280x720",  // HD
                "854x480",
                "720x480",
                "640x480",
                "480x360",
                "426x240",
                "352x288",
                "320x240"
        };
        Set<String> canonicalSet = new HashSet<>(java.util.Arrays.asList(CANONICAL));

        // ── 1. Gather SurfaceTexture output sizes for both cameras ──
        Set<String> backSizes = new HashSet<>();
        Set<String> frontSizes = new HashSet<>();
        try {
            for (CameraType ct : new CameraType[]{CameraType.BACK, CameraType.FRONT}) {
                String camId = getActualCameraIdForType(ct);
                if (camId == null) continue;
                CameraManager mgr = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics chars = mgr.getCameraCharacteristics(camId);
                StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) continue;
                Size[] sizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);
                if (sizes == null) continue;
                for (Size s : sizes) {
                    if (isReasonableVideoSize(s)) {
                        String key = s.getWidth() + "x" + s.getHeight();
                        (ct == CameraType.BACK ? backSizes : frontSizes).add(key);
                    }
                }
            }
        } catch (Exception e) {
            FLog.w(TAG, "Resolution flags: error reading camera sizes", e);
        }

        // ── 2. Intersection = sizes both cameras support ──
        Set<String> commonSizes = new HashSet<>(backSizes);
        commonSizes.retainAll(frontSizes);

        // ── 3. Current camera's sizes ──
        Set<String> currentSizes = (type == CameraType.FRONT) ? frontSizes : backSizes;

        // ── 4. Build result: canonical + non-canonical high-res ──
        List<ResolutionInfo> result = new ArrayList<>();
        Set<String> added = new HashSet<>();

        // Canonical sizes that the current camera supports
        for (String dim : CANONICAL) {
            if (!currentSizes.contains(dim)) continue;
            try {
                String[] parts = dim.split("x");
                Size s = new Size(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                result.add(new ResolutionInfo(s, commonSizes.contains(dim)));
                added.add(dim);
            } catch (Exception ignored) {
            }
        }

        // Non-canonical >=1080p sizes that the current camera supports
        for (String key : currentSizes) {
            if (canonicalSet.contains(key) || added.contains(key)) continue;
            try {
                String[] parts = key.split("x");
                int w = Integer.parseInt(parts[0]);
                int h = Integer.parseInt(parts[1]);
                if (w >= 1920 && h >= 1080) {
                    result.add(new ResolutionInfo(new Size(w, h), commonSizes.contains(key)));
                    added.add(key);
                }
            } catch (Exception ignored) {
            }
        }

        // ── 5. Sort: by area desc, common-first tiebreaker ──
        if (!result.isEmpty()) {
            Collections.sort(result, (a, b) -> {
                long areaA = (long) a.size.getWidth() * a.size.getHeight();
                long areaB = (long) b.size.getWidth() * b.size.getHeight();
                if (areaA != areaB) return Long.compare(areaB, areaA);
                if (a.supportedByBothCameras != b.supportedByBothCameras)
                    return a.supportedByBothCameras ? -1 : 1;
                return 0;
            });

            // Auto-correct saved preference if it's no longer valid
            try {
                Size saved = prefs.getCameraResolution();
                boolean found = result.stream().anyMatch(
                        ri -> ri.size.getWidth() == saved.getWidth() && ri.size.getHeight() == saved.getHeight());
                if (!found) {
                    ResolutionInfo fallback = result.get(0);
                    prefs.sharedPreferences.edit()
                            .putInt(Constants.PREF_VIDEO_RESOLUTION_WIDTH, fallback.size.getWidth())
                            .putInt(Constants.PREF_VIDEO_RESOLUTION_HEIGHT, fallback.size.getHeight())
                            .apply();
                }
            } catch (Exception ignored) {
            }
        }

        // Update the legacy flat caches for backward compat
        List<Size> flatResolutions = new ArrayList<>();
        for (ResolutionInfo ri : result) flatResolutions.add(ri.size);
        cachedResolutionsBack = flatResolutions;
        cachedResolutionsFront = flatResolutions;

        return result;
        // method(getCompatiblesVideoResolutionsWithFlags)-----------
    }

    // Legacy internal helpers (copied/adapted from SettingsFragment to ensure
    // parity)
    private List<CamcorderProfile> getCamcorderProfilesForTypeInternal(CameraType cameraType) {
        Context ctx = getContext();
        if (ctx == null)
            return new ArrayList<>();
        String actualCameraId = getActualCameraIdForType(cameraType);
        if (actualCameraId == null)
            return new ArrayList<>();
        List<Size> supportedVideoSizes = getSupportedVideoSizesForCamera(actualCameraId);
        if (supportedVideoSizes.isEmpty())
            return getCamcorderProfilesFallback(cameraType);
        List<CamcorderProfile> profiles = new ArrayList<>();
        for (Size s : supportedVideoSizes) {
            CamcorderProfile p = createProfileForSize(actualCameraId, s);
            if (p != null)
                profiles.add(p);
        }
        profiles.removeIf(Objects::isNull);
        return profiles;
    }

    private List<Size> getSupportedVideoSizesForCamera(String cameraId) {
        List<Size> supportedSizes = new ArrayList<>();
        try {
            Context ctx = getContext();
            if (ctx == null)
                return supportedSizes;
            CameraManager cameraManager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configMap = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (configMap != null) {
                // Query both MediaRecorder (for reference) and SurfaceTexture (what we actually use).
                // Some cameras (e.g. Pixel 7 Pro front) list 4K only for certain output classes.
                Size[] videoSizes = configMap.getOutputSizes(MediaRecorder.class);
                Size[] surfaceSizes = configMap.getOutputSizes(android.graphics.SurfaceTexture.class);
                List<Size> merged = new ArrayList<>();
                if (videoSizes != null) Collections.addAll(merged, videoSizes);
                if (surfaceSizes != null) {
                    for (Size s : surfaceSizes) {
                        boolean exists = merged.stream().anyMatch(
                                m -> m.getWidth() == s.getWidth() && m.getHeight() == s.getHeight());
                        if (!exists) merged.add(s);
                    }
                }
                merged.sort((s1, s2) -> Long.compare((long) s2.getWidth() * s2.getHeight(),
                        (long) s1.getWidth() * s1.getHeight()));
                
                FLog.d(TAG, "Video Sizes: Camera " + cameraId + " supports " + merged.size() + " video resolutions:");
                for (Size size : merged) {
                    FLog.d(TAG, "Video Sizes: " + size.getWidth() + "x" + size.getHeight() + 
                          " (reasonable: " + isReasonableVideoSize(size) + ")");
                }
                
                for (Size size : merged) {
                    if (isReasonableVideoSize(size))
                        supportedSizes.add(size);
                }
            }
            // Also query the maximum-resolution stream config map (Android 12+).
            // Some devices (e.g. Pixel 7 Pro front camera) only list 4K here.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                StreamConfigurationMap maxResMap = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
                if (maxResMap != null) {
                    Size[] maxResSizes = maxResMap.getOutputSizes(MediaRecorder.class);
                    if (maxResSizes != null) {
                        for (Size size : maxResSizes) {
                            if (isReasonableVideoSize(size)) {
                                String key = size.getWidth() + "x" + size.getHeight();
                                boolean exists = supportedSizes.stream()
                                        .anyMatch(s -> s.getWidth() == size.getWidth() && s.getHeight() == size.getHeight());
                                if (!exists) {
                                    supportedSizes.add(size);
                                    FLog.d(TAG, "Video Sizes (max-res): " + key + " - added from maximum resolution map");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            FLog.e(TAG, "Error accessing camera " + cameraId + " for video sizes", e);
        }
        return supportedSizes;
    }

    // threshold)-----------
    private boolean isReasonableVideoSize(Size size) {
        int w = size.getWidth();
        int h = size.getHeight();
        // Allow lower legacy SD / sub-SD resolutions (e.g., 320x240, 352x288, 426x240)
        // requested by user.
        // Previous threshold filtered anything below 480x360 and removed "super low"
        // options.
        if (w < 320 || h < 240)
            return false; // new minimal floor
        double ar = (double) w / h;
        return ar >= 1.0 && ar <= 2.5;
    }
    // threshold)-----------

    private CamcorderProfile createProfileForSize(String cameraId, Size size) {
        try {
            int[] qualities = { CamcorderProfile.QUALITY_2160P, CamcorderProfile.QUALITY_1080P,
                    CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_HIGH,
                    CamcorderProfile.QUALITY_LOW };
            // Add 8K support without unnecessary API checks - let hasProfile() handle availability
            int[] ext = { CamcorderProfile.QUALITY_8KUHD, CamcorderProfile.QUALITY_2160P,
                    CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_480P,
                    CamcorderProfile.QUALITY_HIGH, CamcorderProfile.QUALITY_LOW };
            qualities = ext;
            int camIdInt = Integer.parseInt(cameraId);
            
            // Simplified diagnostic logging for 8K detection
            if (size.getWidth() == 7680 && size.getHeight() == 4320) {
                FLog.d(TAG, "8K Detection: Checking 8K (7680x4320) for camera " + cameraId);
                boolean has8K = CamcorderProfile.hasProfile(camIdInt, CamcorderProfile.QUALITY_8KUHD);
                FLog.d(TAG, "8K Detection: CamcorderProfile.hasProfile(QUALITY_8KUHD) = " + has8K);
                if (has8K) {
                    CamcorderProfile profile8K = getCamcorderProfile(camIdInt, CamcorderProfile.QUALITY_8KUHD);
                    if (profile8K != null) {
                        FLog.d(TAG, "8K Detection: 8K Profile dimensions = " + profile8K.videoFrameWidth + "x" + profile8K.videoFrameHeight);
                    } else {
                        FLog.w(TAG, "8K Detection: 8K Profile is null despite hasProfile=true");
                    }
                }
            }
            
            for (int q : qualities) {
                if (CamcorderProfile.hasProfile(camIdInt, q)) {
                    CamcorderProfile p = getCamcorderProfile(camIdInt, q);
                    if (p != null && p.videoFrameWidth == size.getWidth() && p.videoFrameHeight == size.getHeight())
                        return p;
                }
            }
            
            // Only allow specific fallbacks for exact resolution matches to prevent phantom options
            // 8K: Only if we have actual 8K profile
            if (size.getWidth() == 7680 && size.getHeight() == 4320) {
                if (CamcorderProfile.hasProfile(camIdInt, CamcorderProfile.QUALITY_8KUHD)) {
                    CamcorderProfile p8k = getCamcorderProfile(camIdInt, CamcorderProfile.QUALITY_8KUHD);
                    if (p8k != null) {
                        FLog.d(TAG, "8K Fallback: Found 8K profile with dimensions " + p8k.videoFrameWidth + "x" + p8k.videoFrameHeight);
                        return p8k;
                    }
                }
            }
            
            // 4K: Only if we have actual 4K profile  
            if (size.getWidth() == 3840 && size.getHeight() == 2160) {
                if (CamcorderProfile.hasProfile(camIdInt, CamcorderProfile.QUALITY_2160P)) {
                    CamcorderProfile p4k = getCamcorderProfile(camIdInt, CamcorderProfile.QUALITY_2160P);
                    if (p4k != null) {
                        FLog.d(TAG, "4K Fallback: Found 4K profile with dimensions " + p4k.videoFrameWidth + "x" + p4k.videoFrameHeight);
                        return p4k;
                    }
                }
            }
        } catch (Exception e) {
            FLog.e(TAG, "createProfileForSize error", e);
        }
        return null;
    }

    private List<CamcorderProfile> getCamcorderProfilesFallback(CameraType cameraType) {
        List<CamcorderProfile> profiles = new ArrayList<>();
        // DUAL_PIP has cameraId=2 which is not a real hardware camera — use BACK camera profiles
        int cameraId = cameraType.isDual() ? CameraType.BACK.getCameraId() : cameraType.getCameraId();
        // Simplified: treat 8K like 4K - just check if profile exists, no API level checks
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_8KUHD))
            profiles.add(getCamcorderProfile(cameraId, CamcorderProfile.QUALITY_8KUHD));
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P))
            profiles.add(getCamcorderProfile(cameraId, CamcorderProfile.QUALITY_2160P));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2K))
            profiles.add(getCamcorderProfile(cameraId, CamcorderProfile.QUALITY_2K));
        int[] qualities = { CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_720P,
                CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_CIF, CamcorderProfile.QUALITY_QCIF,
                CamcorderProfile.QUALITY_LOW };
        Set<Integer> added = new HashSet<>();
        for (CamcorderProfile p : profiles) {
            if (p != null)
                added.add(p.quality);
        }
        for (int q : qualities) {
            if (!added.contains(q) && CamcorderProfile.hasProfile(cameraId, q)) {
                CamcorderProfile p = getCamcorderProfile(cameraId, q);
                if (p != null) {
                    profiles.add(p);
                    added.add(q);
                }
            }
        }
        if (!added.contains(CamcorderProfile.QUALITY_HIGH)
                && CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH))
            profiles.add(getCamcorderProfile(cameraId, CamcorderProfile.QUALITY_HIGH));
        profiles.removeIf(Objects::isNull);
        return profiles;
    }

    private String buildResolutionLabel(Size size) {
        String[] keys = getResources().getStringArray(R.array.video_resolutions_keys);
        String[] values = getResources().getStringArray(R.array.video_resolutions_values);
        String resKey = size.getWidth() + "x" + size.getHeight();
        String label = null;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(resKey)) {
                label = values[i];
                break;
            }
        }
        if (label == null)
            return resKey; // fallback
        return label + " (" + resKey + ")";
    }

    private String getActualCameraIdForType(CameraType type) {
        // DUAL_PIP doesn't map to a single camera — fall back to BACK for capability queries
        if (type != null && type.isDual()) {
            type = CameraType.BACK;
        }
        Context ctx = getContext();
        if (ctx == null)
            return null;
        CameraManager manager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics ch = manager.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (type == CameraType.FRONT && facing == CameraCharacteristics.LENS_FACING_FRONT)
                        return id;
                    if (type == CameraType.BACK && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            FLog.e(TAG, "Error resolving camera id", e);
        }
        return null;
    }



    // Compatible frame rate list - fixed to not be limited by CamcorderProfile default FPS
    private List<Integer> getCompatiblesVideoFrameRates(CameraType cameraType) {
        // The real issue: Don't limit by CamcorderProfile.videoFrameRate which might be just the default (30fps)
        // Instead, provide all available options and let the camera/encoder handle what's actually supported
        int[] options = getResources().getIntArray(R.array.video_framerate_options);
        List<Integer> list = new ArrayList<>();
        
        // Add all frame rate options from the array (includes 60fps, 90fps, 120fps)
        for (int fps : options) {
            list.add(fps);
        }
        
        // If no options defined in array, provide standard options including high frame rates
        if (list.isEmpty()) {
            list.add(24);
            list.add(30);
            list.add(60);
            list.add(90);
            list.add(120);
        }
        
        // Ensure default frame rate is included
        if (!list.contains(Constants.DEFAULT_VIDEO_FRAME_RATE)) {
            list.add(Constants.DEFAULT_VIDEO_FRAME_RATE);
        }
        
        // Sort the list
        java.util.Collections.sort(list);
        
        return list;
    }

    private List<VideoCodec> getCompatiblesVideoCodecs() {
        if (!cachedCodecs.isEmpty())
            return cachedCodecs;
        List<VideoCodec> codecs = new ArrayList<>();
        for (VideoCodec c : VideoCodec.values()) {
            if (Utils.isCodecSupported(c.getMimeType()))
                codecs.add(c);
        }
        if (codecs.isEmpty())
            codecs.add(Constants.DEFAULT_VIDEO_CODEC);
        cachedCodecs = codecs;
        return codecs;
    }

    // Bitrate helpers (mirroring legacy semantics)
    private boolean getBitrateMode() {
        return prefs.sharedPreferences.getBoolean(PREF_BITRATE_MODE_CUSTOM, false);
    }

    private void setBitrateMode(boolean custom) {
        prefs.sharedPreferences.edit().putBoolean(PREF_BITRATE_MODE_CUSTOM, custom).apply();
    }

    private int getBitrateCustomValue() {
        return prefs.sharedPreferences.getInt(PREF_BITRATE_CUSTOM_VALUE, getDefaultBitrate());
    }

    private void setBitrateCustomValue(int value) {
        prefs.sharedPreferences.edit().putInt(PREF_BITRATE_CUSTOM_VALUE, value).apply();
    }

    private int getDefaultBitrate() {
        Size resolution = prefs.getCameraResolution();
        CameraType camera = prefs.getCameraSelection();
        int framerate = prefs.getSpecificVideoFrameRate(camera);

        int width = resolution.getWidth();
        int height = resolution.getHeight();
        int pixels = width * height;

        // Base bitrate calculation based on resolution
        int baseBitrate;

        if (pixels >= 3840 * 2160) {
            // 4K and above
            baseBitrate = 45000; // 45 Mbps
        } else if (pixels >= 2560 * 1440) {
            // 2K/1440p
            baseBitrate = 16000; // 16 Mbps
        } else if (pixels >= 1920 * 1080) {
            // FHD/1080p
            baseBitrate = 12000; // 12 Mbps
        } else if (pixels >= 1280 * 720) {
            // HD/720p
            baseBitrate = 8000; // 8 Mbps
        } else if (pixels >= 854 * 480) {
            // 480p
            baseBitrate = 4000; // 4 Mbps
        } else {
            // SD and below
            baseBitrate = 2000; // 2 Mbps
        }

        // Adjust for framerate (higher framerate = higher bitrate)
        if (framerate > 30) {
            baseBitrate = (int) (baseBitrate * (framerate / 30.0f));
        }

        return baseBitrate;
    }

    private int getCurrentBitrate() {
        return getBitrateMode() ? getBitrateCustomValue() : getDefaultBitrate();
    }



    // Bitrate helper text mirroring thresholds used in dialog (low<3, high>100) but
    // for current value


    // ---- Zoom Ratio Migration ----


    private List<Float> buildZoomRatioOptions(CameraType cam) {
        List<Float> list = new ArrayList<>();
        float max = getHardwareSupportedMaxZoomRatio(cam);
        for (float z = 0.5f; z <= max + 0.001f; z += 0.5f) {
            list.add(((float) Math.round(z * 10)) / 10f);
        }
        if (!list.contains(1.0f))
            list.add(1.0f);
        java.util.Collections.sort(list);
        return list;
    }

    private float getHardwareSupportedMaxZoomRatio(CameraType cam) {
        final float defaultMaxZoom = 5.0f; // legacy default
        Context ctx = getContext();
        if (ctx == null)
            return defaultMaxZoom;
        try {
            CameraManager manager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String id = getActualCameraIdForType(cam);
            if (id == null)
                return defaultMaxZoom;
            CameraCharacteristics ch = manager.getCameraCharacteristics(id);
            if (Build.VERSION.SDK_INT >= 30) {
                Range<Float> range = ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (range != null) {
                    return range.getUpper();
                }
            }
        } catch (Exception e) {
            FLog.w(TAG, "Zoom ratio query failed", e);
        }
        return defaultMaxZoom;
    }

    // ---- Maximum Recording Duration ----
    private String getMaximumRecordingDurationLabel() {
        String option = prefs.getMaximumRecordingDurationOption();
        if (MaximumRecordingDuration.OPTION_1_MINUTE.equals(option)) {
            return getString(R.string.maximum_recording_duration_1_minute);
        }
        if (MaximumRecordingDuration.OPTION_2_MINUTES.equals(option)) {
            return getString(R.string.maximum_recording_duration_2_minutes);
        }
        if (MaximumRecordingDuration.OPTION_3_MINUTES.equals(option)) {
            return getString(R.string.maximum_recording_duration_3_minutes);
        }
        if (MaximumRecordingDuration.OPTION_4_MINUTES.equals(option)) {
            return getString(R.string.maximum_recording_duration_4_minutes);
        }
        if (MaximumRecordingDuration.OPTION_5_MINUTES.equals(option)) {
            return getString(R.string.maximum_recording_duration_5_minutes);
        }
        if (MaximumRecordingDuration.OPTION_10_MINUTES.equals(option)) {
            return getString(R.string.maximum_recording_duration_10_minutes);
        }
        if (MaximumRecordingDuration.OPTION_30_MINUTES.equals(option)) {
            return getString(R.string.maximum_recording_duration_30_minutes);
        }
        if (MaximumRecordingDuration.OPTION_1_HOUR.equals(option)) {
            return getString(R.string.maximum_recording_duration_1_hour);
        }
        if (MaximumRecordingDuration.OPTION_2_HOURS.equals(option)) {
            return getString(R.string.maximum_recording_duration_2_hours);
        }
        if (MaximumRecordingDuration.OPTION_3_HOURS.equals(option)) {
            return getString(R.string.maximum_recording_duration_3_hours);
        }
        if (MaximumRecordingDuration.OPTION_4_HOURS.equals(option)) {
            return getString(R.string.maximum_recording_duration_4_hours);
        }
        if (MaximumRecordingDuration.OPTION_5_HOURS.equals(option)) {
            return getString(R.string.maximum_recording_duration_5_hours);
        }
        if (MaximumRecordingDuration.OPTION_CUSTOM.equals(option)) {
            return prefs.getCustomMaximumRecordingDurationHuman();
        }
        return getString(R.string.maximum_recording_duration_no_limit);
    }





    // ---- Video Splitting Migration ----










    private CamcorderProfile getCamcorderProfile(CameraType type) {
        try {
            String id = getActualCameraIdForType(type);
            if (id == null)
                return null;
            int camId = Integer.parseInt(id); // choose quality based on current resolution
            Size res = prefs.getCameraResolution();
            int quality = CamcorderProfile.QUALITY_HIGH;
            if (res.getWidth() >= 3840 || res.getHeight() >= 2160)
                quality = CamcorderProfile.QUALITY_2160P;
            else if (res.getWidth() >= 1920 || res.getHeight() >= 1080)
                quality = CamcorderProfile.QUALITY_1080P;
            else if (res.getWidth() >= 1280 || res.getHeight() >= 720)
                quality = CamcorderProfile.QUALITY_720P;
            if (CamcorderProfile.hasProfile(camId, quality))
                return getCamcorderProfile(camId, quality);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * CamcorderProfile.get(int,int) is deprecated in API 31 (its replacement,
     * getAll, returns EncoderProfiles with a different shape). The 2-arg form
     * still works on all supported devices, so we route through this helper
     * and keep the deprecation localized.
     */
    @SuppressWarnings("deprecation")
    private CamcorderProfile getCamcorderProfile(int cameraId, int quality) {
        return CamcorderProfile.get(cameraId, quality);
    }

    // Simple holder mirroring legacy CameraIdInfo
    private static class CameraIdInfo {
        final String id;
        final String displayName;

        CameraIdInfo(String id, String display) {
            this.id = id;
            this.displayName = display;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Creates dynamic helper text for lens selection based on detected cameras
     */
    private String createLensHelperText() {
        if (availableBackCameras.isEmpty()) {
            return getString(R.string.setting_back_lens_info_single_v2); // Fallback to static text
        }
        
        int cameraCount = availableBackCameras.size();
        if (cameraCount == 1) {
            return getString(R.string.setting_back_lens_info_single_v2);
        } else {
            StringBuilder helper = new StringBuilder();
            helper.append("Detected ").append(cameraCount).append(" back cameras: ");
            for (int i = 0; i < availableBackCameras.size(); i++) {
                if (i > 0) helper.append(", ");
                CameraIdInfo info = availableBackCameras.get(i);
                // Extract just the lens type from display name (before the parentheses)
                String lensType = info.displayName.split(" \\(")[0];
                helper.append(lensType);
            }
            helper.append(". Select the lens you want to use for recording.");
            return helper.toString();
        }
    }

    /**
     * Creates dynamic helper text for zoom ratio explaining auto-selection behavior
     */
    private String createZoomHelperText(CameraType cameraType) {
        StringBuilder helper = new StringBuilder();
        
        // Get the default zoom ratio for this camera type
        float defaultZoom = prefs.getSpecificZoomRatio(cameraType);
        
        helper.append("Zoom ratio controls the field of view. ");
        helper.append("Default is ").append(String.format(Locale.getDefault(), "%.1fx", defaultZoom));
        
        // Add information about auto-selection for wide-angle cameras
        if (cameraType == CameraType.BACK) {
            String selectedCameraId = prefs.getSelectedBackCameraId();
            if (selectedCameraId != null && !selectedCameraId.equals(Constants.DEFAULT_BACK_CAMERA_ID)) {
                // Check if this is a wide-angle camera
                if (isWideAngleCamera(selectedCameraId)) {
                    helper.append(" (auto-selected 0.5x for wide-angle lens to show full field of view)");
                }
            }
        }
        
        helper.append(". Lower values = wider view, higher values = more zoomed in.");
        return helper.toString();
    }
    
    /**
     * Helper method to check if a camera ID is wide-angle (reused from SharedPreferencesManager logic)
     */
    private boolean isWideAngleCamera(String cameraId) {
        try {
            Context ctx = getContext();
            if (ctx == null) return false;
            
            CameraManager manager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (manager != null) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (focalLengths != null && focalLengths.length > 0) {
                    float focal = focalLengths[0];
                    // Wide-angle cameras typically have focal lengths <= 8mm
                    return focal <= 8f;
                }
            }
        } catch (Exception e) {
            FLog.w(TAG, "Error checking camera characteristics for ID: " + cameraId, e);
        }
        return false;
    }

    /**
     * Queries the Camera2 API for supported FPS ranges for the primary camera of
     * the specified type
     * and returns a list of all unique framerates supported by the hardware.
     *
     * @param cameraType The camera type (FRONT or BACK) to query.
     * @return A sorted List<Integer> of all supported frame rates. Returns default
     *         [30] on critical errors.
     */
    @ExperimentalCamera2Interop
    private List<Integer> getHardwareSupportedFrameRates(CameraType cameraType) {
        if (getContext() == null) {
            FLog.e(TAG, "FPS Query: Context is null.");
            return Collections.singletonList(Constants.DEFAULT_VIDEO_FRAME_RATE);
        }

        // DUAL_PIP doesn't map to a single camera — use BACK camera for FPS capability queries
        if (cameraType != null && cameraType.isDual()) {
            cameraType = CameraType.BACK;
        }

        // Use the new CameraX utility for framerate detection
        try {
            FLog.i(TAG, "Using CameraX API for framerate detection");
            List<Integer> detectedRates = CameraXFrameRateUtil.getHardwareSupportedFrameRates(requireContext(),
                    cameraType);

            // Special handling for Samsung devices - explicitly add 60fps support if not
            // already present
            if (DeviceHelper.isSamsung() && !detectedRates.contains(60)) {
                FLog.i(TAG, "Samsung device detected - Adding 60fps support explicitly");
                List<Integer> enhancedRates = new ArrayList<>(detectedRates);
                enhancedRates.add(60);
                Collections.sort(enhancedRates);
                return enhancedRates;
            }

            // Removed Huawei-specific handling to standardize behavior across devices

            return detectedRates;
        } catch (Exception e) {
            FLog.e(TAG, "Error using CameraX for framerate detection, falling back to Camera2", e);
            // Fallback to Camera2 API implementation - retain original logic
            return getHardwareSupportedFrameRatesUsingCamera2(cameraType);
        }
    }

    /**
     * Original Camera2 API implementation for getting supported framerates.
     * Kept as a fallback method in case CameraX implementation fails.
     * 
     * @param cameraType The camera type (FRONT or BACK) to query.
     * @return A sorted List<Integer> of all supported frame rates.
     */
    private List<Integer> getHardwareSupportedFrameRatesUsingCamera2(CameraType cameraType) {
        FLog.i(TAG,
                "=== Getting Hardware Supported FPS for CameraType: " + cameraType + " using Camera2 API ===");
        final List<Integer> defaultRateList = Collections.singletonList(Constants.DEFAULT_VIDEO_FRAME_RATE);

        if (getContext() == null) {
            FLog.e(TAG, "FPS Query: Context is null.");
            return defaultRateList;
        }

        CameraManager manager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            FLog.e(TAG, "FPS Query: CameraManager is null.");
            return defaultRateList;
        }

        // DUAL_PIP doesn't map to a single camera — use BACK camera for FPS capability queries
        if (cameraType != null && cameraType.isDual()) {
            cameraType = CameraType.BACK;
        }

        String targetCameraId = null;
        try {
            // Find the primary camera ID for the requested type (Prioritize ID "0" for
            // BACK)
            String firstBackIdFallback = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (cameraType == CameraType.FRONT && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        targetCameraId = id;
                        FLog.d(TAG, "FPS Query: Found FRONT camera ID: " + targetCameraId);
                        break;
                    }
                    if (cameraType == CameraType.BACK && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        if (id.equals(Constants.DEFAULT_BACK_CAMERA_ID)) {
                            targetCameraId = id; // Found preferred default BACK ID "0"
                            FLog.d(TAG, "FPS Query: Found Primary BACK camera ID: " + targetCameraId);
                            break;
                        } else if (firstBackIdFallback == null) {
                            firstBackIdFallback = id; // Store first BACK ID encountered as fallback
                        }
                    }
                }
            }
            // If default BACK "0" wasn't found, use the fallback if available
            if (cameraType == CameraType.BACK && targetCameraId == null && firstBackIdFallback != null) {
                targetCameraId = firstBackIdFallback;
                FLog.w(TAG,
                        "FPS Query: Default Back ID '0' not found/back-facing. Using first available back ID: "
                                + targetCameraId);
            }
        } catch (CameraAccessException | IllegalArgumentException e) {
            FLog.e(TAG, "FPS Query: Error accessing camera list/characteristics during ID selection", e);
            return defaultRateList;
        }

        if (targetCameraId == null) {
            FLog.e(TAG, "FPS Query: Could not find a valid Camera ID for type: " + cameraType);
            return defaultRateList;
        }
        FLog.d(TAG, "FPS Query: Using Camera ID: " + targetCameraId + " for characteristic lookup.");

        // Get the available AE FPS ranges for the target camera
        Range<Integer>[] hardwareFpsRanges = null;
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(targetCameraId);
            hardwareFpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        } catch (CameraAccessException | IllegalArgumentException e) {
            FLog.e(TAG, "FPS Query: Camera access/arg exception getting FPS ranges for ID " + targetCameraId,
                    e);
            // Return default [30] on error accessing ranges
            return defaultRateList;
        } catch (Exception e) {
            FLog.e(TAG, "FPS Query: Unexpected error getting FPS ranges for ID " + targetCameraId, e);
            return defaultRateList;
        }

        // Create a set to store all possible framerates from the ranges
        Set<Integer> framerates = new TreeSet<>(); // TreeSet automatically sorts

        // First check for higher framerates in CamcorderProfiles
        // This is important because some devices don't report high FPS in Camera2 AE
        // ranges
        // but do support them in CamcorderProfile
        int maxProfileFps = 30; // Default assumption

        try {
            // Check all quality levels for max framerates
            int cameraId = Integer.parseInt(targetCameraId);
            int[] qualities = {
                    CamcorderProfile.QUALITY_HIGH, CamcorderProfile.QUALITY_2160P,
                    CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_720P
            };

            for (int quality : qualities) {
                if (CamcorderProfile.hasProfile(cameraId, quality)) {
                    CamcorderProfile profile = getCamcorderProfile(cameraId, quality);
                    if (profile != null && profile.videoFrameRate > maxProfileFps) {
                        maxProfileFps = profile.videoFrameRate;
                        FLog.d(TAG, "FPS Query: Found higher framerate " + maxProfileFps +
                                " in CamcorderProfile quality " + quality);
                    }
                }
            }

            // Check for specific high-framerate profiles if they exist
            if (Build.VERSION.SDK_INT >= 29) { // Android 10+
                try {
                    // Some devices have specific high-FPS profiles for 60fps/120fps
                    if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH_SPEED_HIGH)) {
                        CamcorderProfile profile = getCamcorderProfile(cameraId,
                                CamcorderProfile.QUALITY_HIGH_SPEED_HIGH);
                        if (profile != null && profile.videoFrameRate > maxProfileFps) {
                            maxProfileFps = profile.videoFrameRate;
                            FLog.d(TAG, "FPS Query: Found high-speed framerate " +
                                    maxProfileFps + " in QUALITY_HIGH_SPEED_HIGH");
                        }
                    }

                    if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH_SPEED_1080P)) {
                        CamcorderProfile profile = getCamcorderProfile(cameraId,
                                CamcorderProfile.QUALITY_HIGH_SPEED_1080P);
                        if (profile != null && profile.videoFrameRate > maxProfileFps) {
                            maxProfileFps = profile.videoFrameRate;
                            FLog.d(TAG, "FPS Query: Found high-speed framerate " +
                                    maxProfileFps + " in QUALITY_HIGH_SPEED_1080P");
                        }
                    }

                    if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH_SPEED_720P)) {
                        CamcorderProfile profile = getCamcorderProfile(cameraId,
                                CamcorderProfile.QUALITY_HIGH_SPEED_720P);
                        if (profile != null && profile.videoFrameRate > maxProfileFps) {
                            maxProfileFps = profile.videoFrameRate;
                            FLog.d(TAG, "FPS Query: Found high-speed framerate " +
                                    maxProfileFps + " in QUALITY_HIGH_SPEED_720P");
                        }
                    }
                } catch (Exception e) {
                    FLog.w(TAG, "FPS Query: Error checking high-speed profiles: " + e.getMessage());
                }
            }

            FLog.d(TAG, "FPS Query: Maximum framerate found in CamcorderProfiles: " + maxProfileFps);

        } catch (NumberFormatException e) {
            FLog.w(TAG, "FPS Query: Could not parse camera ID as integer: " + targetCameraId);
            // Continue with Camera2 API method only
        } catch (Exception e) {
            FLog.w(TAG, "FPS Query: Error checking CamcorderProfiles: " + e.getMessage());
            // Continue with Camera2 API method only
        }

        if (hardwareFpsRanges == null || hardwareFpsRanges.length == 0) {
            FLog.w(TAG, "FPS Query: No AE FPS ranges reported by hardware for camera " + targetCameraId
                    + ". Using CamcorderProfile.");
            // Create some basic framerates based on CamcorderProfile information
            for (int fps = 10; fps <= maxProfileFps; fps += 5) {
                if (fps <= 30 || fps % 30 == 0) { // Include all multiples of 30 over 30fps
                    framerates.add(fps);
                }
            }

            // Add standard framerates
            int[] standardRates = { 24, 25, 30, 60, 90, 120 };
            for (int rate : standardRates) {
                if (rate <= maxProfileFps) {
                    framerates.add(rate);
                }
            }

            if (framerates.isEmpty()) {
                framerates.add(Constants.DEFAULT_VIDEO_FRAME_RATE); // Default fallback
            }
        } else {
            FLog.d(TAG, "FPS Query: Hardware reported AE ranges for ID " + targetCameraId + ": "
                    + Arrays.toString(hardwareFpsRanges));

            // Process each range to get ALL supported framerates
            for (Range<Integer> range : hardwareFpsRanges) {
                if (range != null) {
                    int lower = range.getLower();
                    int upper = range.getUpper();

                    FLog.d(TAG, "FPS Query: Processing range " + lower + "-" + upper);

                    // For most devices, framerates are available at discrete steps (usually 1fps)
                    // Add ALL integer values within the range to ensure we catch values like 59fps
                    for (int fps = lower; fps <= upper; fps++) {
                        framerates.add(fps);
                    }
                }
            }

            // If CamcorderProfile reported higher framerates than Camera2 API, add those
            // too
            if (maxProfileFps > 30) {
                FLog.d(TAG, "FPS Query: Adding higher framerates from CamcorderProfile");

                // Add standard high framerates if they're supported by the profile
                int[] highRates = { 60, 90, 120, 240 };
                for (int rate : highRates) {
                    if (rate <= maxProfileFps) {
                        framerates.add(rate);
                        FLog.d(TAG, "FPS Query: Added " + rate + "fps from CamcorderProfile");
                    }
                }
            }
        }

        // Ensure we have at least one value (the default)
        if (framerates.isEmpty()) {
            FLog.e(TAG, "FPS Query: No valid framerates found from hardware ranges. Adding default: "
                    + Constants.DEFAULT_VIDEO_FRAME_RATE);
            framerates.add(Constants.DEFAULT_VIDEO_FRAME_RATE);
        }

        // Convert to list and ensure the list is sorted (which TreeSet already does)
        List<Integer> finalSupportedRates = new ArrayList<>(framerates);

        // If the list is too large (some devices might report hundreds of values),
        // we could optionally filter to keep just common/useful values or step at
        // 5-10fps intervals
        if (finalSupportedRates.size() > 20) {
            FLog.w(TAG, "FPS Query: Large number of framerates detected (" + finalSupportedRates.size() +
                    "), keeping only useful values for UI");

            // Filter to keep standard values + any higher FPS values
            Set<Integer> filteredRates = new TreeSet<>();

            // Important standard rates to always include if supported
            int[] standardRates = { 24, 25, 30, 50, 60, 90, 120, 240 };
            for (int rate : standardRates) {
                if (framerates.contains(rate)) {
                    filteredRates.add(rate);
                }
            }

            // Also include significant non-standard rates
            // This handles cases like 59.94fps (which is often rounded to 59 or 60)
            for (int fps : framerates) {
                // Include rates divisible by 5 (e.g., 5, 10, 15, 20, 25...)
                if (fps % 5 == 0 && fps <= 60) {
                    filteredRates.add(fps);
                }
                // Include all higher framerates (e.g., 72, 90, 120, etc.)
                else if (fps > 60) {
                    filteredRates.add(fps);
                }
            }

            // If we've excluded the default rate by accident, add it back
            if (!filteredRates.contains(Constants.DEFAULT_VIDEO_FRAME_RATE) &&
                    framerates.contains(Constants.DEFAULT_VIDEO_FRAME_RATE)) {
                filteredRates.add(Constants.DEFAULT_VIDEO_FRAME_RATE);
            }

            // Replace the full list with our filtered list
            finalSupportedRates = new ArrayList<>(filteredRates);
            FLog.d(TAG, "FPS Query: Filtered to " + finalSupportedRates.size() + " useful framerates");
        }

        return finalSupportedRates;
    }

}
