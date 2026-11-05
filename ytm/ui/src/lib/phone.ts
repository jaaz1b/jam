/**
 * Jam for Android: this UI is running as the YouTube Music app on a phone. Set by the Android
 * engine's init script (`src-tauri/src/android/mod.rs`) before any of this loads, so it is constant
 * for the page's life. Picks the phone shell (bottom tabs, mini player, full-screen player).
 */
export const phone: boolean =
	typeof window !== 'undefined' &&
	(window as unknown as { __JAM_ANDROID__?: boolean }).__JAM_ANDROID__ === true;
