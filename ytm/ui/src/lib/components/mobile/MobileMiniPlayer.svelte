<script lang="ts">
	// Jam for Android: what's playing, docked above the tabs. Tap it (anywhere but its buttons) for
	// the full-screen player; swipe it up to do the same.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { PlayIcon, PauseIcon, NextIcon, MusicNote01Icon } from '@hugeicons/core-free-icons';
	import * as api from '$lib/api';
	import { np, playback } from '$lib/player.svelte';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';

	const pct = $derived(
		playback.duration > 0 ? Math.min(100, Math.max(0, (playback.position / playback.duration) * 100)) : 0
	);
	let startY = 0;
	function open(e: Event) {
		if ((e.target as HTMLElement).closest('button')) return;
		np.open = true;
	}
</script>

<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div
	data-player-bar
	class="relative mx-2 mb-1.5 flex h-[60px] shrink-0 items-center gap-3 overflow-hidden rounded-2xl bg-card pr-1 pl-2 shadow-lg ring-1 ring-foreground/[0.06]"
	onclick={open}
	ontouchstart={(e) => (startY = e.touches[0].clientY)}
	ontouchend={(e) => {
		if (startY - e.changedTouches[0].clientY > 40) np.open = true;
	}}
>
	{#if playback.now?.thumbnail}
		<img
			data-player-art
			src={thumb(playback.now.thumbnail, 120)}
			alt=""
			class="h-11 w-11 shrink-0 rounded-lg object-cover"
		/>
	{:else}
		<div data-player-art class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground/60">
			<HugeiconsIcon icon={MusicNote01Icon} class="h-5 w-5" />
		</div>
	{/if}
	<div class="min-w-0 flex-1">
		<div class="truncate text-[14px] leading-tight font-semibold">{playback.now?.title ?? t('player.not_playing')}</div>
		<div class="mt-0.5 truncate text-[12.5px] leading-tight text-muted-foreground">{playback.now?.artists ?? ''}</div>
	</div>
	<button
		type="button"
		class="flex size-11 shrink-0 items-center justify-center rounded-full active:bg-muted"
		onclick={() => api.togglePause()}
		aria-label={playback.paused ? t('player.play') : t('player.pause')}
		data-play-button
	>
		<HugeiconsIcon icon={PauseIcon} altIcon={PlayIcon} showAlt={playback.paused} class="h-6 w-6" />
	</button>
	<button
		type="button"
		class="flex size-11 shrink-0 items-center justify-center rounded-full active:bg-muted"
		onclick={() => api.nextTrack()}
		aria-label={t('player.next')}
	>
		<HugeiconsIcon icon={NextIcon} class="h-[22px] w-[22px]" />
	</button>
	<div class="absolute inset-x-3 bottom-0 h-[2px] overflow-hidden rounded-full bg-foreground/10">
		<div class="h-full bg-primary transition-[width] duration-1000 ease-linear" style="width:{pct}%"></div>
	</div>
</div>
