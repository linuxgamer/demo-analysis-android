//! JNI bridge between the Android app and the demo-analysis library.
//!
//! The app owns all UI; this layer only translates a file descriptor plus a
//! JSON config into a JSON result, mirroring the JSON shape that the desktop
//! CLI's `print_detection_json` produces.

use std::fs::File;
use std::io::Read;
use std::os::unix::io::FromRawFd;
use std::panic::{self, AssertUnwindSafe};
use std::sync::atomic::{AtomicBool, Ordering};

use anyhow::{anyhow, Context};
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;

use demo_analysis::lib::algorithm::{
    analyse_multithreaded, apply_config, get_algorithms, normalize_config,
};
use demo_analysis::lib::parameters::Config;
use demo_analysis::{MAX_WORKERS, PROGRESS_CURRENT, PROGRESS_TOTAL, SILENT, WORKER_TICKS};

// Dev algorithms dump debug data into ./output and panic in init() when that
// fails, which would abort the whole scan on Android. They are never exposed
// to the app, even if requested by name.
const DEV_ALGORITHMS: &[&str] = &["all_messages", "write_to_file", "viewangles_to_csv"];

// Set by cancelAnalysis(); the progress callback panics when it sees true,
// which unwinds out of the parse loop and gets caught by the outer
// catch_unwind as a regular error - the upstream analyse() has no other way
// to abort mid-demo.
static CANCELLED: AtomicBool = AtomicBool::new(false);

fn check_cancelled() {
    if CANCELLED.load(Ordering::Relaxed) {
        panic!("analysis cancelled by user");
    }
}

fn panic_message(payload: &(dyn std::any::Any + Send)) -> String {
    payload
        .downcast_ref::<&str>()
        .map(|s| (*s).to_string())
        .or_else(|| payload.downcast_ref::<String>().cloned())
        .unwrap_or_else(|| "unknown panic".to_string())
}

fn throw(env: &mut JNIEnv, msg: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", msg);
}

#[no_mangle]
pub extern "system" fn Java_com_tf2demo_analyzer_DemoAnalysis_version<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    match env.new_string(concat!("demo-analysis-android ", env!("CARGO_PKG_VERSION"))) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            throw(&mut env, &e.to_string());
            std::ptr::null_mut()
        }
    }
}

// Full algorithm registry for the settings UI: name, default flag and the
// parameter schema. Dev algorithms are excluded; they make no sense on Android.
#[no_mangle]
pub extern "system" fn Java_com_tf2demo_analyzer_DemoAnalysis_algorithmsJsonRaw<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let mut algorithms = get_algorithms();
        let mut entries = Vec::with_capacity(algorithms.len());
        for algorithm in algorithms.iter_mut() {
            let name = algorithm.algorithm_name().to_string();
            if DEV_ALGORITHMS.contains(&name.as_str()) {
                continue;
            }
            let params = match algorithm.params() {
                Some(params) => serde_json::to_value(params)?,
                None => serde_json::Value::Null,
            };
            entries.push(serde_json::json!({
                "name": name,
                "default": algorithm.default(),
                "params": params,
            }));
        }
        Ok(env
            .new_string(serde_json::to_string(&entries)?)?
            .into_raw())
    })();
    match result {
        Ok(json) => json,
        Err(e) => {
            throw(&mut env, &format!("{e:#}"));
            std::ptr::null_mut()
        }
    }
}

// Blocking call: reads the whole demo from `fd` (ownership is transferred, the
// descriptor is closed before returning), runs the analysis on up to `threads`
// worker threads and returns the detections JSON. Panics inside the library
// are caught and surface as a Java RuntimeException instead of killing the app.
#[no_mangle]
pub extern "system" fn Java_com_tf2demo_analyzer_DemoAnalysis_analyse<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    fd: jint,
    algorithms: JObjectArray<'local>,
    config: JString<'local>,
    threads: jint,
) -> jstring {
    let result = run_analysis(&mut env, fd, &algorithms, &config, threads);
    match result {
        Ok(json) => json,
        Err(e) => {
            throw(&mut env, &format!("{e:#}"));
            std::ptr::null_mut()
        }
    }
}

fn run_analysis(
    env: &mut JNIEnv,
    fd: jint,
    algorithms_array: &JObjectArray,
    config_str: &JString,
    threads: jint,
) -> anyhow::Result<jstring> {
    // SAFETY: the app hands the descriptor over via detachFd(), so this side
    // owns it from here on. File closes it on drop, including on early returns,
    // so it must be adopted before anything can fail.
    let mut file = unsafe { File::from_raw_fd(fd) };

    let requested: Vec<String> = {
        let len = env.get_array_length(algorithms_array)?;
        (0..len)
            .map(|i| -> jni::errors::Result<String> {
                let element = env.get_object_array_element(algorithms_array, i)?;
                let name = JString::from(element);
                let java_str = env.get_string(&name)?;
                Ok(String::from(java_str))
            })
            .collect::<Result<Vec<_>, _>>()?
    };
    let config: Config = {
        let raw: String = env.get_string(config_str)?.into();
        serde_json::from_str(&raw).context("invalid config JSON")?
    };

    SILENT.store(true, Ordering::Relaxed);

    let mut bytes = Vec::new();
    file.read_to_end(&mut bytes).context("reading demo file")?;

    let json = panic::catch_unwind(AssertUnwindSafe(|| -> anyhow::Result<String> {
        let mut algorithms = get_algorithms();
        algorithms.retain(|a| !DEV_ALGORITHMS.contains(&a.algorithm_name()));
        if requested.is_empty() {
            algorithms.retain(|a| a.default());
        } else {
            algorithms.retain(|a| requested.iter().any(|n| n == a.algorithm_name()));
        }
        if algorithms.is_empty() {
            return Err(anyhow!("no algorithms selected"));
        }

        let (normalized, _) = normalize_config(&config);
        apply_config(&mut algorithms, &normalized);

        let analyser = match analyse_multithreaded(
            &bytes,
            algorithms,
            threads.max(1) as usize,
            |_worker, _current, _total| check_cancelled(),
        ) {
            Ok(analyser) => analyser,
            // In multithreaded mode a worker panic surfaces as this generic
            // join error; if a cancel was requested, report that instead.
            Err(e) => {
                check_cancelled();
                return Err(e);
            }
        };
        check_cancelled();

        // The header carries only the author's nick; expose their SteamID64
        // when exactly one known player has that nick.
        let author_steamid = analyser.header.as_ref().and_then(|header| {
            let matches: Vec<u64> = analyser
                .state
                .player_names
                .iter()
                .filter(|(_, name)| *name == &header.nick)
                .map(|(id, _)| *id)
                .collect();
            (matches.len() == 1).then_some(matches[0])
        });

        let analysis = serde_json::json!({
            "server_ip": analyser.header.as_ref().map_or("unknown".to_string(), |h| h.server.clone()),
            "duration": u32::from(analyser.tick),
            "author": analyser.header.as_ref().map_or("unknown".to_string(), |h| h.nick.clone()),
            "map": analyser.header.as_ref().map_or("unknown".to_string(), |h| h.map.clone()),
            "author_steamid": author_steamid,
            "players": analyser.state.player_names,
            "detections": analyser.detections,
        });
        Ok(serde_json::to_string(&analysis)?)
    }))
    .map_err(|payload| anyhow!("analysis panicked: {}", panic_message(payload.as_ref())))??;

    Ok(env.new_string(&json)?.into_raw())
}

#[no_mangle]
pub extern "system" fn Java_com_tf2demo_analyzer_DemoAnalysis_progressCurrent<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    PROGRESS_CURRENT.load(Ordering::Relaxed) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_tf2demo_analyzer_DemoAnalysis_progressTotal<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    PROGRESS_TOTAL.load(Ordering::Relaxed) as jint
}

// Atomics persist across runs; reset them so a fresh scan starts from zero.
#[no_mangle]
pub extern "system" fn Java_com_tf2demo_analyzer_DemoAnalysis_resetProgress<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    PROGRESS_CURRENT.store(0, Ordering::Relaxed);
    PROGRESS_TOTAL.store(0, Ordering::Relaxed);
    CANCELLED.store(false, Ordering::Relaxed);
    for slot in WORKER_TICKS.iter().take(MAX_WORKERS) {
        slot.store(0, Ordering::Relaxed);
    }
}

// Aborts a running analysis: the progress callback panics on the next tick
// and the JNI entry point reports it as a normal error.
#[no_mangle]
pub extern "system" fn Java_com_tf2demo_analyzer_DemoAnalysis_cancelAnalysis<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    CANCELLED.store(true, Ordering::Relaxed);
}
