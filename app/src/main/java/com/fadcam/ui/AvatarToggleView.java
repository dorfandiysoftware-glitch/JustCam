package com.fadcam.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.CompoundButton;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.fadcam.R;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Material 3 switch. Historically an animated avatar toggle; the class name and public API are
 * kept so every existing layout and caller keeps working, but the control is now a standard
 * {@link MaterialSwitch}.
 */
public class AvatarToggleView extends FrameLayout {

    private final MaterialSwitch sw;
    private boolean silent = false;
    private int eyeColor = 0;

    @Nullable
    private CompoundButton.OnCheckedChangeListener listener;

    public AvatarToggleView(Context context) {
        this(context, null);
    }

    public AvatarToggleView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AvatarToggleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Overlay services and dialogs may carry a non-Material3 theme; MaterialSwitch needs M3 attributes.
        TypedValue tv = new TypedValue();
        boolean m3 = context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainerHighest, tv, true);
        Context themed = m3 ? context : new ContextThemeWrapper(context, R.style.Base_Theme_FadCam);
        sw = new MaterialSwitch(themed);
        sw.setOnCheckedChangeListener((button, checked) -> {
            if (silent) return;
            try {
                if (com.fadcam.Utils.hapticsAllowedForUi(getContext())) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            } catch (Exception ignored) {
            }
            if (listener != null) listener.onCheckedChanged(button, checked);
        });
        addView(sw, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    public boolean isChecked() {
        return sw.isChecked();
    }

    /** Sets the state without firing the listener. */
    public void setChecked(boolean newChecked) {
        setChecked(newChecked, false);
    }

    /** Sets the state without firing the listener (the switch animates by itself). */
    public void setChecked(boolean newChecked, boolean animate) {
        if (sw.isChecked() == newChecked) return;
        silent = true;
        try {
            sw.setChecked(newChecked);
        } finally {
            silent = false;
        }
    }

    public void setOnCheckedChangeListener(@Nullable CompoundButton.OnCheckedChangeListener l) {
        this.listener = l;
    }

    /** Simulates a user tap: toggles and fires the listener. */
    @Override
    public boolean performClick() {
        sw.toggle();
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        sw.setEnabled(enabled);
    }

    /** Kept for API compatibility; a Material switch has no "dim" mode. */
    public void setDimWhenOff(boolean dim) {
    }

    /** Kept for API compatibility; the eye colour no longer applies. */
    public void setEyeColor(int color) {
        this.eyeColor = color;
    }

    public int getEyeColor() {
        return eyeColor;
    }
}
