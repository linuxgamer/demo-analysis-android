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

    /**
     * Moves `entry` to the top (re-analysis) or adds it as a new entry.
     * SAF read-grants of entries dropped past the cap are released so we
     * don't leak permissions for files the user can no longer see.
     */
    fun record(context: Context, uri: Uri, name: String, detections: Int) {
        val existing = load(context).toMutableList()
        existing.removeAll { it.uri == uri.toString() }
        existing.add(0, Entry(uri.toString(), name, System.currentTimeMillis(), detections))
        val kept = existing.take(MAX_ENTRIES)
        save(context, kept)
        releaseStaleGrants(context, kept)
    }

    fun clear(context: Context) {
        save(context, emptyList())
        releaseStaleGrants(context, emptyList())
        java.io.File(context.filesDir, FILE).delete()
    }

    /** Releases read grants for URIs no longer present in the history. */
    private fun releaseStaleGrants(context: Context, kept: List<Entry>) {
        val resolver = context.contentResolver
        val keptUris = kept.map { it.uri }.toSet()
        resolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .filter { it.uri.toString() !in keptUris }
            .forEach { permission ->
                runCatching {
                    resolver.releasePersistableUriPermission(
                        permission.uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
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
