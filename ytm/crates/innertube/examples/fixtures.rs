//! Jam: dump real YouTube Music responses as the UI receives them, for designing the phone layout
//! offline (ui/mobile-lab). Anonymous; no account needed.
//!   cargo run -p innertube --example fixtures -- <out dir>
//! Writes one JSON file per command, plus thumbs.txt (every artwork URL seen).

use std::collections::BTreeSet;
use std::path::PathBuf;

use innertube::{Clients, InnerTube, Session, METADATA_CLIENT};
use serde::Serialize;

fn save<T: Serialize>(dir: &PathBuf, name: &str, v: &T, thumbs: &mut BTreeSet<String>) {
    let json = serde_json::to_value(v).unwrap();
    collect(&json, thumbs);
    std::fs::write(dir.join(format!("{name}.json")), serde_json::to_string_pretty(&json).unwrap()).unwrap();
    eprintln!("wrote {name}.json");
}

fn collect(v: &serde_json::Value, out: &mut BTreeSet<String>) {
    match v {
        serde_json::Value::String(s) if s.starts_with("https://") && (s.contains("googleusercontent") || s.contains("ytimg")) => {
            out.insert(s.clone());
        }
        serde_json::Value::Array(a) => a.iter().for_each(|x| collect(x, out)),
        serde_json::Value::Object(o) => o.values().for_each(|x| collect(x, out)),
        _ => {}
    }
}

fn first_id(v: &serde_json::Value, kind: &str) -> Option<String> {
    match v {
        serde_json::Value::Object(o) => {
            if o.get("kind").and_then(|k| k.as_str()) == Some(kind) {
                if let Some(id) = o.get("id").and_then(|i| i.as_str()) {
                    return Some(id.to_owned());
                }
            }
            o.values().find_map(|x| first_id(x, kind))
        }
        serde_json::Value::Array(a) => a.iter().find_map(|x| first_id(x, kind)),
        _ => None,
    }
}

#[tokio::main]
async fn main() {
    let dir = PathBuf::from(std::env::args().nth(1).unwrap_or_else(|| "fixtures".into()));
    std::fs::create_dir_all(&dir).unwrap();
    let it = InnerTube::new(Session::default(), None).unwrap();
    let vd = it.fetch_visitor_data().await.ok();
    let it = InnerTube::new(Session { visitor_data: vd, ..Session::default() }, None).unwrap();
    let clients = Clients::bundled();
    let c = clients.get(METADATA_CLIENT).unwrap();
    let mut thumbs = BTreeSet::new();

    let home = it.home(c, None).await.expect("home");
    save(&dir, "get_home", &home, &mut thumbs);
    if let Some(tok) = home.continuation.as_deref() {
        if let Ok(more) = it.home_continuation(c, tok).await {
            save(&dir, "get_home_more", &more, &mut thumbs);
        }
    }
    if let Ok(m) = it.moods(c).await {
        save(&dir, "get_moods", &m, &mut thumbs);
    }
    let search = it.search_all(c, "daft punk", false).await.expect("search");
    save(&dir, "search_all", &search, &mut thumbs);
    if let Ok(s) = it.search_suggestions(c, "daft").await {
        save(&dir, "search_suggestions", &s, &mut thumbs);
    }
    let sv = serde_json::to_value(&search).unwrap();
    let hv = serde_json::to_value(&home).unwrap();
    let album = first_id(&sv, "album").or_else(|| first_id(&hv, "album"));
    let artist = first_id(&sv, "artist").or_else(|| first_id(&hv, "artist"));
    let playlist = first_id(&hv, "playlist").or_else(|| first_id(&sv, "playlist"));
    let song = first_id(&sv, "song").or_else(|| first_id(&hv, "song"));
    if let Some(id) = album {
        if let Ok(p) = it.album(c, &id).await {
            save(&dir, "get_album", &p, &mut thumbs);
        }
    }
    if let Some(id) = artist {
        if let Ok(p) = it.artist(c, &id).await {
            save(&dir, "get_artist", &p, &mut thumbs);
        }
    }
    if let Some(id) = playlist {
        if let Ok(p) = it.playlist(c, &id, None).await {
            save(&dir, "get_playlist", &p, &mut thumbs);
        }
    }
    if let Some(id) = song {
        if let Ok(n) = it.next(c, Some(&id), Some(&format!("RDAMVM{id}"))).await {
            save(&dir, "next", &n, &mut thumbs);
        }
    }
    std::fs::write(dir.join("thumbs.txt"), thumbs.into_iter().collect::<Vec<_>>().join("\n")).unwrap();
}
