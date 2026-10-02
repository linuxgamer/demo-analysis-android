package com.tf2demo.analyzer

import android.content.Context
import android.content.SharedPreferences

/**
 * Appearance preferences: theme mode (follow system / light / dark) and the
 * AMOLED toggle. The theme is applied in [AnalyzerApp.onCreate] and in
 * [SettingsActivity] before `setContentView` so the change is instant.
 */
object AppearanceStore {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val PREFS = "appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_AMOLED = "amoled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeMode(context: Context): Int = prefs(context).getInt(KEY_MODE, MODE_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply()
    }

    fun amoled(context: Context): Boolean = prefs(context).getBoolean(KEY_AMOLED, false)

    fun setAmoled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AMOLED, on).apply()
    }
}
