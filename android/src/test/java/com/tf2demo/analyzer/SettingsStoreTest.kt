package com.tf2demo.analyzer

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    private val schemaJson = """
        [
          {"name": "backtrack", "default": false, "params": {"window": 1.0}},
          {"name": "nocrex/oob_pitch", "default": true,
           "params": {"min_pitch": -89.999, "max_pitch": 89.999}},
          {"name": "viewangles_180degrees", "default": true, "params": null},
          {"name": "double_tap", "default": false, "params": {"delay": 8}}
        ]
    """.trimIndent()

    @Test
    fun schema_parses_kinds_and_force_default_on() {
        val schema = SettingsStore.schema(schemaJson)
        assertEquals(4, schema.size)

        val oob = schema.first { it.name == "nocrex/oob_pitch" }
        assertEquals(
            listOf(
                SettingsStore.ParamInfo("max_pitch", SettingsStore.Kind.FLOAT, 89.999f),
                SettingsStore.ParamInfo("min_pitch", SettingsStore.Kind.FLOAT, -89.999f),
            ),
            oob.params,
        )

        // Upstream disables these; the app forces them on.
        assertTrue(schema.first { it.name == "backtrack" }.defaultEnabled)
        assertTrue(schema.first { it.name == "double_tap" }.defaultEnabled)
        // Upstream default stands where not forced.
        assertTrue(schema.first { it.name == "nocrex/oob_pitch" }.defaultEnabled)
        // Parameter-less algorithms expose no params.
        assertTrue(schema.first { it.name == "viewangles_180degrees" }.params.isEmpty())
    }

    @Test
    fun paramsJson_emits_only_non_empty_parameter_maps() {
        val state = SettingsStore.State()
        state.params["backtrack"] = mutableMapOf("window" to 1.5f)
        state.params["viewangles_180degrees"] = mutableMapOf()

        val json = org.json.JSONObject(SettingsStore.paramsJson(state))
        assertEquals(1.5f, json.getJSONObject("backtrack").getDouble("window").toFloat())
        // The empty map must not appear at all, like the desktop params.json.
        assertTrue(!json.has("viewangles_180degrees"))
    }

    @Test
    fun importText_merges_and_keeps_unknown_entries() {
        val state = SettingsStore.State()
        SettingsStore.importText(
            """{"backtrack": {"window": 2.0}, "gone/algorithm": {"x": 1}}""",
            state,
        )
        assertEquals(2.0, state.params["backtrack"]!!["window"])
        assertEquals(1, state.params["gone/algorithm"]!!["x"])
    }

    @Test
    fun exportText_round_trips_through_import() {
        val state = SettingsStore.State()
        state.params["double_tap"] = mutableMapOf("delay" to 8)
        state.enabled["double_tap"] = false

        val imported = SettingsStore.State()
        SettingsStore.importText(SettingsStore.exportText(state), imported)

        assertEquals(8, imported.params["double_tap"]!!["delay"])
        // Enabled flags live in prefs, not in the exported file.
        assertTrue(!imported.enabled.containsKey("double_tap"))
    }
}
