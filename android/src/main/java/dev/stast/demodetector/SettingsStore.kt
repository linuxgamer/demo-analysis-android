package dev.stast.demodetector

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

    enum class Kind { FLOAT, INT, BOOL }

    data class ParamInfo(val name: String, val kind: Kind, val default: Any)

    data class AlgorithmInfo(
        val name: String,
        val defaultEnabled: Boolean,
        val params: List<ParamInfo>,
    )

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
                defaultEnabled = entry.optBoolean("default", false),
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
     * ones appear in the UI.
     */
    fun importText(text: String, into: State) {
        val imported = JSONObject(text)
        imported.keys().asSequence().forEach { algorithm ->
            val values = imported.optJSONObject(algorithm) ?: return@forEach
            val target = into.params.getOrPut(algorithm) { mutableMapOf() }
            values.keys().asSequence().forEach { param ->
                when (val value = values.get(param)) {
                    is Boolean -> target[param] = value
                    is Number -> target[param] = value
                }
            }
        }
    }

    private fun matchesKind(value: Any, kind: Kind): Boolean = when (kind) {
        Kind.BOOL -> value is Boolean
        Kind.INT -> value is Int || (value is Long && value in Int.MIN_VALUE..Int.MAX_VALUE)
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
