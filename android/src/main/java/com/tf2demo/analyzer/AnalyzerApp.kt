package com.tf2demo.analyzer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

class AnalyzerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Material You is applied manually here instead of through
                // DynamicColors.applyToActivitiesIfAvailable: the library's
                // own callback would stack its style AFTER our AMOLED overlay
                // and override its colors. This order (dynamic first, AMOLED
                // second) keeps both.
                DynamicColors.applyIfAvailable(activity)
                if (AppearanceStore.amoled(activity) && AppearanceStore.isDarkUi(activity)) {
                    activity.theme.applyStyle(R.style.Theme_TF2DemoAnalyzer_AMOLED, true)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
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
