//! Downloads for offline listening. Jam for Android first (2.1); the desktop UI gets it in the next
//! desktop release (desktop/TODO.md), which is why none of this is Android-only.
//!
//! Modeled on Meld's (Metrolist's) `DownloadUtil`: a download is the stream the player would use,
//! resolved at the quality chosen for downloads rather than for streaming, saved whole, and played
//! from disk from then on, with or without a connection. What carries over:
//!
//! - at most [`MAX_PARALLEL`] at once, the rest queued in order;
//! - a song counts as downloaded only once every byte is on disk (`status = 'done'`), never before;
//! - an expired or refused URL (403 / 410 / 416) is resolved again and the download carries on;
//! - the artwork is saved next to the audio, and the lyrics land in their cache, so the song looks
//!   and reads the same offline;
//! - Cancel all drops everything that hasn't finished.
//!
//! What differs: ExoPlayer's cache is a pile of spans, and this is one file per song, fetched in
//! bounded ranges (googlevideo throttles an open-ended GET to about twice realtime, see
//! audioproxy.rs) into `<id>.part`, which a restart resumes from where it stopped.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock, RwLock};
use std::time::Duration;

use innertube::{AudioQuality, SongItem};
use tauri::{AppHandle, Emitter, Manager};
use tokio::io::AsyncWriteExt;

use crate::db::now_secs;
use crate::orchestrator::PlaybackData;
use crate::state::AppState;

/// Downloads running at once (Meld's `maxParallelDownloads`).
const MAX_PARALLEL: usize = 3;
/// One upstream range. Small enough that a cancel or a dead connection is noticed quickly, big
/// enough that the per-request round trip is noise.
const CHUNK: u64 = 2 * 1024 * 1024;
/// No byte for this long is a dead connection, not a slow one.
const STALL: Duration = Duration::from_secs(25);
/// Fresh URLs one download may ask for before it gives up.
const MAX_RESOLVES: u32 = 4;
/// Network errors in a row one download rides out (with backoff) before it gives up.
const MAX_RETRIES: u32 = 6;

/// The setting, and what the UI shows for each value.
pub const QUALITY_SETTING: &str = "download_quality";

fn quality(db: &crate::db::Db) -> (AudioQuality, &'static str) {
    match db.get_setting(QUALITY_SETTING).as_deref() {
        Some("NORMAL") => (AudioQuality::Low, "NORMAL"),
        Some("LOW") => (AudioQuality::Smallest, "LOW"),
        _ => (AudioQuality::High, "HIGH"),
    }
}

/// A finished download, as playback needs it.
#[derive(Clone)]
struct Offline {
    path: PathBuf,
    cover: Option<PathBuf>,
    loudness_db: Option<f64>,
}

/// One download in flight (queued or running).
struct Job {
    title: String,
    cancel: AtomicBool,
    got: AtomicU64,
    total: AtomicU64,
}

struct Downloads {
    app: AppHandle,
    dir: PathBuf,
    slots: Arc<tokio::sync::Semaphore>,
    jobs: Mutex<HashMap<String, Arc<Job>>>,
    done: RwLock<HashMap<String, Offline>>,
    ticking: AtomicBool,
    /// Finished and failed since the queue was last empty, for the notification's summary.
    finished: AtomicU64,
    failed: AtomicU64,
}

static DL: OnceLock<Downloads> = OnceLock::new();

/// The folder the audio and artwork go in: app data, beside the database, not the cache (Android
/// may clear a cache dir under storage pressure, and these are the user's).
pub fn dir(app: &AppHandle) -> PathBuf {
    app.path().app_data_dir().unwrap_or_else(|_| std::env::temp_dir()).join("downloads")
}

/// Load what's on disk and pick up whatever was still queued when the app last stopped. Call once
/// from setup, after `AppState` is managed.
pub fn init(state: &Arc<AppState>) {
    let app = state.app.clone();
    let dir = dir(&app);
    let _ = std::fs::create_dir_all(&dir);
    // The artwork reaches the webview over the asset protocol, like local music's (local.rs).
    let _ = app.asset_protocol_scope().allow_directory(&dir, true);
    if let Ok(real) = dir.canonicalize() {
        let _ = app.asset_protocol_scope().allow_directory(real, true);
    }
    let dl = DL.get_or_init(|| Downloads {
        app: app.clone(),
        dir,
        slots: Arc::new(tokio::sync::Semaphore::new(MAX_PARALLEL)),
        jobs: Mutex::new(HashMap::new()),
        done: RwLock::new(HashMap::new()),
        ticking: AtomicBool::new(false),
        finished: AtomicU64::new(0),
        failed: AtomicU64::new(0),
    });
    let mut resume = Vec::new();
    let mut done = HashMap::new();
    for row in state.db.download_rows() {
        match row.status.as_str() {
            "done" => match row.path.as_deref().map(PathBuf::from).filter(|p| p.is_file()) {
                Some(path) => {
                    done.insert(
                        row.video_id.clone(),
                        Offline {
                            path,
                            cover: row.cover.as_deref().map(PathBuf::from).filter(|p| p.is_file()),
                            loudness_db: row.loudness_db,
                        },
                    );
                }
                // Deleted behind our back (a cleaner app, a restore without the files).
                None => state.db.delete_download(&row.video_id),
            },
            "queued" | "downloading" => {
                if let Ok(song) = serde_json::from_str::<SongItem>(&row.song_json) {
                    resume.push(song);
                }
            }
            _ => {}
        }
    }
    *dl.done.write().unwrap() = done;
    tracing::info!(downloaded = dl.done.read().unwrap().len(), resuming = resume.len(), "downloads");
    // Oldest first, the order they were asked for.
    for song in resume.into_iter().rev() {
        spawn(state.clone(), song);
    }
}

/// The downloaded file for `video_id`, if there is one. Playback calls this before anything else.
pub fn playable(video_id: &str) -> Option<PlaybackData> {
    let dl = DL.get()?;
    let off = dl.done.read().ok()?.get(video_id).cloned()?;
    if !off.path.is_file() {
        dl.done.write().ok()?.remove(video_id);
        return None;
    }
    Some(PlaybackData {
        video_id: video_id.to_owned(),
        stream_url: off.path.to_string_lossy().into_owned(),
        itag: 0,
        headers: HashMap::new(),
        expires_in_seconds: i64::MAX / 2,
        loudness_db: off.loudness_db,
        playback_ping: None,
        title: None,
        artists: None,
        duration: None,
        thumbnail: None,
        is_video: None,
        is_episode: None,
        stream_client: "download".to_owned(),
    })
}

pub fn is_downloaded(video_id: &str) -> bool {
    DL.get().is_some_and(|dl| dl.done.read().is_ok_and(|d| d.contains_key(video_id)))
}

/// The saved artwork for a downloaded song, for the media notification when offline.
pub fn cover(video_id: &str) -> Option<String> {
    let dl = DL.get()?;
    let d = dl.done.read().ok()?;
    d.get(video_id)?.cover.as_ref().map(|p| p.to_string_lossy().into_owned())
}

/// What the Downloads page shows for one song.
#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadInfo {
    pub video_id: String,
    pub song: SongItem,
    pub status: String,
    /// 0..1 while downloading; 1 when done.
    pub progress: f64,
    pub size: i64,
    pub quality: String,
    pub error: Option<String>,
    pub added_at: i64,
    pub done_at: Option<i64>,
    /// The saved artwork, a path. The UI shows it in place of the song's own thumbnail.
    pub cover: Option<String>,
}

pub fn list(state: &AppState) -> Vec<DownloadInfo> {
    let progress = progress_map();
    state
        .db
        .download_rows()
        .into_iter()
        .filter_map(|r| {
            let song = serde_json::from_str::<SongItem>(&r.song_json).ok()?;
            let p = if r.status == "done" { 1.0 } else { progress.get(&r.video_id).copied().unwrap_or(0.0) };
            Some(DownloadInfo {
                video_id: r.video_id,
                song,
                status: r.status,
                progress: p,
                size: r.size,
                quality: r.quality,
                error: r.error,
                added_at: r.added_at,
                done_at: r.done_at,
                cover: r.cover,
            })
        })
        .collect()
}

/// Queue songs for download. Files on disk, songs already downloaded or on their way are skipped.
/// Returns how many were queued.
pub fn enqueue(state: &Arc<AppState>, songs: Vec<SongItem>) -> usize {
    let Some(dl) = DL.get() else { return 0 };
    let now = now_secs();
    let mut n = 0;
    for song in songs {
        let id = song.video_id.clone();
        if id.is_empty() || crate::local::is_local_song(&id) || is_downloaded(&id) {
            continue;
        }
        if dl.jobs.lock().unwrap().contains_key(&id) {
            continue;
        }
        let song = crate::commands::shed_queue_context(song);
        let Ok(json) = serde_json::to_string(&song) else { continue };
        if state.db.queue_download(&id, &json, now) || {
            // A row left 'queued' with no job behind it: nothing to queue in the DB, but it needs one.
            state.db.download_rows().iter().any(|r| r.video_id == id && r.status != "done")
        } {
            spawn(state.clone(), song);
            n += 1;
        }
    }
    if n > 0 {
        changed(dl);
    }
    n
}

/// Download again everything that failed.
pub fn retry_failed(state: &Arc<AppState>) -> usize {
    let failed: Vec<SongItem> = state
        .db
        .download_rows()
        .into_iter()
        .filter(|r| r.status == "failed")
        .filter_map(|r| serde_json::from_str(&r.song_json).ok())
        .collect();
    enqueue(state, failed)
}

/// Delete downloads (finished or not) and their files.
pub fn remove(state: &AppState, ids: &[String]) {
    let Some(dl) = DL.get() else { return };
    for id in ids {
        if let Some(job) = dl.jobs.lock().unwrap().remove(id) {
            job.cancel.store(true, Ordering::SeqCst);
        }
        dl.done.write().unwrap().remove(id);
        state.db.delete_download(id);
        delete_files(dl, id);
    }
    changed(dl);
}

/// Cancel every download that hasn't finished. What's done stays.
pub fn cancel_all(state: &AppState) {
    let Some(dl) = DL.get() else { return };
    let jobs: Vec<(String, Arc<Job>)> = dl.jobs.lock().unwrap().drain().collect();
    for (id, job) in jobs {
        job.cancel.store(true, Ordering::SeqCst);
        delete_files(dl, &id);
    }
    state.db.delete_downloads(true);
    changed(dl);
}

/// Remove every download (Settings ▸ Storage).
pub fn remove_all(state: &AppState) {
    let Some(dl) = DL.get() else { return };
    for (_, job) in dl.jobs.lock().unwrap().drain() {
        job.cancel.store(true, Ordering::SeqCst);
    }
    dl.done.write().unwrap().clear();
    state.db.delete_downloads(false);
    if let Ok(entries) = std::fs::read_dir(&dl.dir) {
        for e in entries.flatten() {
            let _ = std::fs::remove_file(e.path());
        }
    }
    changed(dl);
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Storage {
    /// Bytes the downloads folder holds (finished songs, their artwork, partial files).
    pub bytes: u64,
    pub songs: usize,
    /// Free space on the volume the downloads live on, when the platform says.
    pub free: Option<u64>,
}

pub fn storage() -> Storage {
    let Some(dl) = DL.get() else { return Storage { bytes: 0, songs: 0, free: None } };
    let bytes = std::fs::read_dir(&dl.dir)
        .map(|it| it.flatten().filter_map(|e| e.metadata().ok()).map(|m| m.len()).sum())
        .unwrap_or(0);
    let songs = dl.done.read().map(|d| d.len()).unwrap_or(0);
    Storage { bytes, songs, free: free_space(&dl.dir) }
}

#[cfg(any(target_os = "linux", target_os = "android"))]
fn free_space(path: &Path) -> Option<u64> {
    use std::ffi::CString;
    use std::os::unix::ffi::OsStrExt;
    let c = CString::new(path.as_os_str().as_bytes()).ok()?;
    let mut st: libc::statvfs = unsafe { std::mem::zeroed() };
    // SAFETY: `c` is a valid NUL-terminated path and `st` a properly sized out-parameter.
    if unsafe { libc::statvfs(c.as_ptr(), &mut st) } != 0 {
        return None;
    }
    Some(st.f_bavail as u64 * st.f_frsize as u64)
}

#[cfg(not(any(target_os = "linux", target_os = "android")))]
fn free_space(_: &Path) -> Option<u64> {
    None
}

// --- the work ------------------------------------------------------------------------------------

fn spawn(state: Arc<AppState>, song: SongItem) {
    let Some(dl) = DL.get() else { return };
    let id = song.video_id.clone();
    let job = Arc::new(Job {
        title: song.title.clone(),
        cancel: AtomicBool::new(false),
        got: AtomicU64::new(0),
        total: AtomicU64::new(0),
    });
    dl.jobs.lock().unwrap().insert(id.clone(), job.clone());
    ensure_ticker(dl);
    tauri::async_runtime::spawn(async move {
        let Ok(_slot) = dl.slots.clone().acquire_owned().await else { return };
        if job.cancel.load(Ordering::SeqCst) {
            return;
        }
        state.db.set_download_status(&id, "downloading", None);
        changed(dl);
        let result = download(&state, dl, &song, &job).await;
        let cancelled = job.cancel.load(Ordering::SeqCst);
        // Only our own entry: a remove + re-download while this one wound down has a new job.
        {
            let mut jobs = dl.jobs.lock().unwrap();
            if jobs.get(&id).is_some_and(|j| Arc::ptr_eq(j, &job)) {
                jobs.remove(&id);
            }
        }
        match result {
            _ if cancelled => {
                // `remove` / `cancel_all` already took the row; the files may have been reopened
                // since, by the last write.
                delete_files(dl, &id);
            }
            Ok(off) => {
                let size = std::fs::metadata(&off.path).map(|m| m.len() as i64).unwrap_or(0);
                state.db.finish_download(
                    &id,
                    &off.path.to_string_lossy(),
                    off.cover.as_ref().map(|p| p.to_string_lossy()).as_deref(),
                    size,
                    off.loudness_db,
                    now_secs(),
                );
                dl.done.write().unwrap().insert(id.clone(), off);
                dl.finished.fetch_add(1, Ordering::SeqCst);
                tracing::info!(video_id = %id, size, "downloaded");
                let (state, song) = (state.clone(), song.clone());
                tauri::async_runtime::spawn(async move { prefetch_lyrics(&state, &song).await });
            }
            Err(e) => {
                tracing::warn!(video_id = %id, error = %e, "download failed");
                state.db.set_download_status(&id, "failed", Some(&e));
                dl.failed.fetch_add(1, Ordering::SeqCst);
            }
        }
        changed(dl);
    });
}

fn part_path(dl: &Downloads, id: &str) -> PathBuf {
    dl.dir.join(format!("{}.part", safe(id)))
}

/// A videoId as a file name. They are `[A-Za-z0-9_-]{11}` in practice; anything else is mapped
/// rather than trusted, since it ends up in a path.
fn safe(id: &str) -> String {
    id.chars().map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '_' }).collect()
}

fn delete_files(dl: &Downloads, id: &str) {
    let stem = safe(id);
    if let Ok(entries) = std::fs::read_dir(&dl.dir) {
        for e in entries.flatten() {
            let name = e.file_name();
            let name = name.to_string_lossy();
            if name.split('.').next() == Some(stem.as_str()) {
                let _ = std::fs::remove_file(e.path());
            }
        }
    }
}

/// The container a format's itag comes in. mpv sniffs the content either way; the extension is
/// for anyone looking at the folder.
fn extension(itag: i64) -> &'static str {
    match itag {
        249 | 250 | 251 | 600 | 171 | 172 => "webm",
        139 | 140 | 141 | 599 => "m4a",
        _ => "audio",
    }
}

async fn resolve(state: &AppState, song: &SongItem, q: AudioQuality) -> Result<PlaybackData, String> {
    state
        .orchestrator
        .resolve(&song.video_id, song.is_upload, q, &state.disabled_clients())
        .await
        .map_err(|e| e.to_string())
}

async fn download(state: &AppState, dl: &Downloads, song: &SongItem, job: &Job) -> Result<Offline, String> {
    let id = song.video_id.as_str();
    let (q, q_name) = quality(&state.db);
    let mut data = resolve(state, song, q).await?;
    let mut resolves = 1;

    let part = part_path(dl, id);
    // A partial file from before a restart is kept only if it is the same format.
    let kept_itag = state.db.download_rows().into_iter().find(|r| r.video_id == id).and_then(|r| r.itag);
    let mut pos = match std::fs::metadata(&part) {
        Ok(m) if kept_itag == Some(data.itag) => m.len(),
        _ => 0,
    };
    state.db.set_download_itag(id, data.itag, q_name);
    let file = tokio::fs::OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(pos == 0)
        .open(&part)
        .await
        .map_err(|e| format!("could not write the file: {e}"))?;
    file.set_len(pos).await.map_err(|e| e.to_string())?;
    let mut file = tokio::io::BufWriter::with_capacity(256 * 1024, file);
    use tokio::io::AsyncSeekExt;
    file.seek(std::io::SeekFrom::Start(pos)).await.map_err(|e| e.to_string())?;

    let mut total: Option<u64> = None;
    let mut retries = 0u32;
    job.got.store(pos, Ordering::Relaxed);

    loop {
        if job.cancel.load(Ordering::SeqCst) {
            return Err("cancelled".into());
        }
        if total.is_some_and(|t| pos >= t) {
            break;
        }
        let end = match total {
            Some(t) => (pos + CHUNK - 1).min(t - 1),
            None => pos + CHUNK - 1,
        };
        let mut req = crate::http::client()
            .get(&data.stream_url)
            .header("Range", format!("bytes={pos}-{end}"))
            .header("Accept-Encoding", "identity");
        for (k, v) in &data.headers {
            req = req.header(k.as_str(), v.as_str());
        }
        let resp = match tokio::time::timeout(STALL, req.send()).await {
            Ok(Ok(r)) => r,
            Ok(Err(e)) => {
                backoff(&mut retries, &format!("{e}")).await?;
                continue;
            }
            Err(_) => {
                backoff(&mut retries, "no answer").await?;
                continue;
            }
        };
        let status = resp.status().as_u16();
        match status {
            200 | 206 => {}
            416 if total.is_some_and(|t| pos >= t) => break,
            // Asked for bytes past the end: a resumed file that was already complete.
            416 if pos > 0
                && resp
                    .headers()
                    .get("content-range")
                    .and_then(|v| v.to_str().ok())
                    .and_then(|v| v.rsplit_once('/'))
                    .and_then(|(_, t)| t.trim().parse::<u64>().ok())
                    == Some(pos) =>
            {
                break
            }
            // The URL ran out (they last hours, and a long queue can outlive one) or was refused.
            403 | 410 | 416 => {
                if resolves >= MAX_RESOLVES {
                    return Err(format!("YouTube refused the download ({status})"));
                }
                tracing::info!(video_id = id, status, client = %data.stream_client, "download URL refused, resolving again");
                state.db.evict_stream(id);
                if data.stream_client == "WEB_REMIX" {
                    state.orchestrator.mark_web_remix_failed(id).await;
                }
                let fresh = resolve(state, song, q).await?;
                resolves += 1;
                if fresh.itag != data.itag {
                    // Another format: the bytes so far are of no use to it.
                    file.flush().await.map_err(|e| e.to_string())?;
                    file.get_mut().set_len(0).await.map_err(|e| e.to_string())?;
                    file.seek(std::io::SeekFrom::Start(0)).await.map_err(|e| e.to_string())?;
                    pos = 0;
                    total = None;
                    state.db.set_download_itag(id, fresh.itag, q_name);
                }
                data = fresh;
                continue;
            }
            _ if status >= 500 || status == 429 => {
                backoff(&mut retries, &format!("HTTP {status}")).await?;
                continue;
            }
            _ => return Err(format!("the download failed (HTTP {status})")),
        }
        // A 200 is the whole file from the top, whatever was asked for.
        if status == 200 && pos > 0 {
            file.flush().await.map_err(|e| e.to_string())?;
            file.get_mut().set_len(0).await.map_err(|e| e.to_string())?;
            file.seek(std::io::SeekFrom::Start(0)).await.map_err(|e| e.to_string())?;
            pos = 0;
        }
        if total.is_none() {
            total = if status == 206 {
                resp.headers()
                    .get("content-range")
                    .and_then(|v| v.to_str().ok())
                    .and_then(|v| v.rsplit_once('/'))
                    .and_then(|(_, t)| t.trim().parse().ok())
            } else {
                resp.content_length()
            };
            if let Some(t) = total {
                job.total.store(t, Ordering::Relaxed);
            }
        }
        let asked = end + 1 - pos;
        let mut got_here = 0u64;
        let mut resp = resp;
        loop {
            if job.cancel.load(Ordering::SeqCst) {
                return Err("cancelled".into());
            }
            match tokio::time::timeout(STALL, resp.chunk()).await {
                Ok(Ok(Some(bytes))) => {
                    file.write_all(&bytes).await.map_err(|e| format!("could not write the file: {e}"))?;
                    pos += bytes.len() as u64;
                    got_here += bytes.len() as u64;
                    job.got.store(pos, Ordering::Relaxed);
                }
                Ok(Ok(None)) => break,
                Ok(Err(e)) => {
                    tracing::debug!(video_id = id, error = %e, "download chunk cut off");
                    break;
                }
                Err(_) => {
                    tracing::debug!(video_id = id, "download chunk stalled");
                    break;
                }
            }
        }
        if got_here == 0 {
            backoff(&mut retries, "the connection dropped").await?;
            continue;
        }
        retries = 0;
        // No length to go by: a short range is the end of the file.
        if total.is_none() && (status == 200 || got_here < asked) {
            total = Some(pos);
        }
    }
    file.flush().await.map_err(|e| e.to_string())?;
    file.get_mut().sync_all().await.ok();
    drop(file);
    if job.cancel.load(Ordering::SeqCst) {
        return Err("cancelled".into());
    }
    if pos == 0 {
        return Err("YouTube sent an empty file".into());
    }
    let path = dl.dir.join(format!("{}.{}", safe(id), extension(data.itag)));
    tokio::fs::rename(&part, &path).await.map_err(|e| e.to_string())?;
    // An album page's rows carry no artwork of their own; `/player` named one.
    let mut song = song.clone();
    if song.thumbnail.is_none() {
        if let Some(thumb) = data.thumbnail.clone() {
            song.thumbnail = Some(thumb);
            if let Ok(json) = serde_json::to_string(&song) {
                state.db.set_download_song(id, &json);
            }
        }
    }
    let cover = save_cover(dl, &song).await;
    Ok(Offline { path, cover, loudness_db: data.loudness_db })
}

/// Wait before trying again after a network error, or give up after [`MAX_RETRIES`].
async fn backoff(retries: &mut u32, why: &str) -> Result<(), String> {
    *retries += 1;
    if *retries > MAX_RETRIES {
        return Err(format!("the connection kept failing ({why})"));
    }
    let secs = (1u64 << (*retries - 1)).min(30);
    tokio::time::sleep(Duration::from_secs(secs)).await;
    Ok(())
}

/// The song's artwork, big enough for the full-screen player. Best effort: a download without its
/// cover still plays, and the UI falls back to the song's own thumbnail when online.
async fn save_cover(dl: &Downloads, song: &SongItem) -> Option<PathBuf> {
    let url = song.thumbnail.as_deref().filter(|u| u.starts_with("https://"))?;
    let url = sized(url, 720);
    let resp = crate::http::client().get(&url).timeout(Duration::from_secs(20)).send().await.ok()?;
    if !resp.status().is_success() {
        return None;
    }
    let bytes = resp.bytes().await.ok()?;
    if bytes.len() < 64 {
        return None;
    }
    let path = dl.dir.join(format!("{}.jpg", safe(&song.video_id)));
    tokio::fs::write(&path, &bytes).await.ok()?;
    Some(path)
}

/// A googleusercontent image URL at `px` square; anything else as it is.
fn sized(url: &str, px: u32) -> String {
    static WH: OnceLock<regex::Regex> = OnceLock::new();
    static S: OnceLock<regex::Regex> = OnceLock::new();
    let wh = WH.get_or_init(|| regex::Regex::new(r"=w\d+-h\d+").expect("static regex"));
    let s = S.get_or_init(|| regex::Regex::new(r"=s\d+").expect("static regex"));
    if wh.is_match(url) {
        wh.replace(url, format!("=w{px}-h{px}").as_str()).into_owned()
    } else if s.is_match(url) {
        s.replace(url, format!("=s{px}").as_str()).into_owned()
    } else {
        url.to_owned()
    }
}

/// Lyrics are cached for good once found (lyrics.rs), so fetching them now is what makes them
/// show offline.
async fn prefetch_lyrics(state: &AppState, song: &SongItem) {
    let duration = crate::state::parse_duration_ms(song.duration.as_deref());
    let req = crate::lyrics::LyricsRequest {
        video_id: song.video_id.clone(),
        title: song.title.clone(),
        artists: song.artists.clone(),
        album: song.album.clone(),
        duration: (duration > 0).then(|| duration as f64 / 1000.0),
        searched: false,
    };
    let _ = tokio::time::timeout(Duration::from_secs(20), crate::lyrics::get_lyrics(state, req, None)).await;
}

// --- progress ------------------------------------------------------------------------------------

fn progress_map() -> HashMap<String, f64> {
    let Some(dl) = DL.get() else { return HashMap::new() };
    dl.jobs
        .lock()
        .unwrap()
        .iter()
        .map(|(id, j)| {
            let total = j.total.load(Ordering::Relaxed);
            let got = j.got.load(Ordering::Relaxed);
            (id.clone(), if total > 0 { (got as f64 / total as f64).min(1.0) } else { 0.0 })
        })
        .collect()
}

/// Tell the UI to read the list again.
fn changed(dl: &Downloads) {
    let _ = dl.app.emit("downloads-changed", ());
}

/// While anything is queued: the progress of what's running, to the UI and (Android) to the
/// download notification, about once a second. Stops itself when the queue is empty.
fn ensure_ticker(dl: &'static Downloads) {
    if dl.ticking.swap(true, Ordering::SeqCst) {
        return;
    }
    tauri::async_runtime::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_millis(800)).await;
            let progress = progress_map();
            // Only what is moving: a long queue's waiting songs are all 0 and stay so.
            let moving: HashMap<&String, &f64> = progress.iter().filter(|(_, p)| **p > 0.0).collect();
            let _ = dl.app.emit("download-progress", &moving);
            let empty = progress.is_empty();
            notify(dl, &progress).await;
            if empty {
                dl.ticking.store(false, Ordering::SeqCst);
                // A job added between the read and the store would have found the flag still set.
                if !dl.jobs.lock().unwrap().is_empty() && !dl.ticking.swap(true, Ordering::SeqCst) {
                    continue;
                }
                dl.finished.store(0, Ordering::SeqCst);
                dl.failed.store(0, Ordering::SeqCst);
                break;
            }
        }
    });
}

#[cfg(target_os = "android")]
async fn notify(dl: &Downloads, progress: &HashMap<String, f64>) {
    let (running, title) = {
        let jobs = dl.jobs.lock().unwrap();
        let running: Vec<&Arc<Job>> =
            jobs.values().filter(|j| j.total.load(Ordering::Relaxed) > 0 || j.got.load(Ordering::Relaxed) > 0).collect();
        (running.len(), running.first().map(|j| j.title.clone()))
    };
    let pending = progress.len();
    let finished = dl.finished.load(Ordering::SeqCst);
    let failed = dl.failed.load(Ordering::SeqCst);
    // Overall: finished ones count whole, the rest by their bytes.
    let total_songs = pending as f64 + finished as f64;
    let overall = if total_songs > 0.0 {
        (finished as f64 + progress.values().sum::<f64>()) / total_songs
    } else {
        1.0
    };
    let body = serde_json::json!({
        "pending": pending,
        "running": running,
        "finished": finished,
        "failed": failed,
        "title": title,
        "progress": overall,
    });
    if let Err(e) = crate::android::call::<serde_json::Value>(&dl.app, "downloadStatus", body).await {
        tracing::debug!(error = %e, "download notification");
    }
}

#[cfg(not(target_os = "android"))]
async fn notify(_: &Downloads, _: &HashMap<String, f64>) {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn file_names_are_safe() {
        assert_eq!(safe("dQw4w9WgXcQ"), "dQw4w9WgXcQ");
        assert_eq!(safe("../etc/passwd"), "___etc_passwd");
    }

    #[test]
    fn covers_are_asked_for_big() {
        assert_eq!(sized("https://lh3.googleusercontent.com/x=w120-h120-l90-rj", 720), "https://lh3.googleusercontent.com/x=w720-h720-l90-rj");
        assert_eq!(sized("https://yt3.ggpht.com/x=s88", 720), "https://yt3.ggpht.com/x=s720");
        assert_eq!(sized("https://i.ytimg.com/vi/x/hqdefault.jpg", 720), "https://i.ytimg.com/vi/x/hqdefault.jpg");
    }
}
