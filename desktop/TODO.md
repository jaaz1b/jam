# Desktop: for the next desktop release

Nothing pending.

Done in 3.2: the saved state keeps the pages only (no copy of the session; Jam 3.1's copy is
deleted at start, `savedstate.js` `cleanUp`), Jam keeps the covers itself (`covers.js`, served in
the saved state through `protocol.handle` on the blocked session), and Linux / Arch builds
(`.github/workflows/linux.yml`, `linux/arch/PKGBUILD.in`).

Already in 3.1 (shipped on Android first): YouTube Music downloads and offline library, Octave
offline, the Octave saved-state switch (title-bar save button).
