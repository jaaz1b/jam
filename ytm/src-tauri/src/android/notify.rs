//! Jam for Android: no separate track-change toast. The media notification already changes with
//! the track, and a second notification per song would be noise.

use tauri::AppHandle;

pub fn track_changed(_app: &AppHandle, _title: &str, _artists: &str, _even_focused: bool) {}
