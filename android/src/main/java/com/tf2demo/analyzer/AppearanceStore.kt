package com.tf2demo.analyzer

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.view.WindowCompat

/**
 * UI preferences: theme mode (follow system / light / dark), the system bar
 * icon colors, and which site opens player profiles.
 */
object AppearanceStore {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    // Profile viewer sites; the stored value is the index into PROFILE_SITES.
    const val SITE_STEAM = 0
    const val SITE_STEAMHISTORY = 1
    const val SITE_SHADEFALL = 2
    val PROFILE_SITES = listOf(SITE_STEAM, SITE_STEAMHISTORY, SITE_SHADEFALL)

    private const val PREFS = "appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_PROFILE_SITE = "profile_site"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeMode(context: Context): Int = prefs(context).getInt(KEY_MODE, MODE_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply()
    }

    /** Which site player rows open; defaults to Steam. */
    fun profileSite(context: Context): Int =
        prefs(context).getInt(KEY_PROFILE_SITE, SITE_STEAM)

    fun setProfileSite(context: Context, site: Int) {
        prefs(context).edit().putInt(KEY_PROFILE_SITE, site).apply()
    }

    /** True when the current configuration resolves to a dark UI. */
    fun isDarkUi(context: Context): Boolean = when (themeMode(context)) {
        MODE_DARK -> true
        MODE_LIGHT -> false
        else ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Makes the system bars match the app: transparent, with icon color
     * derived from the resolved theme (dark icons on light backgrounds and
     * vice versa), otherwise white status bar icons sit on white.
     */
    fun applySystemBarTheme(activity: Activity) {
        val controller = WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        )
        val dark = isDarkUi(activity)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}
