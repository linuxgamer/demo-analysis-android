package dev.stast.demodetector

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var infoDetails: TextView
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

        fileName = findViewById(R.id.fileName)
        analyzeButton = findViewById(R.id.analyzeButton)
        shareButton = findViewById(R.id.shareButton)
        progressLabel = findViewById(R.id.progressLabel)
        progressBar = findViewById(R.id.progressBar)
        status = findViewById(R.id.status)
        demoInfoCard = findViewById(R.id.demoInfoCard)
        infoMap = findViewById(R.id.infoMap)
        infoDetails = findViewById(R.id.infoDetails)
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
        val config = JSONObject().toString()
        return DemoAnalysis.analyse(fd, arrayOf<String>(), config, threads = 2)
    }

    private fun showResult(json: String) {
        val root = JSONObject(json)
        val detections = root.optJSONArray("detections")
        val tickCount = root.optInt("duration", 0)
        val seconds = tickCount / 66.67

        infoMap.text = root.optString("map", "?")
        infoDetails.text = getString(
            R.string.demo_details,
            formatDuration(seconds),
            root.optString("author", "?"),
            root.optString("server_ip", "?"),
        )
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
        detectionAdapter.submitList(rows)
    }

    private fun formatDuration(seconds: Double): String {
        val total = seconds.toInt()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
        else "%d:%02d".format(minutes, secs)
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
