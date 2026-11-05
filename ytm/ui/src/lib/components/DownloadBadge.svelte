<script lang="ts">
	// Where a song stands with downloads, as a mark at the front of its row's artist line: a filled
	// arrow once it is on this device, a filling ring while it downloads, a dotted ring while it
	// waits its turn. Nothing at all for a song that was never downloaded.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { AlertCircleIcon } from '@hugeicons/core-free-icons';
	import { downloads, downloadsEnabled, progressOf } from '$lib/downloads.svelte';
	import { t } from '$lib/i18n.svelte';

	let { id, class: cls = '' }: { id: string; class?: string } = $props();

	const status = $derived(downloadsEnabled ? downloads.byId[id]?.status : undefined);
	const p = $derived(status === 'downloading' ? progressOf(id) : 0);
	const R = 6;
	const C = 2 * Math.PI * R;
</script>

{#if status === 'done'}
	<span
		class="inline-flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground {cls}"
		title={t('downloads.downloaded')}
		aria-label={t('downloads.downloaded')}
		data-download-badge="done"
	>
		<svg viewBox="0 0 16 16" class="h-2.5 w-2.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
			<path d="M8 3.5v7M4.8 7.6 8 10.8l3.2-3.2" />
		</svg>
	</span>
{:else if status === 'downloading' || status === 'queued'}
	<span
		class="inline-flex h-3.5 w-3.5 shrink-0 text-primary {cls}"
		title={status === 'queued' ? t('downloads.waiting') : t('downloads.downloading')}
		aria-label={status === 'queued' ? t('downloads.waiting') : t('downloads.downloading')}
		data-download-badge={status}
	>
		<svg viewBox="0 0 16 16" class="h-3.5 w-3.5 -rotate-90">
			<circle cx="8" cy="8" r={R} fill="none" stroke="currentColor" stroke-opacity="0.25" stroke-width="2" stroke-dasharray={status === 'queued' ? '2 2.2' : undefined} />
			{#if status === 'downloading'}
				<circle
					cx="8"
					cy="8"
					r={R}
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					stroke-linecap="round"
					stroke-dasharray={C}
					stroke-dashoffset={C * (1 - Math.max(0.04, p))}
					style="transition: stroke-dashoffset 0.6s linear"
				/>
			{/if}
		</svg>
	</span>
{:else if status === 'failed'}
	<span class="inline-flex shrink-0 text-destructive {cls}" title={t('downloads.failed_one')} data-download-badge="failed">
		<HugeiconsIcon icon={AlertCircleIcon} class="h-3.5 w-3.5" />
	</span>
{/if}
