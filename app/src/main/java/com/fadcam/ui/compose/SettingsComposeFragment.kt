package com.fadcam.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.fadcam.Constants
import com.fadcam.MainActivity
import com.fadcam.SharedPreferencesManager
import com.fadcam.ui.AboutFragment
import com.fadcam.ui.AppearanceSettingsFragment
import com.fadcam.ui.AudioSettingsFragment
import com.fadcam.ui.AutomationSettingsFragment
import com.fadcam.ui.DebugLogBottomSheetFragment
import com.fadcam.ui.HapticsSettingsFragment
import com.fadcam.ui.NotificationSettingsFragment
import com.fadcam.ui.OverlayNavUtil
import com.fadcam.ui.PrefsBackupBottomSheetFragment
import com.fadcam.ui.SecuritySettingsFragment
import com.fadcam.ui.ShortcutsSettingsFragment
import com.fadcam.ui.StorageSettingsFragment
import com.fadcam.ui.TrashFragment
import com.fadcam.ui.VideoPlayerSettingsFragment
import com.fadcam.ui.VideoSettingsFragment
import com.fadcam.ui.WatermarkSettingsFragment
import com.fadcam.fadrec.ui.ScreenRecordingSettingsFragment

/** Settings home, drawn with Compose Material 3. Sub-pages still open the existing fragments. */
class SettingsComposeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val prefs = SharedPreferencesManager.getInstance(requireContext())
        val theme = prefs.sharedPreferences.getString(Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME)
        val amoled = theme == "Faded Night"
        var showTrash by mutableStateOf(prefs.isShowTrashInSettings)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                JustCamTheme(amoled = amoled) {
                    SettingsScreen(
                        showTrash = showTrash,
                        onHideTrash = { prefs.setShowTrashInSettings(false); showTrash = false },
                        onBack = {
                            (activity as? MainActivity)?.switchFragment(0, true)
                        },
                        onOpen = { open(it) },
                    )
                }
            }
        }
    }

    private fun sub(fragment: Fragment) =
        OverlayNavUtil.show(requireActivity(), fragment, fragment.javaClass.simpleName)

    private fun open(key: String) {
        when (key) {
            "video" -> sub(VideoSettingsFragment())
            "audio" -> sub(AudioSettingsFragment())
            "watermark" -> sub(WatermarkSettingsFragment())
            "screen" -> sub(ScreenRecordingSettingsFragment())
            "appearance" -> sub(AppearanceSettingsFragment())
            "player" -> sub(VideoPlayerSettingsFragment())
            "storage" -> sub(StorageSettingsFragment())
            "notifications" -> sub(NotificationSettingsFragment())
            "security" -> sub(SecuritySettingsFragment())
            "widgets" -> sub(ShortcutsSettingsFragment())
            "haptics" -> sub(HapticsSettingsFragment())
            "automation" -> sub(AutomationSettingsFragment())
            "trash" -> sub(TrashFragment())
            "about" -> sub(AboutFragment())
            "backup" -> PrefsBackupBottomSheetFragment().show(parentFragmentManager, "prefs_backup")
            "debug" -> DebugLogBottomSheetFragment.newInstance().show(parentFragmentManager, "debug_log_tools")
        }
    }
}
