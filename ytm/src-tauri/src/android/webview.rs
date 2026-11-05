//! Jam for Android: the hidden-webview bridge (`crate::webview` on desktop), backed by an Android
//! WebView that `JamPlugin.kt` keeps off screen. Same API and the same harness document, so the
//! cipher code above it runs unchanged: YouTube's player.js executes in a real browser engine,
//! exactly as it does in desktop's hidden window.

use std::time::{Duration, Instant};

use serde_json::Value;
use tauri::AppHandle;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("webview '{0}' does not exist")]
    Gone(String),
    #[error("webview eval failed: {0}")]
    Eval(String),
    #[error("timed out after {0:?}")]
    Timeout(Duration),
    #[error("webview reported a JS error: {0}")]
    BadWebview(String),
    #[error("build failed: {0}")]
    Build(String),
}

#[derive(Clone)]
pub struct Bridge {
    app: AppHandle,
    label: String,
}

/// Kept for lib.rs, which registers the desktop scheme; Android loads the harness directly.
pub const SCHEME: &str = "limusicharness";

pub const HARNESS_HTML: &str = "<!doctype html><html><head><meta charset=utf-8></head><body>\
<script>\
window.__jserr=null;window.__slots={};window._yt_player=window._yt_player||{};\
window.addEventListener('error',function(e){window.__jserr=String((e&&e.message)||e);});\
window.onunhandledrejection=function(e){window.__jserr=String((e.reason&&e.reason.message)||e.reason);};\
window.__harness=1;\
</script></body></html>";

#[derive(serde::Deserialize)]
struct EvalReply {
    json: Option<String>,
}

#[derive(serde::Deserialize)]
struct ExistsReply {
    exists: bool,
}

impl Bridge {
    pub async fn create(app: &AppHandle, label: &str) -> Result<Bridge, Error> {
        crate::android::call::<Value>(
            app,
            "harnessCreate",
            serde_json::json!({ "label": label, "html": HARNESS_HTML }),
        )
        .await
        .map_err(Error::Build)?;
        let bridge = Bridge { app: app.clone(), label: label.to_string() };
        let deadline = Instant::now() + Duration::from_secs(12);
        loop {
            let ready =
                bridge.eval_json("window.__harness===1".to_owned(), Duration::from_millis(800)).await;
            if matches!(ready, Ok(Value::Bool(true))) {
                tracing::info!(label, "webview bridge OK — harness loaded, eval round-trips");
                return Ok(bridge);
            }
            if Instant::now() >= deadline {
                let _ = bridge.destroy();
                return Err(Error::Timeout(Duration::from_secs(12)));
            }
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
    }

    /// Cheap and synchronous on desktop; here the plugin round trip blocks, so it is only asked
    /// from the cipher's idle checks, never on a hot path.
    pub fn exists(&self) -> bool {
        crate::android::call_blocking::<ExistsReply>(
            &self.app,
            "harnessExists",
            serde_json::json!({ "label": self.label }),
        )
        .map(|r| r.exists)
        .unwrap_or(false)
    }

    pub fn eval(&self, js: &str) -> Result<(), Error> {
        let app = self.app.clone();
        let args = serde_json::json!({ "label": self.label, "js": js });
        crate::android::call_blocking::<Value>(&app, "harnessEval", args).map(|_| ()).map_err(|e| {
            if e.contains("gone") {
                Error::Gone(self.label.clone())
            } else {
                Error::Eval(e)
            }
        })
    }

    pub async fn eval_json(&self, js: String, timeout: Duration) -> Result<Value, Error> {
        let call = crate::android::call::<EvalReply>(
            &self.app,
            "harnessEvalJson",
            serde_json::json!({ "label": self.label, "js": js }),
        );
        let reply = tokio::time::timeout(timeout, call)
            .await
            .map_err(|_| Error::Timeout(timeout))?
            .map_err(|e| if e.contains("gone") { Error::Gone(self.label.clone()) } else { Error::Eval(e) })?;
        let raw = reply.json.unwrap_or_default();
        if raw.is_empty() || raw == "null" {
            return Ok(Value::Null);
        }
        serde_json::from_str(&raw).map_err(|e| Error::Eval(format!("bad json {raw:?}: {e}")))
    }

    async fn poll_json(&self, expr: &str, timeout: Duration) -> Result<Value, Error> {
        let deadline = Instant::now() + timeout;
        let step = Duration::from_millis(100);
        loop {
            match self.eval_json(format!("({expr})"), Duration::from_secs(3)).await {
                Ok(v) if !v.is_null() => return Ok(v),
                Ok(_) => {}
                Err(Error::Gone(l)) => return Err(Error::Gone(l)),
                Err(_) => {}
            }
            if let Ok(Some(msg)) = self
                .eval_json("window.__jserr||null".into(), Duration::from_secs(3))
                .await
                .map(|v| v.as_str().map(str::to_owned))
            {
                return Err(Error::BadWebview(msg));
            }
            if Instant::now() >= deadline {
                return Err(Error::Timeout(timeout));
            }
            tokio::time::sleep(step).await;
        }
    }

    pub async fn call_async(&self, expr: &str, timeout: Duration) -> Result<Value, Error> {
        static N: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let id = N.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        let kick = format!(
            "(async()=>{{try{{window.__slots['{id}']={{ok:1,v:await ({expr})}};}}\
             catch(e){{window.__slots['{id}']={{ok:0,e:String((e&&e.message)||e)}};}}}})();"
        );
        self.eval(&kick)?;
        let res = self.poll_json(&format!("window.__slots['{id}']||null"), timeout).await;
        let _ = self.eval(&format!("delete window.__slots['{id}'];"));
        let slot = res?;
        if slot.get("ok").and_then(Value::as_i64) == Some(1) {
            Ok(slot.get("v").cloned().unwrap_or(Value::Null))
        } else {
            Err(Error::Eval(
                slot.get("e").and_then(Value::as_str).unwrap_or("async call failed").to_owned(),
            ))
        }
    }

    pub fn destroy(&self) -> Result<(), Error> {
        let app = self.app.clone();
        let label = self.label.clone();
        tauri::async_runtime::spawn(async move {
            let _ = crate::android::call::<Value>(&app, "harnessDestroy", serde_json::json!({ "label": label })).await;
        });
        Ok(())
    }
}

pub(crate) async fn destroy_and_wait(app: &AppHandle, label: &str) {
    let _ = crate::android::call::<Value>(app, "harnessDestroy", serde_json::json!({ "label": label })).await;
}
