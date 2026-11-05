// YouTube Music downloads for offline listening (src-tauri/src/downloads.rs). Jam for Android has
// the UI now; desktop gets it in the next desktop release (desktop/TODO.md), which is lifting
// `downloadsEnabled` and giving the desktop menus their entries.
import * as api from './api';
import type { DownloadInfo, SongItem } from './api';
import { phone } from './phone';
import { setOfflineCover } from './thumb';
import { toast } from './player.svelte';
import { t } from './i18n.svelte';

/** Whether this build offers downloads at all. */
export const downloadsEnabled = phone;

export const downloads = $state({
	/** Every row, newest first, as Rust last listed them. */
	items: [] as DownloadInfo[],
	/** videoId → its row, for the badges on every track row. */
	byId: {} as Record<string, DownloadInfo>,
	/** Live progress of running downloads, 0..1, between list refreshes. */
	progress: {} as Record<string, number>,
	loaded: false,
	/** Remove-download confirmation (DownloadConfirm.svelte). */
	confirm: null as null | { ids: string[]; title: string }
});

export const downloadCounts = {
	get done() {
		return downloads.items.filter((d) => d.status === 'done').length;
	},
	get pending() {
		return downloads.items.filter((d) => d.status === 'queued' || d.status === 'downloading').length;
	},
	get failed() {
		return downloads.items.filter((d) => d.status === 'failed').length;
	}
};

let refreshing: Promise<void> | null = null;
let again = false;

/** Read the list again (coalesced: a burst of change events is one read, plus one after it). */
export function refreshDownloads(): Promise<void> {
	if (refreshing) {
		again = true;
		return refreshing;
	}
	refreshing = (async () => {
		try {
			do {
				again = false;
				const items = await api.downloadsList();
				const byId: Record<string, DownloadInfo> = {};
				for (const d of items) {
					byId[d.videoId] = d;
					if (d.cover && d.song.thumbnail) setOfflineCover(d.song.thumbnail, d.cover);
				}
				downloads.items = items;
				downloads.byId = byId;
				downloads.loaded = true;
			} while (again);
		} catch {
			// Not on this platform yet, or Rust not up: leave what we had.
		} finally {
			refreshing = null;
		}
	})();
	return refreshing;
}

/** Wire the events once, from the root layout. */
export function initDownloads(): () => void {
	if (!downloadsEnabled) return () => {};
	void refreshDownloads();
	const off: Array<Promise<() => void>> = [
		api.onDownloadsChanged(() => void refreshDownloads()),
		api.onDownloadProgress((p) => (downloads.progress = p))
	];
	return () => off.forEach((u) => u.then((f) => f()));
}

export function statusOf(id: string | undefined | null): DownloadInfo['status'] | undefined {
	return id ? downloads.byId[id]?.status : undefined;
}

export function isDownloaded(id: string | undefined | null): boolean {
	return statusOf(id) === 'done';
}

export function progressOf(id: string): number {
	return downloads.progress[id] ?? downloads.byId[id]?.progress ?? 0;
}

/** Songs that can be downloaded at all: not files already on this device. */
export const downloadable = (s: SongItem) => !!s.video_id && !api.isLocalId(s.video_id);

/**
 * Queue songs. `context` fills in what an album page's rows leave out (their artwork and album),
 * so a song downloaded from there looks right in Downloads and offline.
 */
export async function downloadSongs(
	songs: SongItem[],
	context?: { thumbnail?: string | null; album?: string | null; albumId?: string | null }
): Promise<void> {
	const list = songs.filter(downloadable).map((s) =>
		context
			? {
					...s,
					thumbnail: s.thumbnail ?? context.thumbnail ?? undefined,
					album: s.album ?? context.album ?? undefined,
					album_id: s.album_id ?? context.albumId ?? undefined
				}
			: s
	);
	if (!list.length) return;
	try {
		const n = await api.downloadSongs(list);
		if (n === 0) toast(t('downloads.already'));
		else toast.success(n === 1 ? t('downloads.queued_one') : t('downloads.queued', { count: n }));
	} catch (e) {
		toast.error(String(e));
	}
	await refreshDownloads();
}

/** Ask first, then remove (DownloadConfirm.svelte). */
export function askRemove(ids: string[], title: string) {
	if (ids.length) downloads.confirm = { ids, title };
}

export async function removeDownloads(ids: string[]): Promise<void> {
	try {
		await api.downloadRemove(ids);
		toast(ids.length === 1 ? t('downloads.removed_one') : t('downloads.removed', { count: ids.length }));
	} catch (e) {
		toast.error(String(e));
	}
	await refreshDownloads();
}

export async function cancelAllDownloads(): Promise<void> {
	try {
		await api.downloadCancelAll();
	} catch (e) {
		toast.error(String(e));
	}
	await refreshDownloads();
}

export async function retryDownloads(): Promise<void> {
	try {
		await api.downloadRetry();
	} catch (e) {
		toast.error(String(e));
	}
	await refreshDownloads();
}

/** "12.4 MB", "1.2 GB". */
export function formatBytes(n: number): string {
	if (n < 1024 * 1024) return `${Math.max(0, Math.round(n / 1024))} KB`;
	if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(n < 10 * 1024 * 1024 ? 1 : 0)} MB`;
	return `${(n / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

/** The finished downloads as playable songs, with their saved artwork. */
export function downloadedSongs(): SongItem[] {
	return downloads.items.filter((d) => d.status === 'done').map((d) => d.song);
}

/**
 * Where a set of songs stands, for a Download button on an album or playlist: how many are
 * downloaded, on their way, and how far along the set is overall.
 */
export function setState(songs: SongItem[]) {
	const ids = songs.filter(downloadable).map((s) => s.video_id);
	let done = 0;
	let pending = 0;
	let sum = 0;
	for (const id of ids) {
		const s = downloads.byId[id]?.status;
		if (s === 'done') {
			done++;
			sum += 1;
		} else if (s === 'queued' || s === 'downloading') {
			pending++;
			sum += progressOf(id);
		}
	}
	return { total: ids.length, done, pending, progress: ids.length ? sum / ids.length : 0 };
}
