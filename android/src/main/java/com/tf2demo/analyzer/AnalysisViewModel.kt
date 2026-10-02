package com.tf2demo.analyzer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Owns the analysis run so it survives activity recreation: the blocking JNI
 * call and the progress poller live in `viewModelScope`, and the UI simply
 * re-renders whatever phase the VM reports after a rotation.
 */
class AnalysisViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Phase {
        object Idle : Phase

        /** `ticks`/`total` come from the polled atomics; may jump between workers. */
        data class Running(val ticks: Int, val total: Int) : Phase

        class Done(val json: String) : Phase

        class Failed(val message: String, val cancelled: Boolean) : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /** Last demo the user picked; kept across rotations, lost on process death. */
    var pickedUri: Uri? = null
        private set

    private var job: Job? = null

    fun start(uri: Uri) {
        if (_phase.value is Phase.Running) return
        pickedUri = uri
        job?.cancel()
        job = viewModelScope.launch {
            _phase.value = Phase.Running(0, 0)
            DemoAnalysis.resetProgress()
            val poller = launch {
                var shown = 0
                while (isActive && _phase.value is Phase.Running) {
                    val current = DemoAnalysis.progressCurrent()
                    val total = DemoAnalysis.progressTotal()
                    if (total > 0) {
                        // Monotonic: both workers write the same counter, so
                        // raw polling jumps between their positions.
                        shown = maxOf(shown, current)
                        _phase.value = Phase.Running(shown, total)
                    }
                    delay(250)
                }
            }
            val outcome = runCatching {
                withContext(Dispatchers.IO) { runAnalysis(uri) }
            }
            poller.cancel()
            outcome.fold(
                onSuccess = { json -> _phase.value = Phase.Done(json) },
                onFailure = { e ->
                    val cancelled = e.message?.contains("cancelled by user") == true
                    _phase.value = Phase.Failed(e.message ?: "unknown error", cancelled)
                },
            )
        }
    }

    fun cancel() {
        if (_phase.value is Phase.Running) DemoAnalysis.cancelAnalysis()
    }

    private fun runAnalysis(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val pfd = resolver.openFileDescriptor(uri, "r")
            ?: throw IOException("cannot open $uri")
        // The Rust side adopts the fd as its very first step and closes it on
        // every path; detach here so neither the PFD finalizer nor we close it
        // a second time.
        val fd = pfd.detachFd()
        // Enabled set and parameter overrides from the settings screen; the
        // Rust side normalizes the config (drops unknown entries, coerces
        // number kinds) exactly like the desktop analyser does.
        val context = getApplication<Application>()
        val state = SettingsStore.load(context, SettingsStore.schema(DemoAnalysis.algorithmsJson()))
        val enabled = state.enabled.filterValues { it }.keys.toTypedArray()
        val config = SettingsStore.paramsJson(state)
        return DemoAnalysis.analyse(fd, enabled, config, threads = SettingsStore.threads(context))
    }
}
