package dev.godetect.tf2demo

import androidx.annotation.StringRes

/**
 * Static metadata for the detector algorithms: one-line descriptions shown
 * on algorithm-row long-presses (detections tree and settings screen).
 */
object AlgorithmInfo {
    @StringRes
    fun description(algorithmName: String): Int? = descriptions[algorithmName]

    private val descriptions = mapOf(
        "viewangles_180degrees" to R.string.algo_180,
        "nocrex/oob_pitch" to R.string.algo_oob_pitch,
        "nocrex/angle_repeat" to R.string.algo_angle_repeat,
        "nocrex/aimsnap" to R.string.algo_aimsnap,
        "angle_history" to R.string.algo_angle_history,
        "backtrack" to R.string.algo_backtrack,
        "double_tap" to R.string.algo_double_tap,
        "triggerbot" to R.string.algo_triggerbot,
        "firewindow" to R.string.algo_firewindow,
        "recorder_aim_assist" to R.string.algo_recorder,
        "fidoo/silent_aim" to R.string.algo_silent_aim,
        "fidoo/psilent4" to R.string.algo_psilent,
        "fidoo/nospread" to R.string.algo_nospread,
        "fidoo/auto_backstab" to R.string.algo_auto_backstab,
        "fidoo/bunnyhop" to R.string.algo_bunnyhop,
        "fidoo/invalid_equip_region" to R.string.algo_equip_region,
    )
}
