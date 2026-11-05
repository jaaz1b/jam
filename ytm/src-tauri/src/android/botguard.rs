//! Jam for Android: BotGuard / PoToken minting without V8 builds for Android.
//!
//! Desktop runs `rustypipe-botguard` (deno_core + JSDOM): a JS runtime with no real DOM, the only
//! kind whose integrity tokens land in the class googlevideo accepts (`potoken/botguard.rs`, KI-1).
//! deno_core's V8 has no Android build, so here the same JS (the JSDOM + bgutils bundle
//! rustypipe-botguard ships, `bg_bundle.js`) runs inside a **Web Worker** of a hidden Android
//! WebView: V8 with timers, URL, TextEncoder and crypto, but no `document`, `window` or
//! `navigator` of its own, which is close to deno's globals. The network half (Create, GenerateIT)
//! is Rust, exactly as in rustypipe-botguard.
//!
//! The class check and the re-roll are desktop's: a pot's length gives away which integrity-token
//! class a bootstrap landed in, and a bootstrap is retried until it lands in the accepted one.
//! Anything that fails degrades the way desktop does: the orchestrator falls through to the
//! clients that need no PoToken.

use std::time::Duration;

use base64::Engine;
use serde_json::Value;

use crate::webview::Bridge;

/// The worker's script, in three app assets the harness page fetches from
/// `https://localhost/jam-asset/` (served by `JamPlugin`, off the app's main thread): the globals
/// shim, the bundle, and the message loop. `android/ci/botguard-probe` runs the same three.
const WORKER_PARTS: [&str; 3] =
    ["/jam-asset/bg_worker_head.js", "/jam-asset/bg_bundle.js", "/jam-asset/bg_worker_tail.js"];
const GOOG_API_KEY: &str = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw";
const REQUEST_KEY: &str = "O43z0dpjhgX20SCx4KAo";
const CONTENT_TYPE: &str = "application/json+protobuf";
const X_USER_AGENT: &str = "grpc-web-javascript/0.1";
/// rustypipe-botguard hands BotGuard a WebKit user agent ("other user agents result in invalid
/// tokens"); the HTTP requests still go out with the engine's own.
const JSDOM_UA: &str = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.10 Safari/605.1.1";
/// Same constants as desktop (`potoken/botguard.rs`, where they are measured and explained).
const POT_OVERHEAD: usize = 14;
const ACCEPTED_MAX_IT: usize = 64;
const MAX_BOOTSTRAPS: usize = 6;
const CLASS_PROBE: &str = "limusicprobe";
const LABEL: &str = "botguard";
const CALL: Duration = Duration::from_secs(20);

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("botguard runtime: {0}")]
    Fatal(String),
    #[error("botguard: {0}")]
    Transient(String),
}

pub struct Minter {
    bridge: Bridge,
}

pub struct Bootstrap {
    pub minter: Minter,
    pub session_token: String,
    pub lifetime_secs: u64,
}

impl Drop for Minter {
    fn drop(&mut self) {
        let _ = self.bridge.destroy();
    }
}

fn transient(e: impl std::fmt::Display) -> Error {
    Error::Transient(e.to_string())
}

/// The worker: page-side RPC (`__bgCall`), the worker's own globals arranged like deno's (no
/// `window`/`document`/`location`, an empty `navigator`), then the bundle.
fn setup_js() -> String {
    let parts = serde_json::to_string(&WORKER_PARTS).unwrap_or_default();
    format!(
        "(function(){{\
           if (window.__bgw) return;\
           const pending = {{}}; let n = 0;\
           window.__bgReady = Promise.all({parts}.map((u) => fetch(u).then((r) => {{ if (!r.ok) throw new Error(u + ' ' + r.status); return r.text(); }})))\
             .then((srcs) => new Promise((ready, failed) => {{\
               const w = new Worker(URL.createObjectURL(new Blob([srcs.join('\\n;\\n')], {{type:'text/javascript'}})));\
               w.onerror = (e) => {{ const msg = String((e && e.message) || e); failed(new Error(msg)); window.__jserr = 'worker: ' + msg; }};\
               w.onmessage = (e) => {{\
                 const m = e.data;\
                 if (m.id === 0) {{ ready(true); return; }}\
                 const p = pending[m.id]; if (!p) return; delete pending[m.id];\
                 if (m.ok) p.res(m.v); else p.rej(new Error(m.e));\
               }};\
               window.__bgw = w;\
             }}));\
           window.__bgCall = (fn, args) => window.__bgReady.then(() => new Promise((res, rej) => {{\
             const id = ++n; pending[id] = {{res, rej}}; window.__bgw.postMessage({{id, fn, args}});\
           }}));\
         }})();"
    )
}

async fn call(bridge: &Bridge, func: &str, args: &[&str]) -> Result<Value, Error> {
    let args = serde_json::to_string(args).map_err(transient)?;
    bridge
        .call_async(&format!("window.__bgCall({}, {args})", serde_json::to_string(func).unwrap_or_default()), CALL)
        .await
        .map_err(transient)
}

async fn call_str(bridge: &Bridge, func: &str, args: &[&str]) -> Result<String, Error> {
    match call(bridge, func, args).await? {
        Value::String(s) if !s.is_empty() => Ok(s),
        other => Err(Error::Transient(format!("{func} returned {other}"))),
    }
}

struct Challenge {
    interpreter_js: String,
    program: String,
    global_name: String,
}

async fn post(client: &reqwest::Client, url: &str, body: &Value) -> Result<Value, Error> {
    client
        .post(url)
        .header(reqwest::header::CONTENT_TYPE, CONTENT_TYPE)
        .header("x-goog-api-key", GOOG_API_KEY)
        .header("x-user-agent", X_USER_AGENT)
        .body(body.to_string())
        .send()
        .await
        .map_err(transient)?
        .error_for_status()
        .map_err(transient)?
        .json::<Value>()
        .await
        .map_err(transient)
}

/// `/Create`, descrambled and resolved (rustypipe-botguard's `get_challenge` +
/// `resolve_challenge_data`).
async fn challenge(client: &reqwest::Client) -> Result<Challenge, Error> {
    let resp = post(client, "https://www.youtube.com/api/jnn/v1/Create", &serde_json::json!([REQUEST_KEY])).await?;
    let arr = resp.as_array().ok_or_else(|| transient("Create: array expected"))?;
    let cdata: Vec<Value> = if let Some(scrambled) = arr.get(1).and_then(Value::as_str) {
        let bytes = base64::engine::general_purpose::STANDARD.decode(scrambled).map_err(transient)?;
        let bytes: Vec<u8> = bytes.into_iter().map(|b| b.wrapping_add(97)).collect();
        serde_json::from_slice(&bytes).map_err(transient)?
    } else if let Some(obj) = arr.first().and_then(Value::as_array) {
        obj.clone()
    } else {
        return Err(transient("Create: unknown format"));
    };
    if cdata.len() < 6 {
        return Err(transient("Create: short challenge"));
    }
    let first_str = |v: &Value| {
        v.as_array().and_then(|a| a.iter().find_map(|x| x.as_str().filter(|s| !s.is_empty())).map(str::to_owned))
    };
    let interpreter_js = if let Some(script) = first_str(&cdata[1]) {
        script
    } else if let Some(url) = first_str(&cdata[2]) {
        let url = if url.starts_with("//") { format!("https:{url}") } else { url };
        let parsed = reqwest::Url::parse(&url).map_err(transient)?;
        let host = parsed.host_str().unwrap_or_default().trim_start_matches("www.");
        if host != "google.com" && host != "youtube.com" {
            return Err(transient(format!("Create: interpreter on {host}")));
        }
        client.get(parsed).send().await.map_err(transient)?.error_for_status().map_err(transient)?.text().await.map_err(transient)?
    } else {
        return Err(transient("Create: no interpreter"));
    };
    let program = cdata[4].as_str().ok_or_else(|| transient("Create: program"))?.to_owned();
    let global_name = cdata[5].as_str().ok_or_else(|| transient("Create: globalName"))?.to_owned();
    Ok(Challenge { interpreter_js, program, global_name })
}

/// Desktop's class arithmetic (`potoken/botguard.rs::integrity_token_len`).
fn integrity_token_len(pot: &str, ident: &str) -> Option<usize> {
    let bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD.decode(pot.trim_end_matches('=')).ok()?;
    let ident_len = urlencoding::decode(ident).map_or(ident.len(), |d| d.len());
    bytes.len().checked_sub(ident_len + POT_OVERHEAD)
}

/// One bootstrap: a fresh worker, a solved challenge, a minter. Returns the session token, the
/// probe's integrity-token size and the lifetime.
async fn bootstrap_once(
    bridge: &Bridge,
    client: &reqwest::Client,
    session_ident: &str,
) -> Result<(String, Option<usize>, u64), Error> {
    bridge.eval(&setup_js()).map_err(transient)?;
    bridge
        .call_async("window.__bgReady", Duration::from_secs(20))
        .await
        .map_err(|e| Error::Transient(format!("worker did not start: {e}")))?;
    let c = challenge(client).await?;
    let bg_response = call_str(bridge, "runBotguard", &[&c.interpreter_js, &c.program, &c.global_name, JSDOM_UA]).await?;
    let it = post(client, "https://www.youtube.com/api/jnn/v1/GenerateIT", &serde_json::json!([REQUEST_KEY, bg_response]))
        .await?;
    let it_arr = it.as_array().ok_or_else(|| transient("GenerateIT: array expected"))?;
    let integrity_token = it_arr.first().and_then(Value::as_str).ok_or_else(|| transient("GenerateIT: no token"))?;
    let lifetime = it_arr.get(1).and_then(Value::as_u64).unwrap_or(43_200);
    call(bridge, "newMinter", &[integrity_token]).await?;
    // Session token first, on the fresh minter, as desktop and Metrolist do.
    let ident = urlencoding::decode(session_ident).map(|c| c.into_owned()).unwrap_or_else(|_| session_ident.to_owned());
    let session_token = call_str(bridge, "mint", &[&ident]).await?;
    let probe = call_str(bridge, "mint", &[CLASS_PROBE]).await?;
    Ok((session_token, integrity_token_len(&probe, CLASS_PROBE), lifetime))
}

impl Minter {
    pub async fn spawn(user_agent: String, session_ident: String) -> Result<Bootstrap, Error> {
        let app = crate::android::app().ok_or_else(|| Error::Fatal("engine not started".into()))?;
        let client = reqwest::Client::builder()
            .user_agent(user_agent)
            .gzip(true)
            .brotli(true)
            .timeout(Duration::from_secs(20))
            .build()
            .map_err(|e| Error::Fatal(e.to_string()))?;
        let mut last: Option<(Bridge, String, u64, Option<usize>)> = None;
        for attempt in 1..=MAX_BOOTSTRAPS {
            if let Some((b, ..)) = last.take() {
                crate::webview::destroy_and_wait(app, LABEL).await;
                drop(b);
            }
            // No WebView at all is the one thing that won't fix itself.
            let bridge = Bridge::create(app, LABEL).await.map_err(|e| Error::Fatal(e.to_string()))?;
            let (token, it, lifetime) = match bootstrap_once(&bridge, &client, &session_ident).await {
                Ok(r) => r,
                Err(e) => {
                    let _ = bridge.destroy();
                    return Err(e);
                }
            };
            if it.is_some_and(|n| n <= ACCEPTED_MAX_IT) {
                tracing::info!(attempt, it_bytes = it, ttl = lifetime, "PoToken minter ready (Android worker, accepted class)");
                return Ok(Bootstrap { minter: Minter { bridge }, session_token: token, lifetime_secs: lifetime });
            }
            tracing::info!(attempt, it_bytes = it, "botguard bootstrap (Android worker) in the rejected class, re-rolling");
            last = Some((bridge, token, lifetime, it));
        }
        let (bridge, token, lifetime, it) = last.expect("MAX_BOOTSTRAPS >= 1");
        tracing::warn!(it_bytes = it, "no accepted-class integrity token in {MAX_BOOTSTRAPS} bootstraps (Android worker); using the last");
        Ok(Bootstrap { minter: Minter { bridge }, session_token: token, lifetime_secs: lifetime })
    }

    pub async fn mint(&self, ident: &str) -> Result<String, Error> {
        let ident = urlencoding::decode(ident).map(|c| c.into_owned()).unwrap_or_else(|_| ident.to_owned());
        call_str(&self.bridge, "mint", &[&ident]).await
    }
}
