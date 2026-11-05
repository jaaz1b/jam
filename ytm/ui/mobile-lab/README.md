# Mobile lab (dev only)

Lays out the YouTube Music UI on a phone-sized screen in headless Chrome, with the Tauri backend
replaced by `mock.js` answering from real recorded responses. Nothing here ships.

1. Fixtures: run the **UI fixtures** workflow (or `cargo run -p innertube --example fixtures -- <dir>`
   plus the artwork step in `.github/workflows/ui-fixtures.yml`), and unpack `fixtures.tgz`.
2. `pnpm dev` in `ytm/ui`.
3. `NODE_PATH=<dir with puppeteer-core> node mobile-lab/lab.mjs <fixtures dir> <out dir> [scene …]`

Scenes are listed at the top of `lab.mjs` (home, search, album, artist, playlist, library, np, npq,
npl, more, settings, menu …). `LAB_LIGHT=1` for the light theme.
