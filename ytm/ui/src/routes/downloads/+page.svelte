<script lang="ts">
	// Downloads (Jam for Android): what's on this phone for offline listening, and what is still on
	// its way. Everything here comes from SQLite and the disk (downloads.rs), so the page opens and
	// plays with no connection at all, which is the point of it.
	import { onMount } from 'svelte';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		AlertCircleIcon,
		Cancel01Icon,
		Download04Icon,
		NoInternetIcon,
		PlayIcon,
		RefreshIcon,
		ShuffleIcon
	} from '@hugeicons/core-free-icons';
	import TrackRow from '$lib/components/TrackRow.svelte';
	import TrackFilter, { filterTracks } from '$lib/components/TrackFilter.svelte';
	import * as api from '$lib/api';
	import {
		cancelAllDownloads,
		downloads,
		formatBytes,
		progressOf,
		refreshDownloads,
		removeDownloads,
		retryDownloads
	} from '$lib/downloads.svelte';
	import { openAddManyToPlaylist, openPlayer, playback, toast } from '$lib/player.svelte';
	import { phone } from '$lib/phone';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';

	onMount(() => void refreshDownloads());

	let online = $state(typeof navigator === 'undefined' ? true : navigator.onLine);
	onMount(() => {
		const up = () => (online = true);
		const down = () => (online = false);
		window.addEventListener('online', up);
		window.addEventListener('offline', down);
		return () => {
			window.removeEventListener('online', up);
			window.removeEventListener('offline', down);
		};
	});

	const done = $derived(downloads.items.filter((d) => d.status === 'done'));
	const active = $derived(
		downloads.items
			.filter((d) => d.status === 'downloading' || d.status === 'queued')
			// Running first, then the queue in the order it was asked for.
			.sort((a, b) =>
				a.status === b.status ? a.addedAt - b.addedAt : a.status === 'downloading' ? -1 : 1
			)
	);
	const failed = $derived(downloads.items.filter((d) => d.status === 'failed'));
	const songs = $derived(done.map((d) => d.song));
	const bytes = $derived(done.reduce((a, d) => a + (d.size || 0), 0));

	let query = $state('');
	const shown = $derived(filterTracks(songs, query));
	const nowId = $derived(playback.now?.videoId);

	// Of everything asked for since the queue was last empty, how far along it is.
	const overall = $derived.by(() => {
		if (!active.length) return 0;
		const sum = active.reduce((a, d) => a + progressOf(d.videoId), 0);
		return sum / active.length;
	});

	async function play(start: number | null, shuffle = false) {
		if (!shown.length) return;
		try {
			openPlayer();
			await api.playPlaylist(shown, start, undefined, t('downloads.title'), shuffle);
		} catch (e) {
			toast.error(String(e));
		}
	}
</script>

<div class="content-in {phone ? 'px-4 pb-8 pt-4' : 'p-6'}" data-page="downloads">
	<!-- Header -->
	<div class="flex {phone ? 'flex-col items-center gap-4 text-center' : 'items-end gap-5'}">
		<div
			class="flex shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-primary/80 to-primary/30 text-primary-foreground shadow-lg"
			style={phone ? 'width:min(44vw,10rem);height:min(44vw,10rem)' : 'width:7rem;height:7rem'}
		>
			<HugeiconsIcon icon={Download04Icon} class={phone ? 'h-16 w-16' : 'h-12 w-12'} strokeWidth={1.6} />
		</div>
		<div class="min-w-0">
			<h1 data-page-title class="font-heading {phone ? 'text-2xl' : 'text-4xl'} font-bold tracking-tight">
				{t('downloads.title')}
			</h1>
			{#if done.length}
				<p class="mt-1 text-sm text-muted-foreground">
					{done.length === 1
						? t('downloads.subtitle_one', { size: formatBytes(bytes) })
						: t('downloads.subtitle', { count: done.length, size: formatBytes(bytes) })}
				</p>
			{/if}
		</div>
	</div>

	{#if done.length}
		<div class="mt-5 flex items-center gap-3 {phone ? 'justify-center' : ''}">
			<button
				data-downloads-play
				class="flex cursor-pointer items-center gap-2 rounded-full bg-primary px-6 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90"
				onclick={() => play(null)}
			>
				<HugeiconsIcon icon={PlayIcon} class="h-4 w-4" /> {t('player.play')}
			</button>
			<button
				class="flex cursor-pointer items-center gap-2 rounded-full border px-5 py-2.5 text-sm font-semibold transition hover:bg-accent/10"
				onclick={() => play(null, true)}
			>
				<HugeiconsIcon icon={ShuffleIcon} class="h-4 w-4" /> {t('common.shuffle')}
			</button>
		</div>
	{/if}

	{#if !online}
		<div class="mt-5 flex items-center gap-3 rounded-2xl border bg-card px-4 py-3 text-sm" data-downloads-offline>
			<HugeiconsIcon icon={NoInternetIcon} class="h-5 w-5 shrink-0 text-muted-foreground" />
			<span>{t('downloads.offline_banner')}</span>
		</div>
	{/if}

	<!-- On its way -->
	{#if active.length}
		<section class="mt-6 rounded-2xl border bg-card p-4" data-downloads-active>
			<div class="flex items-center justify-between gap-3">
				<div class="min-w-0">
					<p class="text-sm font-semibold">
						{active.length === 1 ? t('downloads.queued_one') : t('downloads.queued', { count: active.length })}
					</p>
				</div>
				<button
					class="shrink-0 cursor-pointer rounded-full border px-3 py-1.5 text-xs font-semibold transition hover:bg-accent/10"
					onclick={() => cancelAllDownloads()}
				>
					{t('downloads.cancel_all')}
				</button>
			</div>
			<div class="mt-3 h-1.5 overflow-hidden rounded-full bg-muted">
				<div
					class="h-full rounded-full bg-primary"
					style="width:{Math.max(2, overall * 100)}%;transition:width .6s linear"
				></div>
			</div>
			<ul class="mt-3 flex flex-col">
				{#each active.slice(0, 50) as d (d.videoId)}
					{@const p = d.status === 'downloading' ? progressOf(d.videoId) : 0}
					<li class="flex items-center gap-3 py-2">
						{#if d.song.thumbnail}
							<img src={thumb(d.song.thumbnail, 96)} alt="" class="h-10 w-10 shrink-0 rounded-md object-cover" loading="lazy" />
						{:else}
							<div class="h-10 w-10 shrink-0 rounded-md bg-muted"></div>
						{/if}
						<div class="min-w-0 flex-1">
							<p class="truncate text-sm font-medium">{d.song.title}</p>
							{#if d.status === 'downloading'}
								<div class="mt-1.5 h-1 overflow-hidden rounded-full bg-muted">
									<div class="h-full bg-primary" style="width:{Math.max(3, p * 100)}%;transition:width .6s linear"></div>
								</div>
							{:else}
								<p class="truncate text-xs text-muted-foreground">{t('downloads.waiting')}</p>
							{/if}
						</div>
						<button
							class="shrink-0 cursor-pointer rounded-full p-2 text-muted-foreground transition hover:bg-accent/10 hover:text-foreground"
							aria-label={t('downloads.cancel_one')}
							onclick={() => removeDownloads([d.videoId])}
						>
							<HugeiconsIcon icon={Cancel01Icon} class="h-4 w-4" />
						</button>
					</li>
				{/each}
			</ul>
			{#if active.length > 50}
				<p class="pt-1 text-xs text-muted-foreground">+{active.length - 50}</p>
			{/if}
		</section>
	{/if}

	{#if failed.length}
		<section class="mt-4 rounded-2xl border border-destructive/30 bg-destructive/5 p-4" data-downloads-failed>
			<div class="flex items-center justify-between gap-3">
				<p class="flex items-center gap-2 text-sm font-semibold">
					<HugeiconsIcon icon={AlertCircleIcon} class="h-4 w-4 text-destructive" />
					{failed.length === 1
						? t('downloads.failed_count_one')
						: t('downloads.failed_count', { count: failed.length })}
				</p>
				<div class="flex shrink-0 items-center gap-2">
					<button
						class="cursor-pointer rounded-full border px-3 py-1.5 text-xs font-semibold transition hover:bg-accent/10"
						onclick={() => removeDownloads(failed.map((d) => d.videoId))}
					>
						{t('common.remove')}
					</button>
					<button
						class="flex cursor-pointer items-center gap-1.5 rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition hover:opacity-90"
						onclick={() => retryDownloads()}
					>
						<HugeiconsIcon icon={RefreshIcon} class="h-3.5 w-3.5" /> {t('downloads.retry_all')}
					</button>
				</div>
			</div>
			<ul class="mt-2 flex flex-col">
				{#each failed.slice(0, 20) as d (d.videoId)}
					<li class="py-1.5 text-sm">
						<span class="font-medium">{d.song.title}</span>
						{#if d.error}<span class="block truncate text-xs text-muted-foreground">{d.error}</span>{/if}
					</li>
				{/each}
			</ul>
		</section>
	{/if}

	<!-- What's on the phone -->
	{#if done.length}
		<div class="mt-6">
			{#if done.length > 8}
				<div class="mb-2 {phone ? 'px-1' : ''}">
					<TrackFilter bind:value={query} placeholder={t('common.search_this_list')} />
				</div>
			{/if}
			{#each shown as song, i (song.video_id)}
				<TrackRow
					{song}
					active={song.video_id === nowId}
					onplay={() => play(i)}
					onAdd={() => openAddManyToPlaylist([song])}
					lazy
				/>
			{/each}
		</div>
	{:else if downloads.loaded && !active.length && !failed.length}
		<div class="mx-auto mt-10 flex max-w-sm flex-col items-center gap-2 text-center" data-downloads-empty>
			<p class="text-base font-semibold">{t('downloads.empty_title')}</p>
			<p class="text-sm text-muted-foreground">{t('downloads.empty_body')}</p>
		</div>
	{/if}
</div>
