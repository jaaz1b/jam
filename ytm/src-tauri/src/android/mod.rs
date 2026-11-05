//! Jam for Android: the engine's bridge to the Android app around it (`JamPlugin.kt`).
//!
//! On desktop the engine owns its windows: a hidden cipher webview, the Google sign-in window, OS
//! media controls, the tray. Android has one activity and one webview per Tauri app, and the media
//! notification, Discord and the lock screen belong to Jam's Android code. So those pieces call
//! into Kotlin through one Tauri mobile plugin, `jam`:
//!
//! - `webview.rs` (Android): hidden WebViews that run YouTube's player.js for the cipher.
//! - `session.rs`: Google sign-in and session refresh in an Android WebView.
//! - `media.rs` (Android): what's playing → Jam's notification, lock screen and Discord; and the
//!   notification's buttons → the same `AppState` methods the UI uses.
//!
//! Everything degrades the way the desktop code does when its platform piece is missing: a failed
//! call is logged and the feature is skipped, never a crash.

use serde::de::DeserializeOwned;
use serde::Serialize;
use tauri::plugin::{Builder, PluginHandle, TauriPlugin};
use tauri::{AppHandle, Manager, Wry};

/// The plugin handle, managed as app state once the plugin is registered.
pub struct Jam(pub PluginHandle<Wry>);

pub fn init() -> TauriPlugin<Wry> {
    Builder::new("jam")
        .setup(|app, api| {
            let handle = api.register_android_plugin("app.jam.android", "JamPlugin")?;
            app.manage(Jam(handle));
            Ok(())
        })
        .build()
}

/// Call a `JamPlugin` command and wait for its answer.
///
/// Safe to cancel (a `timeout` around it, a dropped future): the call itself runs in its own task.
/// Tauri's async plugin call unwraps the send of Kotlin's reply, so a reply arriving after its
/// caller gave up panics on the main thread, where nothing can unwind, and the app dies. Kept
/// alive in a task, the receiver is still there when the late reply comes.
pub async fn call<T: DeserializeOwned + Send + 'static>(
    app: &AppHandle,
    command: &str,
    args: impl Serialize,
) -> Result<T, String> {
    let jam = app.try_state::<Jam>().ok_or("the Android bridge is not registered")?;
    let handle = jam.0.clone();
    let command = command.to_owned();
    let args = serde_json::to_value(args).map_err(|e| e.to_string())?;
    tauri::async_runtime::spawn(async move {
        handle.run_mobile_plugin_async::<T>(command, args).await.map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| e.to_string())?
}

/// Same, blocking the calling thread. For the owner threads that mirror desktop's design.
pub fn call_blocking<T: DeserializeOwned>(
    app: &AppHandle,
    command: &str,
    args: impl Serialize,
) -> Result<T, String> {
    let jam = app.try_state::<Jam>().ok_or("the Android bridge is not registered")?;
    jam.0.run_mobile_plugin::<T>(command, args).map_err(|e| e.to_string())
}

/// Open a web page in the user's browser (a Custom Tab), for Last.fm's approval page and links.
pub fn open_browser(app: &AppHandle, url: &str) -> Result<(), String> {
    let app = app.clone();
    let url = url.to_owned();
    tauri::async_runtime::spawn(async move {
        if let Err(e) = call::<serde_json::Value>(&app, "openExternal", serde_json::json!({ "url": url })).await {
            tracing::warn!(error = %e, "could not open the browser");
        }
    });
    Ok(())
}

/// Hands the process's JavaVM and the application context to `ndk-context`, where the player
/// crate reads them for FFmpeg (mpv's AudioTrack output). tao has them but, unlike android-activity,
/// doesn't publish them there. Call from setup, before the player starts. Once per process.
pub fn init_jvm() {
    static ONCE: std::sync::Once = std::sync::Once::new();
    ONCE.call_once(|| {
        let Some(ctx) = tao::platform::android::prelude::main_android_context() else {
            tracing::error!("no Android context yet; audio output may not start");
            return;
        };
        let result = (|| -> jni::errors::Result<*mut std::ffi::c_void> {
            // SAFETY: tao's pointers: the process JavaVM and a global ref to the activity.
            let vm = unsafe { jni::JavaVM::from_raw(ctx.java_vm.cast()) }?;
            let mut env = vm.attach_current_thread()?;
            let activity = unsafe { jni::objects::JObject::from_raw(ctx.context_jobject.cast()) };
            let app = env
                .call_method(&activity, "getApplicationContext", "()Landroid/content/Context;", &[])?
                .l()?;
            // The application context outlives any activity; FFmpeg keeps it for the process.
            let global = env.new_global_ref(app)?;
            let raw = global.as_obj().as_raw() as *mut std::ffi::c_void;
            std::mem::forget(global);
            Ok(raw)
        })();
        match result {
            // SAFETY: called once, with the live JavaVM and a global ref that is never deleted.
            Ok(app_ctx) => unsafe { ndk_context::initialize_android_context(ctx.java_vm, app_ctx) },
            Err(e) => tracing::error!(error = %e, "could not get the application context"),
        }
    });
}

/// The app handle, for the few places (Last.fm's browser hop) that have none to pass down.
pub fn set_app(app: &AppHandle) {
    let _ = APP.set(app.clone());
}

pub fn app() -> Option<&'static AppHandle> {
    APP.get()
}

static APP: std::sync::OnceLock<AppHandle> = std::sync::OnceLock::new();

/// Runs before the SPA in the YouTube Music webview: tells it it is Jam on a phone. `__jam` is the
/// flag the rebranded UI already reads to hide what Jam itself provides (the Discord tab);
/// `__JAM_ANDROID__` switches on the phone layout.
pub const INIT_SCRIPT: &str = r#"(function () {
  window.__JAM_ANDROID__ = true;
  function mark() {
    var i = window.__TAURI_INTERNALS__;
    if (i) i.__jam = true;
    if (document.documentElement) document.documentElement.classList.add('jam-android');
  }
  mark();
  document.addEventListener('DOMContentLoaded', mark);
})();"#;
