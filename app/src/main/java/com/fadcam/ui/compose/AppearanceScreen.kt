package com.fadcam.ui.compose

import android.os.Build
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.fadcam.Constants
import com.fadcam.R
import com.fadcam.SharedPreferencesManager

private val LANGUAGE_CODES = listOf("en", "zh", "ar", "fr", "tr", "ps", "in", "it", "el", "de", "es", "et", "ru")

/** Appearance: only Theme and Language. */
@Composable
fun AppearanceScreen(
    prefs: SharedPreferencesManager,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit,
    onLanguage: (String) -> Unit,
) {
    val sp = prefs.sharedPreferences
    var amoled by remember { mutableStateOf(sp.getString(Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME) == "Faded Night") }
    var dynamic by remember { mutableStateOf(sp.getBoolean(PREF_DYNAMIC_COLORS, true)) }
    var lang by remember { mutableStateOf(prefs.language) }
    val names = stringArrayResource(R.array.languages_array)
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SettingsPage(stringResource(R.string.setting_theme_title), onBack) {
        section(stringResource(R.string.setting_theme_title)) {
            item {
                SwitchRow(
                    stringResource(R.string.jc_theme_dynamic),
                    stringResource(if (canDynamic) R.string.jc_theme_dynamic_sub else R.string.jc_theme_dynamic_unsupported),
                    checked = dynamic && canDynamic, enabled = canDynamic,
                ) {
                    dynamic = it
                    sp.edit().putBoolean(PREF_DYNAMIC_COLORS, it).apply()
                    onThemeChanged(if (amoled) "Faded Night" else Constants.DEFAULT_APP_THEME)
                }
            }
            item {
                SwitchRow(stringResource(R.string.jc_theme_black), stringResource(R.string.jc_theme_black_sub), checked = amoled) {
                    amoled = it
                    val name = if (it) "Faded Night" else Constants.DEFAULT_APP_THEME
                    sp.edit().putString(Constants.PREF_APP_THEME, name).apply()
                    onThemeChanged(name)
                }
            }
        }
        section(stringResource(R.string.setting_language_title)) {
            item {
                val idx = LANGUAGE_CODES.indexOf(lang).coerceAtLeast(0)
                ChoiceRow(
                    title = stringResource(R.string.setting_language_title),
                    value = names.getOrElse(idx) { lang },
                    options = LANGUAGE_CODES.mapIndexed { i, c -> c to names.getOrElse(i) { c } },
                    selected = LANGUAGE_CODES[idx],
                ) {
                    lang = it
                    sp.edit().putString(Constants.LANGUAGE_KEY, it).apply()
                    onLanguage(it)
                }
            }
        }
    }
}

private fun LazyListScope.section(label: String, content: LazyListScope.() -> Unit) {
    item { SectionLabel(label) }
    content()
}
