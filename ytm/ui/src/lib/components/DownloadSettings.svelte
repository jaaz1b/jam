<script lang="ts">
	// Settings ▸ Playback ▸ Downloads (Jam for Android): the quality new downloads are fetched at,
	// what they take up on the phone, and a way to clear them. Modeled on Meld's audio quality and
	// storage settings.
	import { onMount } from 'svelte';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { Delete02Icon, Download04Icon } from '@hugeicons/core-free-icons';
	import { goto } from '$app/navigation';
	import * as AlertDialog from '$lib/components/ui/alert-dialog';
	import * as api from '$lib/api';
	import type { DownloadStorage } from '$lib/api';
	import { downloads, formatBytes, refreshDownloads } from '$lib/downloads.svelte';
	import { toast, ui } from '$lib/player.svelte';
	import { t } from '$lib/i18n.svelte';

	let { settings }: { settings: Record<string, string> } = $props();

	const GROUP = 'mb-7 last:mb-1';
	const LABEL = 'mb-2 px-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground';
	const CARD = 'divide-y divide-border/60 overflow-hidden rounded-xl border bg-card';

	const QUALITIES = [
		{ id: 'HIGH', label: 'downloads.quality_high', desc: 'downloads.quality_high_desc' },
		{ id: 'NORMAL', label: 'downloads.quality_normal', desc: 'downloads.quality_normal_desc' },
		{ id: 'LOW', label: 'downloads.quality_low', desc: 'downloads.quality_low_desc' }
	] as const;
	// The modal's settings until a pick here (the modal doesn't read it back).
	let picked = $state<string | null>(null);
	const quality = $derived(picked ?? settings.download_quality ?? 'HIGH');

	async function setQuality(q: string) {
		picked = q;
		try {
			await api.setSetting('download_quality', q);
		} catch (e) {
			toast.error(String(e));
		}
	}

	let storage = $state<DownloadStorage | null>(null);
	async function loadStorage() {
		try {
			storage = await api.downloadStorage();
		} catch {
			storage = null;
		}
	}
	onMount(() => void loadStorage());
	// The list changing (a download landing, a removal) changes the numbers.
	$effect(() => {
		downloads.items;
		void loadStorage();
	});

	let confirmOpen = $state(false);
	async function removeAll() {
		confirmOpen = false;
		try {
			await api.downloadRemoveAll();
			toast(t('downloads.removed_all'));
		} catch (e) {
			toast.error(String(e));
		}
		await refreshDownloads();
		await loadStorage();
	}
</script>

<section class={GROUP} data-settings-downloads>
	<h3 class={LABEL}>{t('downloads.settings_title')}</h3>
	<div data-settings-card class={CARD}>
		<div class="px-4 py-3.5">
			<span class="text-sm font-medium">{t('downloads.quality')}</span>
			<p class="mt-1 max-w-prose text-xs leading-relaxed text-muted-foreground">{t('downloads.quality_desc')}</p>
			<div class="mt-3 flex flex-col gap-2" role="radiogroup" aria-label={t('downloads.quality')}>
				{#each QUALITIES as q (q.id)}
					<button
						type="button"
						role="radio"
						aria-checked={quality === q.id}
						data-download-quality={q.id}
						onclick={() => setQuality(q.id)}
						class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors {quality ===
						q.id
							? 'border-primary bg-primary/8'
							: 'hover:bg-accent/10'}"
					>
						<span
							class="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border-2 {quality === q.id
								? 'border-primary'
								: 'border-muted-foreground/50'}"
						>
							{#if quality === q.id}<span class="h-2 w-2 rounded-full bg-primary"></span>{/if}
						</span>
						<span class="min-w-0">
							<span class="block text-sm font-medium">{t(q.label)}</span>
							<span class="block text-xs leading-relaxed text-muted-foreground">{t(q.desc)}</span>
						</span>
					</button>
				{/each}
			</div>
		</div>
		<div class="flex items-center justify-between gap-4 px-4 py-3.5">
			<div class="min-w-0">
				<span class="text-sm font-medium">{t('downloads.storage')}</span>
				<p class="mt-1 text-xs leading-relaxed text-muted-foreground">
					{t('downloads.storage_used', {
						size: formatBytes(storage?.bytes ?? 0),
						count: storage?.songs ?? 0
					})}
					{#if storage?.free != null}
						<br />{t('downloads.storage_free', { size: formatBytes(storage.free) })}
					{/if}
				</p>
			</div>
			<button
				type="button"
				class="flex shrink-0 cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition hover:bg-accent/10"
				onclick={() => {
					ui.settingsOpen = false;
					goto('/downloads');
				}}
			>
				<HugeiconsIcon icon={Download04Icon} class="h-3.5 w-3.5" /> {t('downloads.title')}
			</button>
		</div>
		<div class="flex items-center justify-between gap-4 px-4 py-3.5">
			<span class="text-sm font-medium">{t('downloads.remove_all')}</span>
			<button
				type="button"
				data-downloads-remove-all
				disabled={!storage?.bytes}
				class="flex shrink-0 cursor-pointer items-center gap-1.5 rounded-full border border-destructive/40 px-3 py-1.5 text-xs font-semibold text-destructive transition hover:bg-destructive/10 disabled:cursor-default disabled:opacity-40"
				onclick={() => (confirmOpen = true)}
			>
				<HugeiconsIcon icon={Delete02Icon} class="h-3.5 w-3.5" /> {t('common.remove')}
			</button>
		</div>
	</div>
</section>

<AlertDialog.Root bind:open={confirmOpen}>
	<AlertDialog.Content>
		<AlertDialog.Header>
			<AlertDialog.Media class="text-muted-foreground">
				<HugeiconsIcon icon={Delete02Icon} />
			</AlertDialog.Media>
			<AlertDialog.Title>{t('downloads.remove_all_title')}</AlertDialog.Title>
			<AlertDialog.Description>{t('downloads.remove_all_desc')}</AlertDialog.Description>
		</AlertDialog.Header>
		<AlertDialog.Footer>
			<AlertDialog.Cancel>{t('common.cancel')}</AlertDialog.Cancel>
			<AlertDialog.Action onclick={removeAll}>{t('common.remove')}</AlertDialog.Action>
		</AlertDialog.Footer>
	</AlertDialog.Content>
</AlertDialog.Root>
