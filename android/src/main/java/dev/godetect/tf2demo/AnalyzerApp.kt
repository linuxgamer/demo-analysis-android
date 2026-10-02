package dev.godetect.tf2demo

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

class AnalyzerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    /** Reads the stored appearance preferences and applies them process-wide. */
    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (AppearanceStore.themeMode(this)) {
                AppearanceStore.MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppearanceStore.MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
