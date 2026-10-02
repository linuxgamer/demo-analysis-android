package dev.godetect.tf2demo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Algorithm settings, mirroring the desktop analyser: parameter values are a
 * plain `{"algorithm": {"param": value}}` JSON object (the exact shape of the
 * desktop `params.json`, so files are interchangeable), while the
 * enabled/disabled flags live only in the app's preferences.
 *
 * Values keep the JSON kinds from the schema (Float/Int/Boolean); unknown
 * algorithm/parameter names survive a round-trip untouched — the Rust side
 * runs `normalize_config` before applying anything, so stale or foreign
 * entries are dropped there, same as on desktop.
 */
object SettingsStore {
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PARAMS = "params"
    private const val KEY_THREADS = "threads"
    private const val KEY_MAX_DEMO_BYTES = "max_demo_bytes"

    /** Valid worker counts for `analyse`; each worker re-reads the demo. */
    val THREAD_OPTIONS = listOf(1, 2, 4)

    /** Limit choices in MB; 0 = Auto (derived from total RAM). */
    val MAX_DEMO_MB_OPTIONS = listOf(0, 256, 512, 1024, 2048)

    fun threads(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THREADS, 2)
        return THREAD_OPTIONS.firstOrNull { it == stored } ?: 2
    }

    fun setThreads(context: Context, threads: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THREADS, threads).apply()
    }

    /** Auto means ~1/4 of total RAM, clamped to the largest option (2 GB). */
    fun autoMaxDemoBytes(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val quarterRam = memoryInfo.totalMem / 4
        return quarterRam.coerceAtMost(2048L * 1024 * 1024)
    }

    /**
     * Effective byte limit for demo files, 0 disables the check. The stored
     * value is in MB; 0 (Auto) resolves to a RAM-based limit.
     */
    fun maxDemoBytes(context: Context): Long {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_DEMO_BYTES, 0)
        return if (stored == 0) autoMaxDemoBytes(context) else stored.toLong() * 1024 * 1024
    }

    fun maxDemoMbOption(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_DEMO_BYTES, 0)

    fun setMaxDemoMb(context: Context, mb: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MAX_DEMO_BYTES, mb).apply()
    }

    /** Clears algorithm settings: everything back to the defaults. */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    enum class Kind { FLOAT, INT, BOOL }

    data class ParamInfo(val name: String, val kind: Kind, val default: Any)

    data class AlgorithmInfo(
        val name: String,
        val defaultEnabled: Boolean,
        val params: List<ParamInfo>,
    )

    // Algorithms the desktop disables by default but the app runs by default:
    // on mobile every bit of signal counts, dev algorithms stay excluded by
    // the Rust side regardless.
    val FORCE_DEFAULT_ON = setOf("backtrack", "double_tap", "nocrex/aimsnap")

    class State(
        val enabled: MutableMap<String, Boolean> = mutableMapOf(),
        val params: MutableMap<String, MutableMap<String, Any>> = mutableMapOf(),
    )

    /** Parses the schema returned by the Rust `algorithmsJson()`. */
    fun schema(algorithmsJson: String): List<AlgorithmInfo> {
        val array = JSONArray(algorithmsJson)
        return (0 until array.length()).map { i ->
            val entry = array.getJSONObject(i)
            val paramsJson = entry.optJSONObject("params")
            val params = paramsJson?.let { obj ->
                obj.keys().asSequence().map { paramName ->
                    val value = obj.get(paramName)
                    val (kind, typed) = when (value) {
                        is Boolean -> Kind.BOOL to value
                        is Int -> Kind.INT to value
                        else -> Kind.FLOAT to (value as Number).toDouble().toFloat()
                    }
                    ParamInfo(paramName, kind, typed)
                }.sortedBy { it.name }.toList()
            } ?: emptyList()
            AlgorithmInfo(
                name = entry.getString("name"),
                defaultEnabled = entry.optBoolean("default", false) ||
                    FORCE_DEFAULT_ON.contains(entry.getString("name")),
                params = params,
            )
        }.sortedBy { it.name }
    }

    fun load(context: Context, schema: List<AlgorithmInfo>): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = State()
        val enabled = prefs.getJSONObjectOpt(KEY_ENABLED)
        val params = prefs.getJSONObjectOpt(KEY_PARAMS)
        for (algorithm in schema) {
            state.enabled[algorithm.name] =
                if (enabled?.has(algorithm.name) == true) enabled.getBoolean(algorithm.name)
                else algorithm.defaultEnabled
            val savedParams = params?.optJSONObject(algorithm.name) ?: continue
            val target = state.params.getOrPut(algorithm.name) { mutableMapOf() }
            for (param in algorithm.params) {
                if (savedParams.has(param.name)) {
                    val value = savedParams.get(param.name)
                    if (matchesKind(value, param.kind)) {
                        target[param.name] = typed(value, param.kind)
                    }
                }
            }
        }
        return state
    }

    fun save(context: Context, state: State) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = JSONObject()
        state.enabled.forEach { (name, on) -> enabled.put(name, on) }
        val params = JSONObject()
        state.params.forEach { (algorithm, values) ->
            val obj = JSONObject()
            values.forEach { (name, value) ->
                when (value) {
                    is Boolean -> obj.put(name, value)
                    is Number -> obj.put(name, value)
                }
            }
            params.put(algorithm, obj)
        }
        prefs.edit().putString(KEY_ENABLED, enabled.toString())
            .putString(KEY_PARAMS, params.toString()).apply()
    }

    /** Parameter values as the desktop-compatible config object. */
    fun paramsJson(state: State): String {
        val params = JSONObject()
        state.params.forEach { (algorithm, values) ->
            if (values.isNotEmpty()) {
                val obj = JSONObject()
                values.forEach { (name, value) ->
                    when (value) {
                        is Boolean -> obj.put(name, value)
                        is Number -> obj.put(name, value)
                    }
                }
                params.put(algorithm, obj)
            }
        }
        return params.toString()
    }

    /** Pretty `params.json` for exporting/sharing. */
    fun exportText(state: State): String = JSONObject(paramsJson(state)).toString(2)

    /**
     * Merges an imported desktop `params.json` over the current state. Unknown
     * algorithms/parameters are kept in the state (harmless) but only known
     * ones appear in the UI. Numbers are normalized to Double/Int — org.json
     * hands out BigDecimal, which nothing downstream understands.
     */
    fun importText(text: String, into: State) {
        val imported = JSONObject(text)
        imported.keys().asSequence().forEach { algorithm ->
            val values = imported.optJSONObject(algorithm) ?: return@forEach
            val target = into.params.getOrPut(algorithm) { mutableMapOf() }
            values.keys().asSequence().forEach { param ->
                when (val value = values.get(param)) {
                    is Boolean -> target[param] = value
                    is Int -> target[param] = value
                    is Number -> target[param] = value.toDouble()
                }
            }
        }
    }

    private fun matchesKind(value: Any, kind: Kind): Boolean = when (kind) {
        Kind.BOOL -> value is Boolean
        // Desktop accepts a float literal where an int is declared (`8.0`).
        Kind.INT -> value is Int || (value is Double && Math.floor(value) == value)
        Kind.FLOAT -> value is Number && value !is Boolean
    }

    private fun typed(value: Any, kind: Kind): Any = when (kind) {
        Kind.BOOL -> value as Boolean
        Kind.INT -> (value as Number).toInt()
        Kind.FLOAT -> (value as Number).toFloat()
    }

    private fun android.content.SharedPreferences.getJSONObjectOpt(key: String): JSONObject? =
        getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
}
