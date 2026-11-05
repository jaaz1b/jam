<script lang="ts">
	// "Remove download?" for every place that can take songs off the device. One instance, in the
	// root layout, driven by `downloads.confirm`, so a menu that closes under it doesn't take it along.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { Delete02Icon } from '@hugeicons/core-free-icons';
	import * as AlertDialog from '$lib/components/ui/alert-dialog';
	import { downloads, removeDownloads } from '$lib/downloads.svelte';
	import { t } from '$lib/i18n.svelte';

	let open = $state(false);
	let pending = $state<{ ids: string[]; title: string } | null>(null);
	$effect(() => {
		if (downloads.confirm) {
			pending = downloads.confirm;
			downloads.confirm = null;
			open = true;
		}
	});
</script>

<AlertDialog.Root bind:open>
	<AlertDialog.Content>
		<AlertDialog.Header>
			<AlertDialog.Media class="text-muted-foreground">
				<HugeiconsIcon icon={Delete02Icon} />
			</AlertDialog.Media>
			<AlertDialog.Title>
				{pending && pending.ids.length > 1
					? t('downloads.remove_many_title', { count: pending.ids.length })
					: t('downloads.remove_title')}
			</AlertDialog.Title>
			<AlertDialog.Description>
				{t('downloads.remove_desc', { title: pending?.title ?? '' })}
			</AlertDialog.Description>
		</AlertDialog.Header>
		<AlertDialog.Footer>
			<AlertDialog.Cancel>{t('common.cancel')}</AlertDialog.Cancel>
			<AlertDialog.Action
				data-download-confirm
				onclick={() => {
					if (pending) void removeDownloads(pending.ids);
					open = false;
				}}
			>
				{t('common.remove')}
			</AlertDialog.Action>
		</AlertDialog.Footer>
	</AlertDialog.Content>
</AlertDialog.Root>
