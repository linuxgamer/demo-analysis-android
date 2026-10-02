package com.tf2demo.analyzer

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recent analyses, persisted as a single JSON file in the app's files dir:
 * SAF uri (re-openable without re-picking while the grant lasts), display
 * name, last-analyzed timestamp and the detection count. Newest first,
 * capped at 10 entries.
 */
object HistoryStore {
    private const val FILE = "history.json"
    private const val MAX_ENTRIES = 10

    data class Entry(val uri: String, val name: String, val analyzedAt: Long, val detections: Int)

    fun load(context: Context): List<Entry> = runCatching {
        val file = java.io.File(context.filesDir, FILE)
        if (!file.exists()) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Entry(
                uri = obj.getString("uri"),
                name = obj.getString("name"),
                analyzedAt = obj.getLong("analyzedAt"),
                detections = obj.getInt("detections"),
            )
        }
    }.getOrDefault(emptyList())

    /** Moves `entry` to the top (re-analysis) or adds it as a new entry. */
    fun record(context: Context, uri: Uri, name: String, detections: Int) {
        val existing = load(context).toMutableList()
        existing.removeAll { it.uri == uri.toString() }
        existing.add(0, Entry(uri.toString(), name, System.currentTimeMillis(), detections))
        save(context, existing.take(MAX_ENTRIES))
    }

    fun clear(context: Context) {
        java.io.File(context.filesDir, FILE).delete()
    }

    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("uri", entry.uri)
                    .put("name", entry.name)
                    .put("analyzedAt", entry.analyzedAt)
                    .put("detections", entry.detections),
            )
        }
        java.io.File(context.filesDir, FILE).writeText(array.toString())
    }
}
