<script lang="ts">
	// Download a whole album or playlist (Jam for Android), as a round button beside Play / Shuffle.
	// It shows where the set stands: an arrow to start, a filling ring while it downloads, a filled
	// check once every song is on the device. Tapping the check asks to remove them.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { Download04Icon } from '@hugeicons/core-free-icons';
	import type { SongItem } from '$lib/api';
	import { askRemove, downloadable, downloadsEnabled, downloadSongs, setState } from '$lib/downloads.svelte';
	import { goto } from '$app/navigation';
	import { t } from '$lib/i18n.svelte';

	let {
		songs,
		title,
		context,
		prepare,
		class: cls = ''
	}: {
		songs: SongItem[];
		/** The album or playlist's name, for the removal prompt. */
		title: string;
		/** Album pages: artwork and album for rows that leave them out. */
		context?: { thumbnail?: string | null; album?: string | null; albumId?: string | null };
		/** Load what the page hasn't yet (a long playlist's later pages); answers the full list. */
		prepare?: () => Promise<SongItem[]>;
		class?: string;
	} = $props();

	const st = $derived(setState(songs));
	const complete = $derived(st.total > 0 && st.done === st.total);
	const busy = $derived(st.pending > 0);
	let preparing = $state(false);
	const R = 8.5;
	const C = 2 * Math.PI * R;

	async function click() {
		if (busy) {
			goto('/downloads');
			return;
		}
		if (complete) {
			askRemove(songs.filter(downloadable).map((s) => s.video_id), title);
			return;
		}
		let list = songs;
		if (prepare) {
			preparing = true;
			try {
				list = await prepare();
			} finally {
				preparing = false;
			}
		}
		await downloadSongs(list, context);
	}

	const label = $derived(
		complete ? t('downloads.downloaded') : busy ? t('downloads.downloading') : t('downloads.download')
	);
</script>

{#if downloadsEnabled}
	<button
		type="button"
		data-download-button={complete ? 'done' : busy ? 'busy' : 'idle'}
		class="relative flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full border transition hover:bg-accent/10 disabled:opacity-50 {complete
			? 'border-primary bg-primary text-primary-foreground'
			: 'text-foreground'} {cls}"
		onclick={click}
		disabled={!st.total || preparing}
		aria-label={label}
		title={label}
	>
		{#if complete}
			<svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
				<path d="M12 5v11M7 11.5l5 5 5-5M6 20h12" />
			</svg>
		{:else if busy || preparing}
			<svg viewBox="0 0 24 24" class="absolute inset-0 m-auto h-8 w-8 -rotate-90 text-primary">
				<circle cx="12" cy="12" r={R} fill="none" stroke="currentColor" stroke-opacity="0.2" stroke-width="2.4" />
				<circle
					cx="12"
					cy="12"
					r={R}
					fill="none"
					stroke="currentColor"
					stroke-width="2.4"
					stroke-linecap="round"
					stroke-dasharray={C}
					stroke-dashoffset={C * (1 - Math.max(0.03, st.progress))}
					style="transition: stroke-dashoffset 0.6s linear"
				/>
			</svg>
			<span class="text-[9px] font-semibold tabular-nums">{Math.round(st.progress * 100)}</span>
		{:else}
			<HugeiconsIcon icon={Download04Icon} class="h-5 w-5" />
			{#if st.done > 0}
				<!-- Some of it is already here: a dot says so without a number to read. -->
				<span class="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-primary ring-2 ring-background"></span>
			{/if}
		{/if}
	</button>
{/if}
