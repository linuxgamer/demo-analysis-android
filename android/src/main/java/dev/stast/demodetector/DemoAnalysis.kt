package dev.stast.demodetector

/**
 * Thin wrapper over the Rust JNI bridge. All methods are blocking and must be
 * called from a background dispatcher.
 */
object DemoAnalysis {
    init {
        System.loadLibrary("demo_analysis_android")
    }

    external fun version(): String

    /**
     * Runs the analysis. `fd` is a detached file descriptor for the demo file;
     * its ownership transfers to the Rust side, which closes it when done.
     *
     * @param algorithms algorithm names to run; empty means "all default ones"
     * @param config JSON object mapping algorithm names to parameter overrides
     * @param threads worker count (each re-reads the demo); 1 keeps it serial
     * @return detections JSON matching the desktop CLI's output shape
     */
    external fun analyse(fd: Int, algorithms: Array<String>, config: String, threads: Int): String

    external fun algorithmsJson(): String
    external fun progressCurrent(): Int
    external fun progressTotal(): Int
    external fun resetProgress()
}
