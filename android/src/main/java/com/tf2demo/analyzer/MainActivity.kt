package com.tf2demo.analyzer

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var pickedUri: Uri? = null
    private var pickedName: String? = null
    private var pickedLastModified: Long = 0
    private val viewModel: AnalysisViewModel by viewModels()

    private lateinit var analyzeButton: Button
    private lateinit var exportButton: Button
    private lateinit var cancelButton: Button
    private lateinit var status: TextView
    private lateinit var pickPlaceholder: TextView
    private lateinit var demoInfoCard: View
    private lateinit var infoName: TextView
    private lateinit var infoAuthor: TextView
    private lateinit var infoAuthorSteamid: TextView
    private lateinit var infoCreated: TextView
    private lateinit var infoTotal: TextView
    private lateinit var detectionsPanel: View
    private lateinit var detectionsHeader: TextView
    private lateinit var detectionsList: RecyclerView
    private lateinit var progressArea: View
    private lateinit var progressLabel: TextView
    private val detectionAdapter = DetectionAdapter(playerNames = emptyMap())

    private val pickDemo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pickedUri = uri
                queryDemoInfo(uri)
                analyzeButton.isEnabled = true
                status.text = ""
                hideResults()
            }
        }

    private val exportDetections =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeExport(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // targetSdk 35 enforces edge-to-edge: pad by the system bars' insets so
        // nothing hides under the status bar or the taskbar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        AppearanceStore.applySystemBarTheme(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // The title lives in the toolbar's layout (with the app icon next to
        // it); disable the ActionBar-provided one to avoid a duplicate.
        supportActionBar?.setDisplayShowTitleEnabled(false)

        analyzeButton = findViewById(R.id.analyzeButton)
        exportButton = findViewById(R.id.exportButton)
        cancelButton = findViewById(R.id.cancelButton)
        status = findViewById(R.id.status)
        pickPlaceholder = findViewById(R.id.pickPlaceholder)
        demoInfoCard = findViewById(R.id.demoInfoCard)
        infoName = findViewById(R.id.infoName)
        infoAuthor = findViewById(R.id.infoAuthor)
        infoAuthorSteamid = findViewById(R.id.infoAuthorSteamid)
        infoCreated = findViewById(R.id.infoCreated)
        infoTotal = findViewById(R.id.infoTotal)
        detectionsPanel = findViewById(R.id.detectionsPanel)
        detectionsHeader = findViewById(R.id.detectionsHeader)
        detectionsList = findViewById(R.id.detectionsList)
        progressArea = findViewById(R.id.progressArea)
        progressLabel = findViewById(R.id.progressLabel)

        detectionsList.layoutManager = LinearLayoutManager(this)
        detectionsList.adapter = detectionAdapter
        detectionAdapter.onOpenProfile = { steamId -> openProfile(steamId, ask = false) }
        detectionAdapter.onChooseProfile = { steamId -> openProfile(steamId, ask = true) }
        detectionAdapter.onTickDetails = { dataJson ->
            if (!dataJson.isNullOrEmpty()) showTickDetails(dataJson)
        }

        analyzeButton.setOnClickListener {
            pickedUri?.let(::startAnalysisIfSizeOk)
        }
        cancelButton.setOnClickListener { viewModel.cancel() }
        exportButton.setOnClickListener {
            exportDetections.launch(exportFileName())
        }

        // Author name and server IP copy on hold, value only.
        infoAuthor.setOnLongClickListener { view ->
            (view.tag as? String)?.let { DetectionAdapter.copyToClipboard(view.context, it) }
            true
        }
        infoAuthorSteamid.setOnLongClickListener { view ->
            (view.tag as? String)?.let { DetectionAdapter.copyToClipboard(view.context, it) }
            true
        }
        infoCreated.setOnLongClickListener { view ->
            (view.tag as? String)?.let { DetectionAdapter.copyToClipboard(view.context, it) }
            true
        }

        // Re-render whatever phase the VM is in (also right after a rotation).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.phase.collect { phase -> render(phase) }
            }
        }
        findViewById<TextView>(R.id.historyClear).setOnClickListener {
            HistoryStore.clear(this)
            refreshHistory()
        }
        refreshHistory()
    }

    /** Recent analyses, shown while no demo is picked; tap re-runs one. */
    private fun refreshHistory() {
        val entries = HistoryStore.load(this)
        val container = findViewById<android.widget.LinearLayout>(R.id.historyList)
        container.removeAllViews()
        val empty = entries.isEmpty() || pickedUri != null
        container.visibility = if (empty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.historyHeader).visibility =
            if (empty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.historyClear).visibility =
            if (empty) View.GONE else View.VISIBLE
        if (empty) return

        val inflater = android.view.LayoutInflater.from(this)
        entries.forEach { entry ->
            val view = inflater.inflate(R.layout.item_history, container, false)
            view.findViewById<TextView>(R.id.historyName).text = entry.name
            view.findViewById<TextView>(R.id.historyMeta).text = getString(
                R.string.history_meta,
                entry.detections,
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(entry.analyzedAt)),
            )
            view.setOnClickListener { onHistoryPicked(entry) }
            container.addView(view)
        }
    }

    /** Re-pick a history entry; the SAF grant may have expired by now. */
    private fun onHistoryPicked(entry: HistoryStore.Entry) {
        val uri = Uri.parse(entry.uri)
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.close()
        }.onFailure {
            // Grant expired: route through the picker instead of failing.
            Toast.makeText(this, R.string.history_expired, Toast.LENGTH_SHORT).show()
            pickDemo.launch(arrayOf("*/*"))
            return
        }
        pickedUri = uri
        pickedName = entry.name
        queryDemoInfo(uri)
        analyzeButton.isEnabled = true
        status.text = ""
        hideResults()
        viewModel.start(uri)
    }

    /** Profile site URLs; order must match AppearanceStore SITE_* constants. */
    private fun profileUrl(site: Int, steamId: String): String? = when (site) {
        AppearanceStore.SITE_STEAM -> "https://steamcommunity.com/profiles/$steamId"
        AppearanceStore.SITE_STEAMHISTORY -> "https://steamhistory.net/id/$steamId"
        AppearanceStore.SITE_SHADEFALL -> "https://shadefall.net/archive/$steamId"
        else -> null
    }

    /** Pretty-printed `data` payload of a single detection. */
    private fun showTickDetails(dataJson: String) {
        val pretty = runCatching {
            org.json.JSONObject(dataJson).toString(2)
        }.getOrElse { dataJson }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detection_details)
            .setMessage(pretty)
            .setPositiveButton(R.string.copy) { _, _ ->
                DetectionAdapter.copyToClipboard(this, pretty)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openProfile(steamId: String, ask: Boolean) {
        val remembered = AppearanceStore.profileSite(this)
        if (!ask && remembered != AppearanceStore.SITE_ASK) {
            profileUrl(remembered, steamId)?.let { openUrl(it) }
            return
        }
        var rememberChoice = AppearanceStore.profileSite(this) != AppearanceStore.SITE_ASK
        val sites = listOf(
            AppearanceStore.SITE_STEAM to getString(R.string.site_steam),
            AppearanceStore.SITE_STEAMHISTORY to getString(R.string.site_steamhistory),
            AppearanceStore.SITE_SHADEFALL to getString(R.string.site_shadefall),
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.open_profile) + " · " + steamId)
            .setItems(sites.map { it.second }.toTypedArray()) { _, which ->
                val site = sites[which].first
                profileUrl(site, steamId)?.let { openUrl(it) }
                // "Remember" was pre-selected from the last use of this dialog.
                if (rememberChoice) AppearanceStore.setProfileSite(this, site)
            }
            .setMultiChoiceItems(
                arrayOf(getString(R.string.remember)),
                booleanArrayOf(AppearanceStore.profileSite(this) != AppearanceStore.SITE_ASK),
            ) { _, _, checked -> rememberChoice = checked }
            .setNegativeButton(R.string.just_once, null)
            .show()
    }

    /** Custom Tab when a browser supports it, plain intent otherwise. */
    private fun openUrl(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    /** Size guard: tf-demo-parser keeps the whole demo in RAM. */
    private fun startAnalysisIfSizeOk(uri: Uri) {
        val limit = SettingsStore.maxDemoBytes(this)
        val size = querySize(uri)
        if (limit > 0 && size > limit) {
            val limitMb = limit / (1024 * 1024)
            Toast.makeText(
                this,
                getString(R.string.demo_too_large, size / (1024 * 1024), limitMb),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        viewModel.start(uri)
    }

    private fun querySize(uri: Uri): Long =
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else 0L
        } ?: 0L

    private fun render(phase: AnalysisViewModel.Phase) {
        when (phase) {
            is AnalysisViewModel.Phase.Running -> {
                progressArea.visibility = View.VISIBLE
                progressLabel.text =
                    getString(R.string.progress_line, phase.ticks * 100 / phase.total, phase.ticks, phase.total)
                analyzeButton.isEnabled = false
                cancelButton.visibility = View.VISIBLE
                status.text = ""
            }

            is AnalysisViewModel.Phase.Done -> {
                progressArea.visibility = View.GONE
                cancelButton.visibility = View.GONE
                analyzeButton.isEnabled = pickedUri != null
                showResult(phase.json)
                exportButton.isEnabled = true
                viewModel.pickedUri?.let { uri ->
                    HistoryStore.record(this, uri, pickedName ?: uri.lastPathSegment ?: uri.toString(),
                        runCatching { JSONObject(phase.json).optJSONArray("detections")?.length() ?: 0 }
                            .getOrDefault(0))
                }
            }

            is AnalysisViewModel.Phase.Failed -> {
                progressArea.visibility = View.GONE
                cancelButton.visibility = View.GONE
                analyzeButton.isEnabled = pickedUri != null
                status.text = if (phase.cancelled) {
                    getString(R.string.cancelled)
                } else {
                    getString(R.string.failed, phase.message)
                }
            }

            AnalysisViewModel.Phase.Idle -> {}
        }
    }

    /** Name and creation time from the SAF metadata, before any analysis. */
    private fun queryDemoInfo(uri: Uri) {
        pickedName = null
        pickedLastModified = 0
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) pickedName = cursor.getString(nameIndex)
        }
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val stats = android.system.Os.fstat(pfd.fileDescriptor)
                pickedLastModified = stats.st_mtime * 1000L
            }
        }
        infoName.text = pickedName ?: uri.lastPathSegment
        // Copyable card values share one long-press handler; plain taps do
        // nothing, matching the detections list behavior.
        infoCreated.text = pickedLastModified.takeIf { it > 0 }?.let {
            getString(
                R.string.created_line,
                SimpleDateFormat("HH:mm, dd.MM.yyyy", Locale.getDefault()).format(Date(it)),
            )
        }
        infoCreated.tag = pickedLastModified.takeIf { it > 0 }
            ?.let { SimpleDateFormat("HH:mm, dd.MM.yyyy", Locale.getDefault()).format(Date(it)) }
        // Before the analysis runs, only the creation time is known.
        infoAuthor.visibility = View.GONE
        infoAuthorSteamid.visibility = View.GONE
        infoTotal.visibility = View.GONE
        demoInfoCard.visibility = View.VISIBLE
        pickPlaceholder.visibility = View.GONE
        refreshHistory()
    }

    private fun showResult(json: String) {
        val root = JSONObject(json)
        val detections = root.optJSONArray("detections")

        val playerNames = mutableMapOf<Long, String>()
        root.optJSONObject("players")?.let { players ->
            players.keys().asSequence().forEach { key ->
                playerNames[key.toLongOrNull() ?: return@forEach] = players.getString(key)
            }
        }

        infoAuthor.text = getString(R.string.author_line, root.optString("author", "?"))
        infoAuthor.visibility = View.VISIBLE
        infoAuthor.tag = root.optString("author", "").takeIf { it.isNotEmpty() }
        // Rust fills this only when the author nick maps to exactly one player.
        infoAuthorSteamid.visibility = View.GONE
        root.optLong("author_steamid", 0L).takeIf { it != 0L }?.let { steamId ->
            infoAuthorSteamid.text = getString(R.string.steamid_line, steamId)
            infoAuthorSteamid.visibility = View.VISIBLE
            infoAuthorSteamid.tag = steamId.toString()
        }

        val rows = buildList {
            if (detections != null) {
                for (i in 0 until detections.length()) {
                    val item = detections.getJSONObject(i)
                    add(
                        DetectionRow(
                            tick = item.optInt("tick", 0),
                            algorithm = item.getString("algorithm"),
                            steamId = item.optLong("player", 0).toString(),
                            dataJson = item.optJSONObject("data")?.toString(),
                        )
                    )
                }
            }
        }

        infoTotal.text = getString(R.string.total_line, rows.size)
        infoTotal.visibility = View.VISIBLE
        infoName.visibility = View.VISIBLE
        detectionsHeader.text = getString(R.string.detections)
        detectionsPanel.visibility = View.VISIBLE
        detectionAdapter.playerNames = playerNames
        detectionAdapter.submit(rows)
        status.text = if (rows.isEmpty()) getString(R.string.no_detections) else ""
    }

    private fun hideResults() {
        detectionsPanel.visibility = View.GONE
    }

    private fun writeExport(uri: Uri) {
        val json = when (val phase = viewModel.phase.value) {
            is AnalysisViewModel.Phase.Done -> phase.json
            else -> return
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(this, R.string.exported, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.failed, it.message),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun exportFileName(): String =
        (pickedName?.removeSuffix(".dem") ?: "detections") + "-detections.json"
}
