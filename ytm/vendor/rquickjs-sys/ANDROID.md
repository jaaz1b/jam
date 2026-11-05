Jam: rquickjs-sys 0.9.0 (used by rustypipe) as published, plus pregenerated bindings for
`aarch64-linux-android` and `x86_64-linux-android`, copied from the matching `*-unknown-linux-gnu`
files (same LP64 C ABI; QuickJS's API uses no glibc-specific types). Upstream ships no Android
bindings, and generating them would need bindgen + libclang against the NDK in every build.
Wired in through `[patch.crates-io]` in the workspace Cargo.toml.
