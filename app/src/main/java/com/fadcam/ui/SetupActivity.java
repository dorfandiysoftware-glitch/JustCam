package com.fadcam.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.fadcam.Constants;
import com.fadcam.MainActivity;
import com.fadcam.R;
import com.fadcam.SharedPreferencesManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** First-run setup: welcome, permissions, done. Lands on the camera when finished. */
public class SetupActivity extends AppCompatActivity {

    private static final int STEP_WELCOME = 0;
    private static final int STEP_PERMISSIONS = 1;
    private static final int STEP_DONE = 2;

    private int step = STEP_WELCOME;
    private View stepWelcome;
    private View stepPermissions;
    private View stepDone;
    private LinearProgressIndicator progress;
    private MaterialButton btnBack;
    private MaterialButton btnNext;
    private View permCamera;
    private View permMic;
    private View permNotifications;
    private View permLocation;
    private View permBattery;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        refreshPermissionRows();
                        requestBatteryExemption();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Base_Theme_FadCam);
        super.onCreate(savedInstanceState);
        try {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        } catch (Exception ignored) {
        }
        setContentView(R.layout.activity_setup);

        stepWelcome = findViewById(R.id.setup_step_welcome);
        stepPermissions = findViewById(R.id.setup_step_permissions);
        stepDone = findViewById(R.id.setup_step_done);
        progress = findViewById(R.id.setup_progress);
        btnBack = findViewById(R.id.setup_back);
        btnNext = findViewById(R.id.setup_next);
        permCamera = findViewById(R.id.perm_camera);
        permMic = findViewById(R.id.perm_mic);
        permNotifications = findViewById(R.id.perm_notifications);
        permLocation = findViewById(R.id.perm_location);
        permBattery = findViewById(R.id.perm_battery);

        bindRow(permCamera, R.drawable.ic_camera, R.string.setup_perm_camera, R.string.setup_perm_camera_desc);
        bindRow(permMic, R.drawable.ic_mic, R.string.setup_perm_mic, R.string.setup_perm_mic_desc);
        bindRow(permNotifications, R.drawable.ic_notification_icon, R.string.setup_perm_notifications,
                R.string.setup_perm_notifications_desc);
        bindRow(permLocation, R.drawable.ic_location, R.string.setup_perm_location, R.string.setup_perm_location_desc);

        bindRow(permBattery, R.drawable.ic_check_circle, R.string.setup_perm_battery, R.string.setup_perm_battery_desc);
        permBattery.setOnClickListener(v -> requestBatteryExemption());
        findViewById(R.id.setup_grant_all).setOnClickListener(v -> requestAll());
        btnBack.setOnClickListener(v -> showStep(step - 1));
        btnNext.setOnClickListener(v -> onNext());

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt("step", STEP_WELCOME);
        }
        showStep(step);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("step", step);
    }

    @Override
    public void onBackPressed() {
        if (step > STEP_WELCOME) {
            showStep(step - 1);
        } else {
            super.onBackPressed();
        }
    }

    private void bindRow(View row, int icon, int title, int desc) {
        ((ImageView) row.findViewById(R.id.perm_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.perm_title)).setText(title);
        ((TextView) row.findViewById(R.id.perm_desc)).setText(desc);
    }

    private void showStep(int newStep) {
        step = Math.max(STEP_WELCOME, Math.min(STEP_DONE, newStep));
        stepWelcome.setVisibility(step == STEP_WELCOME ? View.VISIBLE : View.GONE);
        stepPermissions.setVisibility(step == STEP_PERMISSIONS ? View.VISIBLE : View.GONE);
        stepDone.setVisibility(step == STEP_DONE ? View.VISIBLE : View.GONE);
        progress.setProgressCompat(step + 1, true);
        btnBack.setVisibility(step == STEP_WELCOME ? View.INVISIBLE : View.VISIBLE);
        btnNext.setText(step == STEP_DONE ? R.string.setup_finish : R.string.setup_next);
        if (step == STEP_PERMISSIONS) refreshPermissionRows();
    }

    private void onNext() {
        if (step == STEP_PERMISSIONS && !granted(Manifest.permission.CAMERA)) {
            Toast.makeText(this, R.string.setup_camera_required, Toast.LENGTH_SHORT).show();
            requestAll();
            return;
        }
        if (step == STEP_DONE) {
            finishSetup();
        } else {
            showStep(step + 1);
        }
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAll() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        list.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionLauncher.launch(list.toArray(new String[0]));
    }

    private boolean batteryExempt() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryExemption() {
        if (batteryExempt()) return;
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (step == STEP_PERMISSIONS) refreshPermissionRows();
    }

    private void refreshPermissionRows() {
        setStatus(permCamera, granted(Manifest.permission.CAMERA));
        setStatus(permMic, granted(Manifest.permission.RECORD_AUDIO));
        boolean notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || granted(Manifest.permission.POST_NOTIFICATIONS);
        setStatus(permNotifications, notif);
        setStatus(permLocation, granted(Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(Manifest.permission.ACCESS_COARSE_LOCATION));
        setStatus(permBattery, batteryExempt());
    }

    private void setStatus(View row, boolean ok) {
        row.findViewById(R.id.perm_status).setVisibility(ok ? View.VISIBLE : View.INVISIBLE);
    }

    private void finishSetup() {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(this);
        prefs.sharedPreferences.edit()
                .putBoolean(Constants.COMPLETED_ONBOARDING_KEY, true)
                .commit();
        prefs.setShowOnboarding(false);
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
