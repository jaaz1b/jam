<script lang="ts">
	// Jam for Android: the bottom tabs. Home, Search and Library are the three places a phone user
	// lives; everything the desktop sidebar and titlebar hold besides them sits behind More.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		Home01Icon,
		Search01Icon,
		LibraryIcon,
		Menu01Icon
	} from '@hugeicons/core-free-icons';
	import { page } from '$app/state';
	import { t } from '$lib/i18n.svelte';
	import { mobile } from './mobile.svelte';

	const tabs = [
		{ href: '/', label: () => t('nav.home'), icon: Home01Icon },
		{ href: '/search', label: () => t('nav.search'), icon: Search01Icon },
		{ href: '/library', label: () => t('nav.library'), icon: LibraryIcon }
	];
	const isActive = (href: string) =>
		href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);
</script>

<nav
	class="flex h-16 shrink-0 items-stretch border-t border-foreground/[0.06] bg-card/95 px-2"
	aria-label={t('nav.home')}
>
	{#each tabs as tab (tab.href)}
		{@const on = isActive(tab.href)}
		<a
			href={tab.href}
			data-mobile-nav={tab.href}
			class="flex flex-1 flex-col items-center justify-center gap-1 text-[11px] font-medium {on
				? 'text-foreground'
				: 'text-muted-foreground'}"
			aria-current={on ? 'page' : undefined}
		>
			<span
				class="flex h-8 w-14 items-center justify-center rounded-full transition-colors {on
					? 'bg-primary/15 text-primary'
					: ''}"
			>
				<HugeiconsIcon icon={tab.icon} class="h-[22px] w-[22px]" strokeWidth={on ? 2 : 1.6} />
			</span>
			{tab.label()}
		</a>
	{/each}
	<button
		type="button"
		data-mobile-nav="more"
		onclick={() => (mobile.moreOpen = true)}
		class="flex flex-1 flex-col items-center justify-center gap-1 text-[11px] font-medium text-muted-foreground"
	>
		<span class="flex h-8 w-14 items-center justify-center rounded-full">
			<HugeiconsIcon icon={Menu01Icon} class="h-[22px] w-[22px]" strokeWidth={1.6} />
		</span>
		{t('nav.more')}
	</button>
</nav>
