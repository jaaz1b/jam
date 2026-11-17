//! Jam for Windows' YouTube Music mode.
//!
//! Jam (the Windows app, Orpheon's port) starts this app with `JAM_COMPANION=http://127.0.0.1:<port>`
//! and `JAM_COMPANION_TOKEN=<secret>` and stays running behind it. Jam then does what Jam does in
//! every mode: its Discord presence (with the custom presence settings), the tray's Jam entries
//! (presence settings, back to Spotify mode). This app sends it what is playing; its own Discord
//! presence stays off.

use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::mpsc::{channel, Sender};
use std::sync::OnceLock;
use std::time::Duration;

use serde::Serialize;

struct Companion {
    host: String,
    token: String,
}

static COMPANION: OnceLock<Option<Companion>> = OnceLock::new();

fn companion() -> Option<&'static Companion> {
    COMPANION
        .get_or_init(|| {
            let url = std::env::var("JAM_COMPANION").ok()?;
            let host = url.strip_prefix("http://")?.trim_end_matches('/').to_string();
            if !host.starts_with("127.0.0.1:") {
                return None;
            }
            let token = std::env::var("JAM_COMPANION_TOKEN").ok().filter(|t| t.len() >= 16)?;
            Some(Companion { host, token })
        })
        .as_ref()
}

/// True when Jam started this app and runs behind it.
pub fn active() -> bool {
    companion().is_some()
}

/// One request to Jam (short, loopback). Errors are ignored: Jam may be closing.
pub fn post(path: &str, body: &str) {
    let Some(c) = companion() else { return };
    let Ok(mut s) = TcpStream::connect(&c.host) else { return };
    let _ = s.set_write_timeout(Some(Duration::from_secs(2)));
    let _ = s.set_read_timeout(Some(Duration::from_secs(2)));
    let req = format!(
        "POST {path} HTTP/1.1\r\nHost: {}\r\nX-Jam-Token: {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        c.host,
        c.token,
        body.len()
    );
    if s.write_all(req.as_bytes()).is_ok() {
        let mut sink = [0u8; 256];
        let _ = s.read(&mut sink);
    }
}

/// The tray's Jam entries, in a thread of their own (the request can wait on Jam).
pub fn action(path: &'static str) {
    std::thread::spawn(move || post(path, "{}"));
}

/// What Jam's `Player` reads: the same fields as Jam for Android's `nowPlaying` bridge.
#[derive(Serialize, Default, Clone, PartialEq)]
struct NowPlaying {
    title: String,
    artist: String,
    album: String,
    artwork: Option<String>,
    playing: bool,
    position: f64,
    duration: Option<f64>,
    #[serde(rename = "hasTrack")]
    has_track: bool,
}

pub enum Update {
    Metadata { title: String, artist: String, album: Option<String>, cover: Option<String> },
    Album(String),
    Duration(f64),
    Playback { playing: bool, pos: f64 },
}

static TX: OnceLock<Option<Sender<Update>>> = OnceLock::new();

/// Sends a change of what's playing to Jam (coalesced on a thread of its own).
pub fn update(u: Update) {
    let tx = TX.get_or_init(|| {
        if !active() {
            return None;
        }
        let (tx, rx) = channel::<Update>();
        std::thread::Builder::new()
            .name("jam-companion".into())
            .spawn(move || {
                let mut np = NowPlaying::default();
                let mut sent = NowPlaying::default();
                while let Ok(first) = rx.recv() {
                    apply(&mut np, first);
                    while let Ok(more) = rx.try_recv() {
                        apply(&mut np, more);
                    }
                    if np != sent {
                        if let Ok(body) = serde_json::to_string(&np) {
                            post("/now-playing", &body);
                        }
                        sent = np.clone();
                    }
                }
            })
            .ok()?;
        Some(tx)
    });
    if let Some(tx) = tx {
        let _ = tx.send(u);
    }
}

fn apply(np: &mut NowPlaying, u: Update) {
    match u {
        Update::Metadata { title, artist, album, cover } => {
            np.title = title;
            np.artist = artist;
            np.album = album.unwrap_or_default();
            np.artwork = cover.filter(|c| c.starts_with("https://"));
            np.duration = None;
            np.position = 0.0;
            np.has_track = true;
        }
        Update::Album(a) => np.album = a,
        Update::Duration(d) => np.duration = Some(d),
        Update::Playback { playing, pos } => {
            np.playing = playing;
            np.position = pos.max(0.0);
        }
    }
}
