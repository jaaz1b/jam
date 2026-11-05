//! Jam for Android: no floating mini player window. Android's own media notification and the
//! picture of the player in Jam's bottom bar do that job; the UI hides the button.

use tauri::AppHandle;

pub const LABEL: &str = "mini";

pub fn open(_app: &AppHandle) -> Result<(), String> {
    Err("The mini player isn't available on Android".into())
}

pub fn set_compact(_app: &AppHandle, _compact: bool) -> Result<(), String> {
    Ok(())
}

pub fn save_position(_app: &AppHandle) {}

pub fn close(_app: &AppHandle) {}
