package com.fadcam.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fadcam.SharedPreferencesManager;

/** Compose Material 3 page (see ui/compose/SimpleScreens.kt). */
public class AdvancedSettingsFragment extends androidx.fragment.app.Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        SharedPreferencesManager prefs = SharedPreferencesManager.getInstance(requireContext());
        androidx.compose.ui.platform.ComposeView v = new androidx.compose.ui.platform.ComposeView(requireContext());
        v.setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        com.fadcam.ui.compose.SimpleScreensKt.setAdvancedContent(v, () -> { OverlayNavUtil.dismiss(requireActivity()); return kotlin.Unit.INSTANCE; });
        return v;
    }
}
