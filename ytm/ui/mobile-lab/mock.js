// Jam mobile lab: a stand-in for the Tauri backend, so the YouTube Music UI can be laid out on a
// phone-sized viewport in a plain browser. Dev only, never shipped. Answers from real responses
// recorded by `cargo run -p innertube --example fixtures` (window.__FIXTURES__), and fakes a song
// that is playing so the player bar, now-playing view, queue and lyrics have something to show.
(function () {
	const FX = window.__FIXTURES__ || {};
	window.__JAM_ANDROID__ = true;
	document.addEventListener('DOMContentLoaded', () => document.documentElement.classList.add('jam-android'));

	const callbacks = new Map();
	let nextId = 1;
	const listeners = new Map(); // event -> [handlerId]

	const queueItems = (FX.next && FX.next.items) || [];
	const first = queueItems[0] || { video_id: 'abc', title: 'Song', artists: 'Artist', duration: '3:30' };
	const nowPlaying = {
		videoId: first.video_id, title: first.title, artists: first.artists, artistId: first.artist_id,
		artistRuns: first.artist_runs, thumbnail: first.thumbnail, duration: first.duration, album: first.album || null,
		streamClient: 'ANDROID_VR', rating: 'indifferent', isVideo: false,
	};
	const lyricsLines = [
		'Under the city lights we were running', 'Chasing the echo of a song', 'Every word that you said kept on humming',
		'Like it was there all along', '', 'Oh, turn it up, turn it up tonight', "We'll be dancing till the morning light",
		'Oh, turn it up, turn it up tonight', 'Nothing in the world could feel this right', '',
		'Neon colours on the window', 'Painting shadows on the wall', 'Every heartbeat is a tempo', 'And we never miss a call',
	];
	const settings = Object.assign({ locale: 'en', theme: 'dark', accent: 'red' }, FX.settings || {});

	// Downloads (downloads.rs): a few on the phone, one running, one waiting, one failed.
	const OFFLINE = location.hash === '#offline';
	const dl = [];
	queueItems.slice(1, 7).forEach((s, i) => dl.push({ videoId: s.video_id, song: s, status: 'done', progress: 1, size: 3_400_000 + i * 410_000, quality: 'HIGH', addedAt: 1000 - i, doneAt: 2000 - i, cover: null }));
	if (!OFFLINE) {
		const more = queueItems.slice(7, 10);
		if (more[0]) dl.push({ videoId: more[0].video_id, song: more[0], status: 'downloading', progress: 0.42, size: 0, quality: 'HIGH', addedAt: 3000 });
		if (more[1]) dl.push({ videoId: more[1].video_id, song: more[1], status: 'queued', progress: 0, size: 0, quality: 'HIGH', addedAt: 3001 });
		if (more[2]) dl.push({ videoId: more[2].video_id, song: more[2], status: 'failed', progress: 0, size: 0, quality: 'HIGH', addedAt: 2999, error: 'YouTube refused the download (403)' });
	}
	const dlChanged = () => setTimeout(() => emit('downloads-changed', null), 0);

	const T = {
		downloads_list: () => dl.slice(),
		download_songs: (a) => { let n = 0; for (const s of a.songs) { if (dl.some((d) => d.videoId === s.video_id)) continue; dl.push({ videoId: s.video_id, song: s, status: 'queued', progress: 0, size: 0, quality: 'HIGH', addedAt: Date.now() }); n++; } dlChanged(); return n; },
		download_remove: (a) => { for (const id of a.ids) { const i = dl.findIndex((d) => d.videoId === id); if (i >= 0) dl.splice(i, 1); } dlChanged(); return null; },
		download_cancel_all: () => { for (let i = dl.length - 1; i >= 0; i--) if (dl[i].status !== 'done') dl.splice(i, 1); dlChanged(); return null; },
		download_retry: () => { dl.forEach((d) => { if (d.status === 'failed') d.status = 'queued'; }); dlChanged(); return 1; },
		download_remove_all: () => { dl.length = 0; dlChanged(); return null; },
		download_storage: () => ({ bytes: dl.filter((d) => d.status === 'done').reduce((a, d) => a + d.size, 0) + 220_000, songs: dl.filter((d) => d.status === 'done').length, free: 41_800_000_000 }),
		get_settings: () => settings,
		set_setting: (a) => { settings[a.key] = a.value; return null; },
		get_playback: () => ({ speed: 1, semitones: 0, reverb: { on: false, preset: 'room', mix: 0.3 }, now: nowPlaying, paused: false, position: 63, duration: 214, volume: 80 }),
		get_queue: () => ({ items: queueItems, currentIndex: 0, shuffle: false, repeat: 'off', sourceName: `${first.title} Radio`, sourceId: null, prevTrack: null }),
		get_account: () => ({ signedIn: false }),
		get_account_identities: () => [],
		get_google_accounts: () => [],
		get_home: () => { if (OFFLINE) throw 'http: error sending request for url (https://music.youtube.com/youtubei/v1/browse)'; return FX.get_home || { chips: [], sections: [] }; },
		get_home_more: () => FX.get_home_more || { chips: [], sections: [] },
		get_moods: () => FX.get_moods || [],
		get_mood_art: () => ({}),
		search_all: () => FX.search_all || { top: [], songs: [], albums: [], artists: [], playlists: [] },
		search_suggestions: () => FX.search_suggestions || { queries: [], items: [] },
		search_cards: () => [],
		search_videos: () => [],
		search: () => (FX.search_all && FX.search_all.songs) || [],
		get_album: () => FX.get_album,
		get_artist: () => FX.get_artist,
		get_artist_appears_on: () => [],
		get_playlist: () => FX.get_playlist,
		get_playlist_more: () => ({ items: [], continuation: null }),
		get_playlist_suggestions: () => ({ items: [], continuation: null }),
		get_library: () => (FX.get_home ? FX.get_home.sections.flatMap((s) => s.items).filter((i) => i.kind === 'playlist').slice(0, 12) : []),
		local_playlists: () => [],
		get_library_albums: () => (FX.search_all ? FX.search_all.albums : []),
		get_library_artists: () => (FX.search_all ? FX.search_all.artists : []),
		get_library_subscriptions: () => [],
		get_upload_albums: () => [],
		get_history: () => [{ title: 'Today', items: queueItems.slice(0, 8) }],
		get_local_library: () => ({ folders: [], songs: [], albums: [], artists: [] }),
		get_blocked_artists: () => [],
		play_counts: () => ({}),
		playlist_index: () => ({}),
		sync_playlist_index: () => ({}),
		log_ui: () => null,
		get_lyrics: () => ({
			source: 'LRCLIB', provider: 'lrclib', synced: true, instrumental: false, pinned: false, offset_ms: 0,
			lines: lyricsLines.map((text, i) => ({ time_ms: 50000 + i * 3500, text })),
		}),
		lyrics_providers: () => [{ id: 'youtube', name: 'YouTube Music', on: true }, { id: 'lrclib', name: 'LRCLIB', on: true }],
		get_comments: () => ({ state: 'ok', header: null, threads: [], continuation: null }),
		listening_stats: () => null,
		top_artists: () => [],
		get_stream_clients: () => [],
		get_global_hotkeys: () => ({ enabled: false, bindings: {} }),
		global_hotkeys_on_wayland: () => false,
		lastfm_status: () => ({ connected: false }),
		listenbrainz_status: () => ({ connected: false }),
		lt_get_state: () => null,
		take_launch_args: () => [],
		show_main: () => true,
		network_reachable: () => true,
		can_self_update: () => false,
		check_beta_update: () => null,
		release_notes: () => [],
		app_icon_path: () => null,
		import_status: () => null,
		video_stream: () => null,
		native_video_rect: () => false,
		diagnostics_summary: () => 'mobile lab',
		'plugin:updater|check': () => null,
		'plugin:app|version': () => '1.4.0',
		'plugin:app|name': () => 'Jam',
		'plugin:app|tauri_version': () => '2.11.5',
		'plugin:window|is_maximized': () => false,
		'plugin:window|is_fullscreen': () => false,
		'plugin:window|scale_factor': () => 2,
		'plugin:window|inner_size': () => ({ width: 824, height: 1830 }),
		'plugin:webview|set_webview_zoom': () => null,
		'plugin:event|listen': (a) => {
			const arr = listeners.get(a.event) || [];
			arr.push(a.handler);
			listeners.set(a.event, arr);
			return a.handler;
		},
		'plugin:event|unlisten': () => null,
		'plugin:event|emit': (a) => { emit(a.event, a.payload); return null; },
	};

	function emit(event, payload) {
		for (const h of listeners.get(event) || []) {
			const cb = callbacks.get(h);
			if (cb) cb({ event, id: h, payload });
		}
	}
	window.__labEmit = emit;
	window.__labLog = [];

	window.__TAURI_EVENT_PLUGIN_INTERNALS__ = { unregisterListener: () => {} };
	window.__TAURI_INTERNALS__ = {
		metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main', windowLabel: 'main' } },
		__jam: true,
		transformCallback(cb, once) {
			const id = nextId++;
			callbacks.set(id, (data) => { if (once) callbacks.delete(id); return cb && cb(data); });
			return id;
		},
		unregisterCallback(id) { callbacks.delete(id); },
		runCallback(id, data) { const cb = callbacks.get(id); if (cb) cb(data); },
		callbacks,
		convertFileSrc: (p) => p,
		invoke(cmd, args) {
			const f = T[cmd];
			if (!f) {
				window.__labLog.push(cmd);
				// Commands with nothing to show: lists come back empty, the rest null.
				return Promise.resolve(/^(get_|list|search)/.test(cmd) ? [] : null);
			}
			try { return Promise.resolve(f(args || {})); } catch (e) { return Promise.reject(String(e)); }
		},
	};
	window.isTauri = true;
	// The position ticks like a real song.
	let pos = 63;
	setInterval(() => { pos += 1; emit('position', { position: pos }); }, 1000);
})();
