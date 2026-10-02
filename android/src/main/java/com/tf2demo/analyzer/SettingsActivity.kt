package com.tf2demo.analyzer

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        // The AMOLED overlay must be set before any content is inflated.
        if (AppearanceStore.amoled(this) && isDarkUi()) {
            setTheme(R.style.Theme_TF2DemoAnalyzer_AMOLED)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Same edge-to-edge handling as the main screen: pad by the system
        // bars' insets so the list doesn't render under the status/task bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupAppearance()

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

    private fun setupAppearance() {
        val mode = AppearanceStore.themeMode(this)
        val themeGroup =
            findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.themeGroup)
        themeGroup.check(
            when (mode) {
                AppearanceStore.MODE_LIGHT -> R.id.themeLight
                AppearanceStore.MODE_DARK -> R.id.themeDark
                else -> R.id.themeSystem
            }
        )
        themeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.themeLight -> AppearanceStore.MODE_LIGHT
                R.id.themeDark -> AppearanceStore.MODE_DARK
                else -> AppearanceStore.MODE_SYSTEM
            }
            AppearanceStore.setThemeMode(this, newMode)
            // Mode change is process-wide; recreate so this screen re-reads it.
            (application as AnalyzerApp).applyTheme()
            recreate()
        }

        val amoled = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
            R.id.amoledSwitch
        )
        amoled.isChecked = AppearanceStore.amoled(this)
        amoled.setOnCheckedChangeListener { _, checked ->
            AppearanceStore.setAmoled(this, checked)
            recreate()
        }
    }

    /** True when the current configuration resolves to a dark UI. */
    private fun isDarkUi(): Boolean {
        val mode = AppearanceStore.themeMode(this)
        val systemDark =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        return when (mode) {
            AppearanceStore.MODE_DARK -> true
            AppearanceStore.MODE_LIGHT -> false
            else -> systemDark
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
