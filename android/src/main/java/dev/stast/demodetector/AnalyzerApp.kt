package dev.stast.demodetector

import android.app.Application
import com.google.android.material.color.DynamicColors

class AnalyzerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Material You: apply a wallpaper-derived color scheme wherever the
        // system supports it (Android 12+); older versions keep the static
        // Material3 DayNight palette.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
