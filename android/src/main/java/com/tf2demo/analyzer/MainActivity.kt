package com.tf2demo.analyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {
    private var pickedUri: Uri? = null
    private var pickedName: String? = null
    private var analysisJob: Job? = null
    private var lastResult: String? = null

    private lateinit var fileName: TextView
    private lateinit var analyzeButton: Button
    private lateinit var shareButton: Button
    private lateinit var progressLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var status: TextView
    private lateinit var demoInfoCard: View
    private lateinit var infoMap: TextView
    private lateinit var infoAuthor: TextView
    private lateinit var infoServer: TextView
    private lateinit var infoCounts: TextView
    private lateinit var detectionsHeader: TextView
    private lateinit var detectionsList: RecyclerView
    private val detectionAdapter = DetectionAdapter()

    private val pickDemo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pickedUri = uri
                pickedName = queryDisplayName(uri)
                fileName.text = pickedName ?: uri.lastPathSegment
                analyzeButton.isEnabled = true
                status.text = ""
                hideResults()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // targetSdk 35 enforces edge-to-edge: pad the content by the system
        // bars' insets so nothing hides under the status bar or taskbar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        fileName = findViewById(R.id.fileName)
        analyzeButton = findViewById(R.id.analyzeButton)
        shareButton = findViewById(R.id.shareButton)
        progressLabel = findViewById(R.id.progressLabel)
        progressBar = findViewById(R.id.progressBar)
        status = findViewById(R.id.status)
        demoInfoCard = findViewById(R.id.demoInfoCard)
        infoMap = findViewById(R.id.infoMap)
        infoAuthor = findViewById(R.id.infoAuthor)
        infoServer = findViewById(R.id.infoServer)
        infoCounts = findViewById(R.id.infoCounts)
        detectionsHeader = findViewById(R.id.detectionsHeader)
        detectionsList = findViewById(R.id.detectionsList)

        detectionsList.layoutManager = LinearLayoutManager(this)
        detectionsList.adapter = detectionAdapter

        findViewById<Button>(R.id.pickButton).setOnClickListener {
            pickDemo.launch(arrayOf("*/*"))
        }
        analyzeButton.setOnClickListener { startAnalysis() }
        shareButton.setOnClickListener { shareResult() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        // Author name and server IP copy on tap, value only.
        infoAuthor.setOnClickListener { copyToClipboard(infoAuthor.tag as? String ?: return@setOnClickListener) }
        infoServer.setOnClickListener { copyToClipboard(infoServer.tag as? String ?: return@setOnClickListener) }
    }

    private fun copyToClipboard(value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("value", value))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun startAnalysis() {
        val uri = pickedUri ?: return
        analysisJob?.cancel()
        lastResult = null
        shareButton.isEnabled = false
        hideResults()
        setProgressVisible(true)
        progressBar.progress = 0
        status.text = ""

        DemoAnalysis.resetProgress()
        // Progress polling runs while the blocking JNI call churns on Dispatchers.IO.
        analysisJob = lifecycleScope.launch {
            val poller = launch {
                while (isActive) {
                    val current = DemoAnalysis.progressCurrent()
                    val total = DemoAnalysis.progressTotal()
                    if (total > 0) {
                        progressBar.progress = (current * 1000L / total).toInt()
                        progressLabel.text = "$current / $total ticks"
                    }
                    delay(250)
                }
            }
            val outcome = runCatching {
                withContext(Dispatchers.IO) { runAnalysis(uri) }
            }
            poller.cancel()
            setProgressVisible(false)
            outcome
                .onSuccess { json ->
                    lastResult = json
                    showResult(json)
                    shareButton.isEnabled = true
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
        val tickCount = root.optInt("duration", 0)
        val seconds = tickCount / 66.67

        infoMap.text = root.optString("map", "?")
        infoAuthor.text = getString(R.string.author_line, root.optString("author", "?"))
        infoAuthor.tag = root.optString("author", "")
        infoServer.text = getString(R.string.server_line, root.optString("server_ip", "?"))
        infoServer.tag = root.optString("server_ip", "")
        demoInfoCard.visibility = View.VISIBLE

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
        // Per-algorithm counts, most frequent first — desktop shows the same breakdown.
        val counts = rows.groupingBy { it.algorithm }.eachCount().entries
            .sortedByDescending { it.value }
        infoCounts.text = if (rows.isEmpty()) {
            getString(R.string.no_detections)
        } else {
            counts.joinToString("\n") { (algorithm, count) -> "• $algorithm — $count" }
        }

        detectionsHeader.text = getString(R.string.detections_count, rows.size)
        detectionsHeader.visibility = View.VISIBLE
        detectionsList.visibility = View.VISIBLE
        detectionAdapter.submit(rows)
    }

    private fun hideResults() {
        demoInfoCard.visibility = View.GONE
        detectionsHeader.visibility = View.GONE
        detectionsList.visibility = View.GONE
    }

    private fun shareResult() {
        val json = lastResult ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TEXT, json)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_json)))
    }

    private fun setProgressVisible(visible: Boolean) {
        val visibility = if (visible) ProgressBar.VISIBLE else ProgressBar.GONE
        progressBar.visibility = visibility
        progressLabel.visibility = visibility
        analyzeButton.isEnabled = !visible && pickedUri != null
    }
}
