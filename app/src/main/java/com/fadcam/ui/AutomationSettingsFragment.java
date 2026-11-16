package com.fadcam.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fadcam.SharedPreferencesManager;
import com.fadcam.ui.compose.MoreScreensKt;

/** Automation preferences (Volume Shutter), drawn with Compose Material 3. */
public class AutomationSettingsFragment extends BaseFragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        androidx.compose.ui.platform.ComposeView cv = new androidx.compose.ui.platform.ComposeView(requireContext());
        cv.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        MoreScreensKt.setAutomationContent(cv, SharedPreferencesManager.getInstance(requireContext()),
                () -> { OverlayNavUtil.dismiss(requireActivity()); return kotlin.Unit.INSTANCE; });
        return cv;
    }
}
