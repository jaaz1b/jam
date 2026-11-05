<script lang="ts">
	// Jam for Android: what the desktop sidebar and titlebar hold besides the three tabs, as a
	// bottom sheet off the More tab.
	import { fade, fly } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		Clock01Icon,
		Chart02Icon,
		UserGroupIcon,
		Link01Icon,
		FileImportIcon,
		Settings02Icon,
		Add01Icon,
		Download04Icon
	} from '@hugeicons/core-free-icons';
	import { downloadCounts } from '$lib/downloads.svelte';
	import { goto } from '$app/navigation';
	import { t } from '$lib/i18n.svelte';
	import { ui } from '$lib/player.svelte';
	import { lt } from '$lib/lt.svelte';
	import { openImport } from '$lib/import.svelte';
	import { mobile } from './mobile.svelte';

	const close = () => (mobile.moreOpen = false);
	const items = [
		{ id: 'downloads', label: () => t('downloads.title'), icon: Download04Icon, run: () => goto('/downloads') },
		{ id: 'history', label: () => t('nav.history'), icon: Clock01Icon, run: () => goto('/history') },
		{ id: 'stats', label: () => t('nav.stats'), icon: Chart02Icon, run: () => goto('/stats') },
		{ id: 'new-playlist', label: () => t('nav.new_playlist'), icon: Add01Icon, run: () => (ui.newPlaylist = { songs: [] }) },
		{ id: 'listen-together', label: () => t('nav.listen_together'), icon: UserGroupIcon, run: () => (ui.ltOpen = true) },
		{ id: 'link', label: () => t('dialogs.link.title'), icon: Link01Icon, run: () => (ui.linkOpen = true) },
		{ id: 'import', label: () => t('nav.import'), icon: FileImportIcon, run: () => openImport() },
		{ id: 'settings', label: () => t('nav.settings'), icon: Settings02Icon, run: () => (ui.settingsOpen = true) }
	];
</script>

{#if mobile.moreOpen}
	<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
	<div class="fixed inset-0 z-40 bg-black/50" transition:fade={{ duration: 150 }} onclick={close}></div>
	<div
		class="fixed inset-x-0 bottom-0 z-50 rounded-t-3xl border-t bg-popover pb-3 text-popover-foreground shadow-2xl"
		transition:fly={{ y: 320, duration: 240, easing: cubicOut }}
		role="dialog"
		aria-label={t('nav.more')}
	>
		<div class="mx-auto mt-2.5 mb-2 h-1 w-9 rounded-full bg-foreground/20"></div>
		{#each items as item (item.id)}
			<button
				type="button"
				data-mobile-more={item.id}
				onclick={() => {
					close();
					item.run();
				}}
				class="flex w-full items-center gap-4 px-6 py-3.5 text-left text-[15px] active:bg-muted"
			>
				<HugeiconsIcon icon={item.icon} class="h-[22px] w-[22px] text-muted-foreground" />
				<span class="flex-1">{item.label()}</span>
				{#if item.id === 'downloads' && downloadCounts.pending}
					<span class="rounded-full bg-primary/15 px-2 py-0.5 text-xs font-semibold text-primary">{downloadCounts.pending}</span>
				{/if}
				{#if item.id === 'listen-together' && lt.role !== 'none'}
					<span class="h-2 w-2 rounded-full bg-primary"></span>
				{/if}
			</button>
		{/each}
	</div>
{/if}
