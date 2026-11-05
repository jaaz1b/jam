fn main() {
    // Last.fm API credentials live in a gitignored `lastfm.keys` next to this file (the repo is
    // public — they must never be tracked). Format: `LIMUSIC_LASTFM_API_KEY=…` and
    // `LIMUSIC_LASTFM_API_SECRET=…`, one per line. Exported as compile-time env for `option_env!`
    // in lastfm.rs; missing file just means the scrobbler reports "not configured".
    println!("cargo:rerun-if-changed=lastfm.keys");
    if let Ok(keys) = std::fs::read_to_string("lastfm.keys") {
        for line in keys.lines() {
            if let Some((k, v)) = line.split_once('=') {
                let (k, v) = (k.trim(), v.trim());
                if k == "LIMUSIC_LASTFM_API_KEY" || k == "LIMUSIC_LASTFM_API_SECRET" {
                    println!("cargo:rustc-env={k}={v}");
                }
            }
        }
    }
    // tauri-build watches tauri.conf.json but not the icons it embeds, so editing a PNG here
    // leaves `generate_context!` emitting the old `default_window_icon` (window, tray, taskbar).
    println!("cargo:rerun-if-changed=icons");

    // Jam for Android: its own capability set, because the desktop one grants permissions of
    // plugins that are not built for Android (updater, global shortcuts), which tauri-build rejects.
    let mut attrs = tauri_build::Attributes::new();
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        println!("cargo:rerun-if-changed=capabilities-android");
        attrs = attrs.capabilities_path_pattern("./capabilities-android/*.json");
        // libmpv and the FFmpeg libraries it needs, built by android/libmpv and unpacked into the
        // app's jniLibs (they ship in the APK from there). Linked from here, not RUSTFLAGS, because
        // the Tauri CLI sets RUSTFLAGS for Android builds and that would drop a target flag.
        let abi = match std::env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
            Ok("aarch64") => "arm64-v8a",
            Ok("x86_64") => "x86_64",
            Ok("arm") => "armeabi-v7a",
            _ => "x86",
        };
        let dir = std::env::var("JAM_LIBMPV_DIR").map(std::path::PathBuf::from).unwrap_or_else(|_| {
            std::path::Path::new(&std::env::var("CARGO_MANIFEST_DIR").unwrap())
                .join("gen/android/app/src/main/jniLibs")
        });
        println!("cargo:rerun-if-env-changed=JAM_LIBMPV_DIR");
        println!("cargo:rustc-link-search=native={}", dir.join(abi).display());
    }
    tauri_build::try_build(attrs).expect("tauri-build failed")
}
