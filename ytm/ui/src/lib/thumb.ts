import { convertFileSrc } from '@tauri-apps/api/core';

// Rewrite a Google image URL to (about) the pixel size a slot actually renders, so WebKitGTK
// doesn't decode a 544px (or 1080p) image for a 40px row. Only lh3/yt3 googleusercontent-style
// URLs carry the size in the URL (`=w544-h544` / `=s576` suffixes). A video's i.ytimg thumb has its
// sizes as separate files that can 404, so only the big player swaps one (see below); anything else
// is returned unchanged.
/**
 * Downloaded songs' saved artwork (downloads.svelte.ts), keyed by the remote URL with its size
 * suffix taken off. Used in place of the network copy, so a downloaded song keeps its cover offline
 * and costs no data online.
 */
const offlineCovers = new Map<string, string>();
const coverKey = (url: string) => url.replace(/=(w\d+-h\d+|s\d+)[^/]*$/, '');
export function setOfflineCover(url: string, path: string): void {
	if (url.startsWith('https://')) offlineCovers.set(coverKey(url), path);
}

export function thumb(url: string | undefined | null, px: number): string | undefined {
	if (!url) return undefined;
	if (offlineCovers.size && url.startsWith('https://')) {
		const saved = offlineCovers.get(coverKey(url));
		if (saved) return convertFileSrc(saved);
	}
	// Local library artwork is a path on this machine, not a URL. The webview can't open a bare
	// path, so hand it through Tauri's asset protocol. Kept here rather than at the command
	// boundary so what gets stored (queue, Shortcuts) stays the real path — which is also what
	// MPRIS needs.
	if (url.startsWith('/') || /^[A-Za-z]:[\\/]/.test(url)) return convertFileSrc(url);
	// Every InnerTube reply, /player included, tops a video's thumbs out at 426x240, which the big
	// player crops square and stretches (#425). Ask for the 1280x720 file instead; not every video
	// has one, so the caller steps down to the original on a miss (`isMissingThumb`).
	const video = /^https:\/\/i\.ytimg\.com\/vi\/([\w-]{11})\//.exec(url);
	if (video && px >= 720) return `https://i.ytimg.com/vi/${video[1]}/maxresdefault.jpg`;
	if (/=w\d+-h\d+/.test(url)) return url.replace(/=w\d+-h\d+/, `=w${px}-h${px}`);
	if (/=s\d+/.test(url)) return url.replace(/=s\d+/, `=s${px}`);
	return url;
}

// i.ytimg answers a size it doesn't have with a 404 *and* a 120x90 grey picture, which a webview
// may load without firing `error`. Read that picture as the miss it is.
export function isMissingThumb(e: Event): boolean {
	const img = e.currentTarget as HTMLImageElement;
	return img.naturalWidth === 120 && img.naturalHeight === 90;
}
