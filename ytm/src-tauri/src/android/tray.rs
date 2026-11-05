//! Jam for Android: there is no tray. The notification and the launcher are the way back, and
//! Android owns them, so every entry point here is a no-op the desktop callers can keep calling.

use tauri::{AppHandle, Emitter};

pub fn available() -> bool {
    false
}

pub fn set_available(_val: bool) {}

pub fn show_main(_app: &AppHandle) {}

/// Still useful on Android: Jam tells the SPA when it is out of sight (`ui-visible`), so it can
/// stop restyling for nobody. See the desktop module for why the SPA cannot work this out alone.
pub fn set_main_visible(app: &AppHandle, visible: bool) {
    let _ = app.emit_to("main", "ui-visible", visible);
}

pub fn init(_app: &AppHandle) -> tauri::Result<()> {
    Ok(())
}

pub fn set_playing(_app: &AppHandle, _playing: bool) {}

pub fn set_icon(_app: &AppHandle, _icon: &tauri::image::Image<'_>) {}
