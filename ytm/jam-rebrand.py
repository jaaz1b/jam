#!/usr/bin/env python3
"""Rebrand the Limusic fork in ytm/ as Jam's YouTube Music app.

Idempotent: safe to run again, and meant to be re-run after pulling a newer Limusic into ytm/
(see ytm/UPSTREAM). It only changes what the user sees; code identifiers, storage keys, and the
names the engine reports to third-party services (lyrics, ListenBrainz) keep crediting Limusic.

    python3 ytm/jam-rebrand.py
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent
UI = ROOT / 'ui' / 'src' / 'lib'
RS = ROOT / 'src-tauri' / 'src'
changed = []


def edit(path, pairs, required=True):
    p = pathlib.Path(path)
    s = p.read_text(encoding='utf-8')
    out = s
    for old, new in pairs:
        if old in out:
            out = out.replace(old, new)
        elif new not in out and required:
            print(f'!! pattern not found in {p.relative_to(ROOT)}: {old[:70]!r}', file=sys.stderr)
    if out != s:
        p.write_text(out, encoding='utf-8')
        changed.append(str(p.relative_to(ROOT)))


# ---------------------------------------------------------------- UI strings, every language
# Keys whose "Limusic" refers to the upstream project itself, not to this app: keep them.
KEEP = {'kofi', 'kofi_hint', 'language_contribute'}
CREDIT = "Jam's YouTube Music app, built on Limusic by SimoHypers (GPL-3.0). Thank you!"


def rebrand(node, key=None, path=()):
    if isinstance(node, dict):
        return {k: rebrand(v, k, path + (k,)) for k, v in node.items()}
    if isinstance(node, list):
        return [rebrand(v, key, path) for v in node]
    if isinstance(node, str) and key not in KEEP:
        return node.replace('Limusic', 'Jam')
    return node


for f in sorted((UI / 'locales').glob('*.json')):
    raw = f.read_text(encoding='utf-8')
    data = json.loads(raw)
    new = rebrand(data)
    if f.name == 'en.json' and isinstance(new.get('settings', {}).get('about'), dict):
        new['settings']['about']['description'] = CREDIT
    indent = '\t' if '\n\t' in raw else 2
    text = json.dumps(new, ensure_ascii=False, indent=indent) + ('\n' if raw.endswith('\n') else '')
    if text != raw:
        f.write_text(text, encoding='utf-8')
        changed.append(str(f.relative_to(ROOT)))

# ---------------------------------------------------------------- UI components
edit(UI / 'components' / 'Sidebar.svelte', [
    ("{wide('lg:block')}\">Limusic</span>", "{wide('lg:block')}\">Jam</span>"),
])
edit(UI / 'components' / 'Titlebar.svelte', [('\t\tLimusic\n', '\t\tJam\n')], required=False)
edit(UI / 'components' / 'SettingsDialog.svelte', [
    ('<span class="font-heading text-lg font-bold">Limusic</span>',
     '<span class="font-heading text-lg font-bold">Jam · YouTube Music</span>'),
    # Inside Jam, Jam shows one Discord presence for every source; this app's own Discord tab
    # would only configure a presence that is switched off.
    ("\t\t{ id: 'discord', label: t('settings.tabs.discord'),",
     "\t\t...((globalThis as any).__TAURI_INTERNALS__?.__jam ? [] : [{ id: 'discord' as const, label: t('settings.tabs.discord'),"),
])
# close the conditional spread opened above (same line)
p = UI / 'components' / 'SettingsDialog.svelte'
s = p.read_text(encoding='utf-8')
s2 = re.sub(r"(\.\.\.\(\(globalThis as any\)\.__TAURI_INTERNALS__\?\.__jam \? \[\] : \[\{ id: 'discord' as const, label: t\('settings\.tabs\.discord'\), hint: t\('settings\.tabs\.discord_hint'\), icon: DiscordIcon \})(,)",
            r"\1]),", s)
if s2 != s:
    p.write_text(s2, encoding='utf-8')
edit(UI / 'components' / 'DiscordCard.svelte', [("cfg.app_name.trim() || 'Limusic'", "cfg.app_name.trim() || 'Jam'")], required=False)

# ---------------------------------------------------------------- engine: names the user sees
edit(RS / 'mini.rs', [('.title("Limusic Mini Player")', '.title("Jam Mini Player")')])
edit(RS / 'notify.rs', [('.appname("Limusic")', '.appname("Jam")')])
edit(RS / 'media.rs', [('display_name: "Limusic"', 'display_name: "Jam"')])
edit(RS / 'lib.rs', [
    ('"Limusic could not open or create its database"', '"Jam (YouTube Music) could not open or create its database"'),
    ('fatal("Limusic could not start its network client"', 'fatal("Jam (YouTube Music) could not start its network client"'),
    ('"Limusic could not load libmpv, which it uses to play audio"', '"Jam (YouTube Music) could not load libmpv, which it uses to play audio"'),
    ('"Limusic could not start its audio event loop"', '"Jam (YouTube Music) could not start its audio event loop"'),
])
edit(RS / 'diagnostics.rs', [('"Limusic {} ({} {}, {})",', '"Jam YouTube Music {} ({} {}, {}), based on Limusic",')], required=False)

print('rebranded:' if changed else 'already rebranded', *changed, sep='\n  ')
