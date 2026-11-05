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
pub async fn call<T: DeserializeOwned>(
    app: &AppHandle,
    command: &str,
    args: impl Serialize,
) -> Result<T, String> {
    let jam = app.try_state::<Jam>().ok_or("the Android bridge is not registered")?;
    jam.0.run_mobile_plugin_async::<T>(command, args).await.map_err(|e| e.to_string())
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
