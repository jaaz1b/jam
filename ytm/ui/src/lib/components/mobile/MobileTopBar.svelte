<script lang="ts">
	// Jam for Android: the slim bar above every page. A tab's name on the three tab roots, a back
	// arrow everywhere else (album, artist, playlist …), and the account menu, which is where
	// signing in to YouTube Music lives.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { ArrowLeft01Icon, Search01Icon } from '@hugeicons/core-free-icons';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { t } from '$lib/i18n.svelte';
	import AccountMenu from '../AccountMenu.svelte';

	const path = $derived(page.url.pathname);
	const root = $derived(path === '/' || path.startsWith('/search') || path.startsWith('/library'));
	const title = $derived(
		path === '/'
			? 'Music'
			: path.startsWith('/search')
				? t('nav.search')
				: path.startsWith('/library')
					? t('nav.library')
					: path.startsWith('/history')
						? t('nav.history')
						: path.startsWith('/stats')
							? t('nav.stats')
							: ''
	);
</script>

<header class="flex h-12 shrink-0 items-center gap-1 bg-background px-1.5">
	{#if root}
		{#if path === '/'}
			<span class="flex items-center gap-2 pl-2.5 text-[19px] font-bold tracking-tight">
				<span class="flex h-6 w-6 items-center justify-center rounded-full bg-primary">
					<span class="ml-0.5 h-0 w-0 border-y-[5px] border-l-[8px] border-y-transparent border-l-primary-foreground"></span>
				</span>
				{title}
			</span>
		{:else}
			<span class="pl-2.5 text-[19px] font-bold tracking-tight">{title}</span>
		{/if}
	{:else}
		<button
			type="button"
			onclick={() => history.back()}
			class="flex size-10 items-center justify-center rounded-full text-foreground active:bg-muted"
			aria-label={t('common.back')}
		>
			<HugeiconsIcon icon={ArrowLeft01Icon} class="h-[22px] w-[22px]" />
		</button>
		{#if title}<span class="text-[17px] font-semibold">{title}</span>{/if}
	{/if}
	<span class="flex-1"></span>
	{#if !path.startsWith('/search')}
		<button
			type="button"
			onclick={() => goto('/search')}
			class="flex size-10 items-center justify-center rounded-full text-foreground active:bg-muted"
			aria-label={t('nav.search')}
		>
			<HugeiconsIcon icon={Search01Icon} class="h-[22px] w-[22px]" />
		</button>
	{/if}
	<div class="flex h-10 items-center [&>button]:rounded-full [&>button]:px-1.5">
		<AccountMenu />
	</div>
</header>
