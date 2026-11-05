<script lang="ts">
	// Jam for Android: the full-screen player. The desktop view leaves transport to the player bar
	// underneath it; on a phone this is the whole screen, so it carries the transport itself: seek,
	// shuffle, previous, play, next, repeat. Up next, lyrics and comments open as a sheet over its
	// lower half, the same three tabs the desktop view has.
	import { fade, fly } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { beforeNavigate } from '$app/navigation';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		ArrowDown01Icon,
		FavouriteIcon,
		Add01Icon,
		PlayIcon,
		PauseIcon,
		NextIcon,
		PreviousIcon,
		ShuffleIcon,
		RepeatIcon,
		RepeatOne01Icon,
		Queue01Icon,
		Mic01Icon,
		Comment01Icon,
		PreferenceVerticalIcon,
		MusicNote01Icon,
		GoBackward15SecIcon,
		GoForward15SecIcon
	} from '@hugeicons/core-free-icons';
	import * as api from '$lib/api';
	import {
		np,
		playback,
		cycleRepeat,
		openAddToPlaylist,
		SKIP_SECS,
		skipBy,
		toggleNowPlayingRating,
		ui
	} from '$lib/player.svelte';
	import { canSwitch, claimVideo, parkVideo, pickVersion, showVideo, videoSide } from '$lib/video.svelte';
	import { isMissingThumb, thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';
	import ArtistLine from '../ArtistLine.svelte';
	import M3SeekWave from '../M3SeekWave.svelte';
	import TrackMenu from '../TrackMenu.svelte';
	import QueueList from '../QueueList.svelte';
	import LyricsView from '../LyricsView.svelte';
	import CommentsView from '../CommentsView.svelte';
	import SpeedPicker from '../SpeedPicker.svelte';
	import { mobile } from './mobile.svelte';

	beforeNavigate(() => {
		np.open = false;
		mobile.sheet = null;
	});

	const close = () => {
		np.open = false;
		mobile.sheet = null;
	};

	const fmt = (secs: number) => {
		if (!secs || secs < 0) return '0:00';
		const s = Math.floor(secs);
		const h = Math.floor(s / 3600);
		const m = Math.floor((s % 3600) / 60);
		return `${h ? `${h}:` : ''}${h ? String(m).padStart(2, '0') : m}:${String(s % 60).padStart(2, '0')}`;
	};

	// Same seek rules as the desktop player bar: a held local value while dragging, so the position
	// ticks can't yank the thumb back, and one seek on release. Shown in listening seconds.
	let seekDrag = $state<number | null>(null);
	const shownPosition = $derived((seekDrag ?? playback.position) / playback.speed);
	const shownDuration = $derived(playback.duration / playback.speed);
	const seekPct = $derived(shownDuration > 0 ? Math.min(1, Math.max(0, shownPosition / shownDuration)) : 0);
	const onSeekInput = (e: Event) => (seekDrag = Number((e.target as HTMLInputElement).value) * playback.speed);
	function onSeekCommit(e: Event) {
		const v = Number((e.target as HTMLInputElement).value) * playback.speed;
		playback.position = v;
		seekDrag = null;
		api.seek(v);
	}

	const shuffleOn = $derived(playback.queue.shuffle ?? false);
	const repeat = $derived(playback.queue.repeat ?? 'off');
	const episode = $derived(!!playback.now?.isEpisode);
	const effectsOn = $derived(playback.speed !== 1 || playback.semitones !== 0 || playback.reverb.enabled);
	const currentSong = $derived.by(() => {
		const cur = playback.queue.items[playback.queue.currentIndex];
		return cur?.video_id === playback.now?.videoId ? cur : null;
	});
	const local = $derived(!!playback.now && api.isLocalId(playback.now.videoId));

	let justLiked = $state(false);
	function toggleLike() {
		if (playback.rating !== 'like') justLiked = true;
		toggleNowPlayingRating();
	}
	function addToPlaylist() {
		const now = playback.now!;
		openAddToPlaylist(
			currentSong ?? {
				video_id: now.videoId,
				title: now.title,
				artists: now.artists,
				artist_id: now.artistId,
				thumbnail: now.thumbnail,
				duration: now.duration
			}
		);
	}

	// The cover: crisp first, then smaller sizes the CDN is known to serve, then a note.
	let attempt = $state(0);
	$effect(() => {
		playback.now?.thumbnail;
		attempt = 0;
	});
	const srcs = $derived([720, 400, 120].map((px) => thumb(playback.now?.thumbnail, px)));

	// Swipe the view down to close it, like a sheet.
	let dragY = $state(0);
	let startY = 0;
	let tracking = false;
	function onTouchStart(e: TouchEvent) {
		if ((e.target as HTMLElement).closest('input, [data-np-sheet]')) return;
		tracking = true;
		startY = e.touches[0].clientY;
	}
	function onTouchMove(e: TouchEvent) {
		if (!tracking) return;
		dragY = Math.max(0, e.touches[0].clientY - startY);
	}
	function onTouchEnd() {
		if (!tracking) return;
		tracking = false;
		if (dragY > 120) close();
		dragY = 0;
	}

	const tabs = $derived([
		{ id: 'queue' as const, label: t('player.up_next'), icon: Queue01Icon },
		{ id: 'lyrics' as const, label: t('player.lyrics'), icon: Mic01Icon },
		...(local ? [] : [{ id: 'comments' as const, label: t('player.comments'), icon: Comment01Icon }])
	]);
	function openSheet(id: 'queue' | 'lyrics' | 'comments') {
		mobile.sheet = id;
		np.tab = id;
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	data-np-view
	class="fixed inset-0 z-40 flex flex-col overflow-hidden bg-background text-foreground"
	style={dragY ? `transform:translateY(${dragY}px)` : undefined}
	transition:fly={{ y: '100%', duration: 300, easing: cubicOut }}
	ontouchstart={onTouchStart}
	ontouchmove={onTouchMove}
	ontouchend={onTouchEnd}
>
	<!-- The cover's colours fill the room: a tiny image blown up and blurred, painted once. -->
	{#if playback.now?.thumbnail}
		<img
			src={thumb(playback.now.thumbnail, 60)}
			alt=""
			aria-hidden="true"
			class="pointer-events-none absolute inset-0 h-full w-full scale-150 object-cover opacity-45 blur-3xl dark:opacity-35"
		/>
		<div class="pointer-events-none absolute inset-0 bg-gradient-to-b from-background/30 via-background/60 to-background"></div>
	{/if}

	<!-- Header: close, where it's playing from, the track's menu. -->
	<div class="relative flex h-14 shrink-0 items-center gap-1 px-2">
		<button
			type="button"
			onclick={close}
			class="flex size-11 items-center justify-center rounded-full active:bg-foreground/10"
			aria-label={t('player.minimize_player')}
		>
			<HugeiconsIcon icon={ArrowDown01Icon} class="h-6 w-6" />
		</button>
		<div class="min-w-0 flex-1 text-center">
			{#if playback.queue.sourceName}
				<div class="text-[10.5px] font-semibold tracking-[0.12em] text-muted-foreground uppercase">
					{t('player.playing_from')}
				</div>
				<div class="truncate text-[13px] font-semibold">{playback.queue.sourceName}</div>
			{/if}
		</div>
		{#if currentSong}
			<TrackMenu
				song={currentSong}
				playlistId={playback.queue.sourceId}
				queueIndex={playback.queue.currentIndex}
				onAdd={() => openAddToPlaylist(currentSong!)}
				triggerClass="flex size-11 items-center justify-center rounded-full text-foreground active:bg-foreground/10"
			/>
		{:else}
			<span class="size-11"></span>
		{/if}
	</div>

	<!-- Song / video, when there is a choice. -->
	{#if canSwitch()}
		<div class="relative flex shrink-0 justify-center pb-1">
			<div class="flex rounded-full bg-foreground/[0.08] p-1" role="group">
				{#each [false, true] as on (on)}
					<button
						type="button"
						onclick={() => pickVersion(on)}
						aria-pressed={videoSide() === on}
						class="rounded-full px-5 py-1 text-[13px] font-semibold transition-colors {videoSide() === on
							? 'bg-background text-foreground shadow-sm'
							: 'text-muted-foreground'}"
					>
						{on ? t('player.video') : t('player.song')}
					</button>
				{/each}
			</div>
		</div>
	{/if}

	<!-- The picture: the cover, or the music video. -->
	<div class="relative flex min-h-0 flex-1 items-center justify-center px-7 py-3">
		<button
			type="button"
			class="relative block w-full max-w-[min(100%,calc(100dvh-25rem))]"
			onclick={() => api.togglePause()}
			aria-label={t('a11y.play_pause')}
		>
			<div class="contents" {@attach (box: HTMLElement) => {
				if (ui.theaterOpen) return;
				claimVideo(box);
				return () => parkVideo(box);
			}}></div>
			{#if !showVideo()}
				{#key playback.now?.videoId}
					{#if srcs[attempt] && attempt < srcs.length}
						<img
							src={srcs[attempt]}
							alt=""
							onerror={() => attempt++}
							onload={(e) => isMissingThumb(e) && attempt++}
							style={srcs[2] ? `background-image:url(${srcs[2]})` : undefined}
							class="aspect-square w-full rounded-[20px] bg-cover bg-center object-cover shadow-2xl"
							in:fade={{ duration: 200 }}
						/>
					{:else}
						<div class="flex aspect-square w-full items-center justify-center rounded-[20px] bg-muted text-muted-foreground/40">
							<HugeiconsIcon icon={MusicNote01Icon} class="h-16 w-16" />
						</div>
					{/if}
				{/key}
			{/if}
		</button>
	</div>

	<!-- Title, artist, like, add. -->
	<div class="relative flex shrink-0 items-center gap-2 px-6">
		<div class="min-w-0 flex-1">
			<div class="truncate text-[22px] leading-tight font-bold tracking-tight">{playback.now?.title ?? ''}</div>
			<ArtistLine
				runs={playback.now?.artistRuns}
				text={playback.now?.artists ?? ''}
				class="mt-1 block truncate text-[15px] text-muted-foreground"
			/>
		</div>
		{#if !local}
			<button
				type="button"
				onclick={toggleLike}
				class="flex size-11 shrink-0 items-center justify-center rounded-full active:bg-foreground/10"
				aria-label={t('common.like')}
				aria-pressed={playback.rating === 'like'}
			>
				<span class="inline-flex" class:animate-heart-pop={justLiked} onanimationend={() => (justLiked = false)}>
					<HugeiconsIcon
						icon={FavouriteIcon}
						class="h-[24px] w-[24px] {playback.rating === 'like' ? 'fill-current text-primary' : ''}"
					/>
				</span>
			</button>
		{/if}
		<button
			type="button"
			onclick={addToPlaylist}
			class="flex size-11 shrink-0 items-center justify-center rounded-full active:bg-foreground/10"
			aria-label={t('player.save_to_playlist')}
		>
			<HugeiconsIcon icon={Add01Icon} class="h-[24px] w-[24px]" />
		</button>
	</div>

	<!-- Seek. -->
	<div class="relative shrink-0 px-6 pt-4">
		<div class="m3-seek relative flex items-center" style="--pct:{seekPct * 100}%">
			<M3SeekWave pct={seekPct} paused={playback.paused} duration={shownDuration} dragging={seekDrag !== null} />
			<input
				type="range"
				class="range w-full"
				style="--pct:{seekPct * 100}%"
				min="0"
				max={shownDuration || 0}
				value={shownPosition}
				oninput={onSeekInput}
				onchange={onSeekCommit}
				aria-label={t('player.seek')}
			/>
		</div>
		<div class="mt-1 flex justify-between text-[12px] text-muted-foreground tabular-nums">
			<span>{fmt(shownPosition)}</span>
			<span>{fmt(shownDuration)}</span>
		</div>
	</div>

	<!-- Transport. -->
	<div class="relative flex shrink-0 items-center justify-between px-5 pt-2 pb-3">
		{#if episode}
			<SpeedPicker class="flex size-12 items-center justify-center rounded-full text-sm font-semibold active:bg-foreground/10" />
		{:else}
			<button
				type="button"
				onclick={() => api.toggleShuffle()}
				class="flex size-12 items-center justify-center rounded-full active:bg-foreground/10"
				aria-label={t('player.shuffle')}
				aria-pressed={shuffleOn}
			>
				<HugeiconsIcon icon={ShuffleIcon} class="h-[22px] w-[22px] {shuffleOn ? 'text-primary' : 'text-muted-foreground'}" />
			</button>
		{/if}
		<button
			type="button"
			onclick={() => (episode ? skipBy(-SKIP_SECS) : api.prevTrack())}
			class="flex size-14 items-center justify-center rounded-full active:bg-foreground/10"
			aria-label={episode ? t('player.skip_back', { seconds: SKIP_SECS }) : t('player.previous')}
		>
			<HugeiconsIcon icon={episode ? GoBackward15SecIcon : PreviousIcon} class="h-8 w-8" />
		</button>
		<button
			type="button"
			data-play-button
			onclick={() => api.togglePause()}
			class="flex size-[72px] items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg active:scale-95"
			aria-label={playback.paused ? t('player.play') : t('player.pause')}
		>
			<HugeiconsIcon icon={PauseIcon} altIcon={PlayIcon} showAlt={playback.paused} class="h-9 w-9" />
		</button>
		<button
			type="button"
			onclick={() => (episode ? skipBy(SKIP_SECS) : api.nextTrack())}
			class="flex size-14 items-center justify-center rounded-full active:bg-foreground/10"
			aria-label={episode ? t('player.skip_forward', { seconds: SKIP_SECS }) : t('player.next')}
		>
			<HugeiconsIcon icon={episode ? GoForward15SecIcon : NextIcon} class="h-8 w-8" />
		</button>
		<button
			type="button"
			onclick={cycleRepeat}
			class="flex size-12 items-center justify-center rounded-full active:bg-foreground/10"
			aria-pressed={repeat !== 'off'}
			aria-label={t('player.repeat_state', {
				state: repeat === 'off' ? t('player.repeat_off') : repeat === 'one' ? t('player.repeat_one') : t('player.repeat_all')
			})}
		>
			<HugeiconsIcon
				icon={RepeatIcon}
				altIcon={RepeatOne01Icon}
				showAlt={repeat === 'one'}
				class="h-[22px] w-[22px] {repeat !== 'off' ? 'text-primary' : 'text-muted-foreground'}"
			/>
		</button>
	</div>

	<!-- Up next / Lyrics / Comments, and the effects. -->
	<div class="relative flex h-14 shrink-0 items-center justify-around border-t border-foreground/[0.07] px-2">
		{#each tabs as tab (tab.id)}
			<button
				type="button"
				data-np-tab={tab.id}
				onclick={() => openSheet(tab.id)}
				class="flex h-11 items-center gap-2 rounded-full px-3.5 text-[13px] font-semibold text-muted-foreground active:bg-foreground/10"
			>
				<HugeiconsIcon icon={tab.icon} class="h-[18px] w-[18px]" />
				{tab.label}
			</button>
		{/each}
		<button
			type="button"
			onclick={() => (ui.tempoPitchOpen = true)}
			class="flex size-11 items-center justify-center rounded-full active:bg-foreground/10"
			aria-label={t('player.effects')}
		>
			<HugeiconsIcon icon={PreferenceVerticalIcon} class="h-5 w-5 {effectsOn ? 'text-primary' : 'text-muted-foreground'}" />
		</button>
	</div>

	<!-- The sheet: covers everything under the header, with the song still in reach at its top. -->
	{#if mobile.sheet}
		<div
			data-np-sheet
			class="absolute inset-x-0 top-14 bottom-0 z-10 flex flex-col rounded-t-3xl border-t bg-background shadow-2xl"
			transition:fly={{ y: 600, duration: 260, easing: cubicOut }}
		>
			<div class="flex shrink-0 items-center gap-3 px-4 pt-3 pb-2">
				{#if playback.now?.thumbnail}
					<img src={thumb(playback.now.thumbnail, 120)} alt="" class="h-11 w-11 rounded-lg object-cover" />
				{/if}
				<div class="min-w-0 flex-1">
					<div class="truncate text-[14px] font-semibold">{playback.now?.title ?? ''}</div>
					<div class="truncate text-[12.5px] text-muted-foreground">{playback.now?.artists ?? ''}</div>
				</div>
				<button
					type="button"
					onclick={() => api.togglePause()}
					class="flex size-11 items-center justify-center rounded-full active:bg-muted"
					aria-label={playback.paused ? t('player.play') : t('player.pause')}
				>
					<HugeiconsIcon icon={PauseIcon} altIcon={PlayIcon} showAlt={playback.paused} class="h-6 w-6" />
				</button>
				<button
					type="button"
					onclick={() => (mobile.sheet = null)}
					class="flex size-11 items-center justify-center rounded-full active:bg-muted"
					aria-label={t('common.close')}
				>
					<HugeiconsIcon icon={ArrowDown01Icon} class="h-6 w-6" />
				</button>
			</div>
			<div class="flex shrink-0 gap-1 border-b px-3" role="tablist">
				{#each tabs as tab (tab.id)}
					<button
						type="button"
						role="tab"
						aria-selected={mobile.sheet === tab.id}
						onclick={() => openSheet(tab.id)}
						class="relative flex-1 py-3 text-[13.5px] font-semibold {mobile.sheet === tab.id ? 'text-foreground' : 'text-muted-foreground'}"
					>
						{tab.label}
						{#if mobile.sheet === tab.id}
							<span class="absolute inset-x-6 bottom-0 h-[3px] rounded-t-full bg-primary"></span>
						{/if}
					</button>
				{/each}
			</div>
			<div class="flex min-h-0 flex-1 flex-col">
				{#if mobile.sheet === 'queue'}
					<QueueList />
				{:else if mobile.sheet === 'comments'}
					<CommentsView />
				{:else}
					<LyricsView expanded />
				{/if}
			</div>
		</div>
	{/if}
</div>
