//! Jam host mode.
//!
//! In Jam, this engine runs headless behind the Jam desktop app (Electron). Jam starts it with
//! `JAM_HOST=ws://127.0.0.1:<port>` and `JAM_TOKEN=<secret>`, and shows this app's own UI inside
//! its window. The engine's main window stays hidden and loads `jam-relay.html` instead of the UI:
//! a small page that connects back to Jam and relays every `invoke` / event between the UI hosted
//! in Jam and this process. Commands, events, permissions and state are therefore exactly the ones
//! the UI already uses; nothing in the command layer knows it is being driven remotely.
//!
//! What host mode changes, and why:
//! - the main window is never shown (Jam's window is the window);
//! - no tray icon and no taskbar preview buttons (Jam has its own);
//! - no Discord presence of its own (Jam builds one presence for every source);
//! - window chrome is reported as native (`on`), so the UI hides its own window buttons and
//!   resize borders: Jam draws the title bar;
//! - music video falls back to the `<video>` element (the native path draws into the hidden
//!   window, where nobody would see it).

use std::sync::OnceLock;

struct Host {
    url: String,
    token: String,
}

static HOST: OnceLock<Option<Host>> = OnceLock::new();

fn host() -> Option<&'static Host> {
    HOST.get_or_init(|| {
        let url = std::env::var("JAM_HOST").ok().filter(|v| v.starts_with("ws://127.0.0.1:"))?;
        let token = std::env::var("JAM_TOKEN").ok().filter(|v| v.len() >= 16)?;
        Some(Host { url, token })
    })
    .as_ref()
}

/// True when running inside Jam.
pub fn active() -> bool {
    host().is_some()
}

/// The page the hidden main window loads in host mode (ui/static/jam-relay.html).
pub const RELAY_PAGE: &str = "jam-relay.html";

/// Runs before the relay page's own script: where to connect, and the secret proving it's us.
pub fn init_script() -> String {
    match host() {
        Some(h) => format!(
            "window.__JAM_HOST__ = {}; window.__JAM_TOKEN__ = {};",
            serde_json::to_string(&h.url).unwrap_or_default(),
            serde_json::to_string(&h.token).unwrap_or_default()
        ),
        None => String::new(),
    }
}
