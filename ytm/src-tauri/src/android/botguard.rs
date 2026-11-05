//! Jam for Android: no BotGuard. It runs on V8 (deno_core), which has no Android build, so minting
//! fails at once and `PoTokenGenerator` latches the runtime as unusable. The orchestrator then
//! resolves through the clients that need no PoToken, the same path desktop takes when V8 fails.

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("botguard runtime: {0}")]
    Fatal(String),
    #[error("botguard: {0}")]
    Transient(String),
}

pub struct Minter;

pub struct Bootstrap {
    pub minter: Minter,
    pub session_token: String,
    pub lifetime_secs: u64,
}

impl Minter {
    pub async fn spawn(_user_agent: String, _session_ident: String) -> Result<Bootstrap, Error> {
        Err(Error::Fatal("BotGuard is not available on Android".into()))
    }

    pub async fn mint(&self, _ident: &str) -> Result<String, Error> {
        Err(Error::Fatal("BotGuard is not available on Android".into()))
    }
}
