# Signing keys

`jam-debug.jks` is a **public debug key** (password `android`, alias `androiddebugkey`). It keeps the
signature the same between CI builds, so a new APK installs over the old one without losing your logins.

Because it's public, anyone could sign an APK that installs over Jam. Only install Jam APKs from this
repo's Releases. To use a private key instead, add these repository secrets and CI will use them:

- `JAM_KEYSTORE_B64` — the keystore file, base64-encoded
- `JAM_KEYSTORE_PASSWORD`, `JAM_KEY_ALIAS`, `JAM_KEY_PASSWORD` (optional, defaults to the store password)

Switching keys means uninstalling the old Jam once (Android won't update across signatures).
