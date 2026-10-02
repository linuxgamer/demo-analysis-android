package com.tf2demo.analyzer

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var pickedUri: Uri? = null
    private var pickedName: String? = null
    private var pickedLastModified: Long = 0
    private var analysisJob: Job? = null
    private var lastResult: String? = null

    private lateinit var analyzeButton: Button
    private lateinit var exportButton: Button
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
    private lateinit var progressBar: ProgressBar
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

    private fun exportFileName(): String =
        (pickedName?.removeSuffix(".dem") ?: "detections") + "-detections.json"

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

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        analyzeButton = findViewById(R.id.analyzeButton)
        exportButton = findViewById(R.id.exportButton)
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
        progressBar = findViewById(R.id.progressBar)
        progressLabel = findViewById(R.id.progressLabel)

        detectionsList.layoutManager = LinearLayoutManager(this)
        detectionsList.adapter = detectionAdapter

        analyzeButton.setOnClickListener { startAnalysis() }
        exportButton.setOnClickListener {
            exportDetections.launch(exportFileName())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_pick -> {
            pickDemo.launch(arrayOf("*/*"))
            true
        }

        R.id.action_settings -> {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
            true
        }

        else -> super.onOptionsItemSelected(item)
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
        infoCreated.text = pickedLastModified.takeIf { it > 0 }?.let {
            getString(
                R.string.created_line,
                SimpleDateFormat("HH:mm, dd.MM.yyyy", Locale.getDefault()).format(Date(it)),
            )
        }
        // Before the analysis runs, only the creation time is known.
        infoAuthor.visibility = View.GONE
        infoAuthorSteamid.visibility = View.GONE
        infoTotal.visibility = View.GONE
        demoInfoCard.visibility = View.VISIBLE
        pickPlaceholder.visibility = View.GONE
    }

    private fun startAnalysis() {
        val uri = pickedUri ?: return
        analysisJob?.cancel()
        lastResult = null
        exportButton.isEnabled = false
        hideResults()
        progressArea.visibility = View.VISIBLE
        progressBar.progress = 0
        progressLabel.text = ""
        status.text = ""

        DemoAnalysis.resetProgress()
        // Progress polling runs while the blocking JNI call churns on Dispatchers.IO.
        // Both workers write the same global counter, so the polled value
        // oscillates between their positions; display a monotonic maximum
        // instead, otherwise the percentage jumps backwards (60% -> 42%).
        var shownTicks = 0
        analysisJob = lifecycleScope.launch {
            val poller = launch {
                while (isActive) {
                    val current = DemoAnalysis.progressCurrent()
                    val total = DemoAnalysis.progressTotal()
                    if (total > 0) {
                        shownTicks = maxOf(shownTicks, current)
                        progressBar.progress = (shownTicks * 1000L / total).toInt()
                        progressLabel.text =
                            getString(R.string.progress_line, shownTicks * 100 / total, shownTicks, total)
                    }
                    delay(250)
                }
            }
            val outcome = runCatching {
                withContext(Dispatchers.IO) { runAnalysis(uri) }
            }
            poller.cancel()
            progressArea.visibility = View.GONE
            outcome
                .onSuccess { json ->
                    lastResult = json
                    showResult(json)
                    exportButton.isEnabled = true
                }
                .onFailure { e ->
                    status.text = getString(R.string.failed, e.message)
                }
        }
    }

    private fun runAnalysis(uri: Uri): String {
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("cannot open $uri")
        // The Rust side adopts the fd as its very first step and closes it on
        // every path; detach here so neither the PFD finalizer nor we close it
        // a second time.
        val fd = pfd.detachFd()
        // Enabled set and parameter overrides from the settings screen; the
        // Rust side normalizes the config (drops unknown entries, coerces
        // number kinds) exactly like the desktop analyser does.
        val state = SettingsStore.load(this, SettingsStore.schema(DemoAnalysis.algorithmsJson()))
        val enabled = state.enabled.filterValues { it }.keys.toTypedArray()
        val config = SettingsStore.paramsJson(state)
        return DemoAnalysis.analyse(fd, enabled, config, threads = 2)
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
        infoAuthor.setOnClickListener { view ->
            root.optString("author", "").takeIf { it.isNotEmpty() }?.let {
                DetectionAdapter.copyToClipboard(view.context, it)
            }
        }
        // Rust fills this only when the author nick maps to exactly one player.
        infoAuthorSteamid.visibility = View.GONE
        root.optLong("author_steamid", 0L).takeIf { it != 0L }?.let { steamId ->
            infoAuthorSteamid.text = getString(R.string.steamid_line, steamId)
            infoAuthorSteamid.visibility = View.VISIBLE
            infoAuthorSteamid.setOnClickListener { view ->
                DetectionAdapter.copyToClipboard(view.context, steamId.toString())
            }
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
        val json = lastResult ?: return
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            android.widget.Toast.makeText(this, R.string.exported, android.widget.Toast.LENGTH_SHORT)
                .show()
        }.onFailure {
            android.widget.Toast.makeText(
                this,
                getString(R.string.failed, it.message),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
}
