<script lang="ts">
	// Offline (Jam for Android): how much of this list can play, above its tracks.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { NoInternetIcon } from '@hugeicons/core-free-icons';
	import type { SongItem } from '$lib/api';
	import { downloadsEnabled, net, playableOffline } from '$lib/downloads.svelte';
	import { t } from '$lib/i18n.svelte';

	let { songs }: { songs: SongItem[] } = $props();
	const show = $derived(downloadsEnabled && !net.online && songs.length > 0);
	const count = $derived(show ? playableOffline(songs) : 0);
</script>

{#if show}
	<p class="mb-2 flex items-center gap-2 px-2 text-xs text-muted-foreground" data-offline-note>
		<HugeiconsIcon icon={NoInternetIcon} class="h-3.5 w-3.5 shrink-0" />
		{t('downloads.offline_count', { count, total: songs.length })}
	</p>
{/if}
