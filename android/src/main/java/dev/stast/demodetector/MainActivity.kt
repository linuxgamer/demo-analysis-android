package dev.stast.demodetector

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
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

    private val pickDemo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pickedUri = uri
                pickedName = queryDisplayName(uri)
                fileName.text = pickedName ?: uri.lastPathSegment
                analyzeButton.isEnabled = true
                status.text = ""
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
                    status.text = summarize(json)
                    shareButton.isEnabled = true
                }
                .onFailure { e ->
                    status.text = "Failed: ${e.message}"
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

    private fun summarize(json: String): String {
        val root = JSONObject(json)
        val detections = root.optJSONArray("detections") ?: return "0 detections"
        val byAlgorithm = linkedMapOf<String, Int>()
        for (i in 0 until detections.length()) {
            val algorithm = detections.getJSONObject(i).getString("algorithm")
            byAlgorithm[algorithm] = (byAlgorithm[algorithm] ?: 0) + 1
        }
        val map = root.optString("map", "?")
        val summary = buildString {
            appendLine("Map: $map — ${detections.length()} detections")
            byAlgorithm.forEach { (algorithm, count) -> appendLine("  $algorithm: $count") }
        }
        return summary.trimEnd()
    }

    private fun shareResult() {
        val json = lastResult ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TEXT, json)
        }
        startActivity(android.content.Intent.createChooser(intent, "Detections JSON"))
    }

    private fun setProgressVisible(visible: Boolean) {
        val visibility = if (visible) ProgressBar.VISIBLE else ProgressBar.GONE
        progressBar.visibility = visibility
        progressLabel.visibility = visibility
        analyzeButton.isEnabled = !visible && pickedUri != null
    }
}
