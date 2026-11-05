//! Jam for Android: OS media integration. Desktop publishes to MPRIS/SMTC through `souvlaki`; on
//! Android the media notification, lock screen, Bluetooth/headset buttons and Discord are Jam's
//! (`PlaybackService.kt`, `Discord.kt`), so this sends what's playing to Kotlin and routes the
//! notification's buttons back into the same [`AppState`] methods the UI uses.
//!
//! Same shape as the desktop module: a cheap handle that posts to one owner thread, which coalesces
//! a burst of updates into the latest state before it crosses JNI.

use std::sync::mpsc::{channel, Sender};
use std::sync::{Arc, OnceLock};

use serde::Serialize;
use tauri::ipc::{Channel, InvokeResponseBody};
use tauri::{AppHandle, Manager};

use crate::state::AppState;

enum MediaUpdate {
    Metadata { title: String, artist: String, album: Option<String>, cover: Option<String> },
    Album(String),
    Duration(f64),
    Playback { playing: bool, pos: f64 },
    VideoId(String),
}

pub struct MediaHandle {
    tx: Sender<MediaUpdate>,
}

impl MediaHandle {
    pub fn set_metadata(&self, title: &str, artist: &str, album: Option<&str>, cover: Option<&str>) {
        let _ = self.tx.send(MediaUpdate::Metadata {
            title: title.to_owned(),
            artist: artist.to_owned(),
            album: album.map(str::to_owned),
            cover: cover.map(str::to_owned),
        });
    }

    pub fn set_album(&self, album: &str) {
        let _ = self.tx.send(MediaUpdate::Album(album.to_owned()));
    }

    /// Listening seconds, like the desktop module and the UI.
    pub fn set_duration(&self, secs: f64, speed: f64) {
        let _ = self.tx.send(MediaUpdate::Duration(secs / speed));
    }

    pub fn set_playback(&self, playing: bool, pos: f64, speed: f64) {
        let _ = self.tx.send(MediaUpdate::Playback { playing, pos: pos / speed });
    }

    /// Android's media volume is the system's; the app's own slider stays inside the app.
    pub fn set_volume(&self, _volume: i64) {}

    /// The track's id, for the "Listen on YouTube Music" link Discord shows.
    pub fn set_video_id(&self, id: &str) {
        let _ = self.tx.send(MediaUpdate::VideoId(id.to_owned()));
    }
}

/// What Kotlin's `Player.report` reads (same fields as Jam's web sources report).
#[derive(Serialize, Default, Clone, PartialEq)]
struct NowPlaying {
    title: String,
    artist: String,
    album: String,
    artwork: Option<String>,
    playing: bool,
    position: f64,
    duration: Option<f64>,
    url: String,
    #[serde(rename = "hasTrack")]
    has_track: bool,
}

pub fn spawn(app: AppHandle) -> Option<MediaHandle> {
    register_controls(&app);
    let (tx, rx) = channel::<MediaUpdate>();
    let spawned = std::thread::Builder::new().name("media-controls".into()).spawn(move || {
        let mut np = NowPlaying::default();
        let mut sent = NowPlaying::default();
        while let Ok(first) = rx.recv() {
            apply(&mut np, first);
            // Coalesce whatever else is already queued: only the latest state crosses JNI.
            while let Ok(more) = rx.try_recv() {
                apply(&mut np, more);
            }
            if np != sent {
                if let Err(e) = crate::android::call_blocking::<serde_json::Value>(&app, "nowPlaying", &np) {
                    tracing::debug!(error = %e, "could not update the media notification");
                }
                sent = np.clone();
            }
        }
    });
    match spawned {
        Ok(_) => Some(MediaHandle { tx }),
        Err(e) => {
            tracing::warn!(error = %e, "media-controls thread spawn failed");
            None
        }
    }
}

fn apply(np: &mut NowPlaying, u: MediaUpdate) {
    match u {
        MediaUpdate::Metadata { title, artist, album, cover } => {
            np.title = title;
            np.artist = artist;
            np.album = album.unwrap_or_default();
            np.artwork = cover;
            np.duration = None;
            np.position = 0.0;
            np.has_track = true;
        }
        MediaUpdate::Album(a) => np.album = a,
        MediaUpdate::Duration(d) => np.duration = Some(d),
        MediaUpdate::Playback { playing, pos } => {
            np.playing = playing;
            np.position = pos.max(0.0);
        }
        MediaUpdate::VideoId(id) => {
            np.url = if crate::local::is_local_song(&id) {
                String::new()
            } else {
                format!("https://music.youtube.com/watch?v={id}")
            };
        }
    }
}

/// Hand Kotlin a channel for the notification's buttons. Each message is `{action, position?}`.
fn register_controls(app: &AppHandle) {
    let cb_app = app.clone();
    let channel: Channel<serde_json::Value> = Channel::new(move |body: InvokeResponseBody| {
        if let Ok(msg) = body.deserialize::<serde_json::Value>() {
            let action = msg.get("action").and_then(|v| v.as_str()).unwrap_or_default().to_owned();
            let position = msg.get("position").and_then(|v| v.as_f64());
            control(&cb_app, action, position);
        }
        Ok(())
    });
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        if let Err(e) = crate::android::call::<serde_json::Value>(
            &app,
            "registerControls",
            serde_json::json!({ "channel": channel }),
        )
        .await
        {
            tracing::warn!(error = %e, "media buttons are not connected");
        }
    });
}

/// A notification / lock-screen / headset button. Same paths as desktop's `handle_event`.
fn control(app: &AppHandle, action: String, position: Option<f64>) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let Some(state) = app.try_state::<Arc<AppState>>() else { return };
        let state = state.inner().clone();
        match action.as_str() {
            "play" => {
                if !state.player.is_idle() {
                    let _ = state.player.play();
                } else {
                    state.resume_or_toggle().await;
                }
            }
            "toggle" => state.resume_or_toggle().await,
            "pause" | "stop" => {
                let _ = state.player.pause();
            }
            "next" | "nexttrack" => state.next_in_queue().await,
            "previous" | "previoustrack" => state.prev_in_queue().await,
            "seekto" => {
                if let Some(p) = position {
                    let _ = state.user_seek(p.max(0.0) * state.player.speed()).await;
                }
            }
            other => tracing::debug!(action = other, "unknown media button"),
        }
    });
}

/// Same as desktop: a cover big enough for a notification or Discord card.
pub(crate) fn cover_url(url: &str) -> String {
    static WH: OnceLock<regex::Regex> = OnceLock::new();
    static S: OnceLock<regex::Regex> = OnceLock::new();
    let wh = WH.get_or_init(|| regex::Regex::new(r"=w\d+-h\d+").expect("static regex"));
    let s = S.get_or_init(|| regex::Regex::new(r"=s\d+").expect("static regex"));
    if wh.is_match(url) {
        wh.replace(url, "=w512-h512").into_owned()
    } else if s.is_match(url) {
        s.replace(url, "=s512").into_owned()
    } else {
        url.to_owned()
    }
}
