package dev.godetect.tf2demo

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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
            .setNavigationOnClickListener { finish() }

        // Same edge-to-edge handling as the main screen: pad by the system
        // bars' insets so the list doesn't render under the status/task bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        AppearanceStore.applySystemBarTheme(this)

        setupAppearance()
        setupLanguage()
        setupProfileSite()

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
        findViewById<android.widget.TextView>(R.id.aboutButton).setOnClickListener {
            showAbout()
        }
    }

    /** Version, license summary and upstream credits. */
    private fun showAbout() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.app_name) + " " + version)
            .setMessage(
                getString(
                    R.string.about_text,
                    "https://github.com/Nocrex/demo-analysis",
                    "https://github.com/eatthefreakingpaper/tf2-demo-player-aio",
                    "https://github.com/demostf/parser",
                ),
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

        setupThreads()
        setupMaxSize()
        setupReset()
    }

    /**
     * Fills a toggle group with buttons for every option and persists the
     * selected one through [select]; used for threads and the demo size cap.
     */
    private fun <T> fillToggleGroup(
        groupId: Int,
        options: List<T>,
        current: T,
        label: (T) -> String,
        select: (T) -> Unit,
    ) {
        val group =
            findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(groupId)
        // The group tracks selection by child view id: programmatic buttons
        // need generated ids, and the initial highlight must go through
        // group.check() (setting isChecked before addView is not tracked).
        val optionById = mutableMapOf<Int, T>()
        options.forEach { option ->
            val button = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                id = android.view.View.generateViewId()
                text = label(option)
                optionById[id] = option
            }
            group.addView(
                button,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            if (option == current) group.check(button.id)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            optionById[checkedId]?.let(select)
        }
    }

    private fun setupThreads() =
        fillToggleGroup(
            R.id.threadsGroup,
            SettingsStore.THREAD_OPTIONS,
            SettingsStore.threads(this),
            label = { it.toString() },
        ) { SettingsStore.setThreads(this, it) }

    /** System / English / Russian per-app language, applied via AppCompat. */
    private fun setupLanguage() {
        val current = androidx.appcompat.app.AppCompatDelegate
            .getApplicationLocales()
            .toLanguageTags()
        val options = listOf(
            "" to getString(R.string.language_system),
            "en" to "English",
            "ru" to "Русский",
        )
        fillToggleGroup(
            R.id.languageGroup,
            options,
            options.firstOrNull { it.first == current } ?: options.first(),
            label = { it.second },
        ) { pair ->
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(pair.first),
            )
        }
    }

    private fun setupMaxSize() =
        fillToggleGroup(
            R.id.maxSizeGroup,
            SettingsStore.MAX_DEMO_MB_OPTIONS,
            SettingsStore.maxDemoMbOption(this),
            label = { if (it == 0) getString(R.string.auto) else "${it}M" },
        ) { SettingsStore.setMaxDemoMb(this, it) }

    private fun setupProfileSite() {
        val siteLabels = mapOf(
            AppearanceStore.SITE_STEAM to getString(R.string.site_steam),
            AppearanceStore.SITE_STEAMHISTORY to getString(R.string.site_steamhistory),
            AppearanceStore.SITE_SHADEFALL to getString(R.string.site_shadefall),
        )
        fillToggleGroup(
            R.id.profileSiteGroup,
            AppearanceStore.PROFILE_SITES,
            AppearanceStore.profileSite(this),
            label = { siteLabels.getValue(it) },
        ) { AppearanceStore.setProfileSite(this, it) }
    }

    /** Confirm-then-clear for the algorithm settings. */
    private fun setupReset() {
        findViewById<android.widget.ImageButton>(R.id.resetButton).setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset)
                .setMessage(R.string.reset_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    SettingsStore.reset(this)
                    val schema = SettingsStore.schema(DemoAnalysis.algorithmsJson())
                    state = SettingsStore.load(this, schema)
                    adapter = AlgorithmAdapter(schema, state) { SettingsStore.save(this, state) }
                    findViewById<RecyclerView>(R.id.algorithmsList).adapter = adapter
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    /** True when the current configuration resolves to a dark UI. */
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
