package com.tf2demo.analyzer

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Algorithm settings: enable/disable switches, parameter dialogs, and
 * import/export of the desktop-compatible params.json via SAF.
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var adapter: AlgorithmAdapter
    private lateinit var state: SettingsStore.State

    private val importParams =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readText(uri)?.let { importFrom(it) }
        }

    private val exportParams =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) writeText(uri, SettingsStore.exportText(state))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val schema = SettingsStore.schema(DemoAnalysis.algorithmsJson())
        state = SettingsStore.load(this, schema)

        adapter = AlgorithmAdapter(schema, state) { SettingsStore.save(this, state) }
        findViewById<RecyclerView>(R.id.algorithmsList).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = this@SettingsActivity.adapter
        }

        findViewById<Button>(R.id.importButton).setOnClickListener {
            importParams.launch(arrayOf("application/json", "text/*", "*/*"))
        }
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportParams.launch("params.json")
        }
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    private fun writeText(uri: Uri, text: String) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            Toast.makeText(this, R.string.exported, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, getString(R.string.failed, it.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun importFrom(text: String) {
        runCatching { SettingsStore.importText(text, state) }
            .onSuccess {
                SettingsStore.save(this, state)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, R.string.imported, Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(this, getString(R.string.failed, it.message), Toast.LENGTH_LONG)
                    .show()
            }
    }
}
