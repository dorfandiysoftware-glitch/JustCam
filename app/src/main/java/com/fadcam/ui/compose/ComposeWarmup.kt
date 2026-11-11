package com.fadcam.ui.compose

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView

/** Composes the settings screen once off-screen so the first real open is not slow. */
object ComposeWarmup {
    @JvmStatic
    fun run(activity: Activity) {
        val root = activity.window.decorView as? ViewGroup ?: return
        root.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            val v = ComposeView(activity).apply {
                alpha = 0f
                isClickable = false
                setContent {
                    JustCamTheme(amoled = false) {
                        SettingsScreen(showTrash = true, onBack = {}, onOpen = {})
                    }
                }
            }
            root.addView(v, ViewGroup.LayoutParams(1, 1))
            root.postDelayed({ root.removeView(v) }, 1500)
        }, 800)
    }
}
