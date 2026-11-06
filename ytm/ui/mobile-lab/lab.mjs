// Jam mobile lab: screenshots of the YouTube Music UI on a phone-sized screen, against recorded
// responses (see mock.js). Dev only.
//
//   node mobile-lab/lab.mjs <fixtures dir> <out dir> [scene ...]
//
// Needs the UI dev server on :5183 (`pnpm dev`), puppeteer-core (NODE_PATH) and Chrome
// (CHROME=/usr/bin/google-chrome). Scenes: home search album artist playlist library history np
// npq npl menu settings; default all.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const puppeteer = require('puppeteer-core');

const [fxDir, outDir, ...wanted] = process.argv.slice(2);
const BASE = process.env.LAB_URL || 'http://localhost:5183';
const fixtures = {};
for (const f of fs.readdirSync(fxDir)) if (f.endsWith('.json')) fixtures[f.slice(0, -5)] = JSON.parse(fs.readFileSync(path.join(fxDir, f), 'utf8'));
const mock = fs.readFileSync(new URL('./mock.js', import.meta.url), 'utf8');
fs.mkdirSync(outDir, { recursive: true });

const idOf = (fx, kind) => {
	let found = null;
	const walk = (v) => {
		if (found || !v || typeof v !== 'object') return;
		if (!Array.isArray(v) && v.kind === kind && v.id) { found = v.id; return; }
		Object.values(v).forEach(walk);
	};
	walk(fx);
	return found;
};
const albumId = idOf(fixtures.search_all, 'album') || 'MPREb';
const artistId = idOf(fixtures.search_all, 'artist') || 'UC';
const playlistId = idOf(fixtures.get_home, 'playlist') || 'VL';

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const clickSel = (sel) => async (p) => { await p.waitForSelector(sel, { timeout: 4000 }).catch(() => {}); await p.$eval(sel, (e) => e.click()).catch((e) => console.log('click failed', sel, e.message)); await wait(900); };
const SCENES = {
	home: { path: '/' },
	homescroll: { path: '/', act: async (p) => { await p.evaluate(() => document.querySelector('[data-app-main]')?.scrollBy(0, 900)); await wait(700); } },
	search: { path: '/search?q=daft%20punk' },
	searchempty: { path: '/search' },
	album: { path: `/album/${albumId}` },
	artist: { path: `/artist/${artistId}` },
	playlist: { path: `/playlist/${playlistId}` },
	library: { path: '/library' },
	history: { path: '/history' },
	stats: { path: '/stats' },
	np: { path: '/', act: clickSel('[data-player-art]') },
	npq: { path: '/', act: async (p) => { await clickSel('[data-player-art]')(p); await clickSel('[data-np-tab="queue"]')(p); } },
	npl: { path: '/', act: async (p) => { await clickSel('[data-player-art]')(p); await clickSel('[data-np-tab="lyrics"]')(p); } },
	more: { path: '/', act: clickSel('[data-mobile-nav="more"]') },
	settings: { path: '/', act: async (p) => { await clickSel('[data-mobile-nav="more"]')(p); await clickSel('[data-mobile-more="settings"]')(p); } },
	settingsgen: { path: '/', act: async (p) => { await clickSel('[data-mobile-nav="more"]')(p); await clickSel('[data-mobile-more="settings"]')(p); await clickSel('[data-settings-nav] button')(p); } },
	settingsplay: { path: '/', act: async (p) => { await clickSel('[data-mobile-nav="more"]')(p); await clickSel('[data-mobile-more="settings"]')(p); await clickSel('[data-settings-nav] button:nth-of-type(3)')(p); } },
	settingslook: { path: '/', act: async (p) => { await clickSel('[data-mobile-nav="more"]')(p); await clickSel('[data-mobile-more="settings"]')(p); await clickSel('[data-settings-nav] button:nth-of-type(2)')(p); } },
	menu: { path: `/album/${albumId}`, act: async (p) => { await wait(800); const b = await p.$$('[aria-label="Track options"], [aria-label="More options"]'); if (b[0]) { await b[0].click(); await wait(900); } } },
	addpl: { path: '/', act: async (p) => { await clickSel('[data-player-art]')(p); await clickSel('[aria-label="Save to playlist"]')(p); } },
	effects: { path: '/', act: async (p) => { await clickSel('[data-player-art]')(p); await clickSel('[aria-label="Speed & pitch"]')(p); } },
	lt: { path: '/', act: async (p) => { await clickSel('[data-mobile-nav="more"]')(p); await clickSel('[data-mobile-more="listen-together"]')(p); } },
	account: { path: '/', act: clickSel('header [aria-haspopup], header button:last-child') },
	downloads: { path: '/downloads' },
	downloadsscroll: { path: '/downloads', act: async (p) => { await p.evaluate(() => document.querySelector('[data-app-main]')?.scrollBy(0, 600)); await wait(500); } },
	dlmenu: { path: '/downloads', act: async (p) => { await wait(500); const b = await p.$$('[aria-label="Track options"]'); if (b[0]) { await b[0].click(); await wait(900); } } },
	dlconfirm: { path: '/downloads', act: async (p) => { await wait(500); const b = await p.$$('[aria-label="Track options"]'); if (b[0]) { await b[0].click(); await wait(700); } await clickSel('[data-menu-item="download"]')(p); } },
	offline: { path: '/#offline' },
	offlinepl: { path: `/playlist/${playlistId}#offline` },
	offlinealbum: { path: `/album/${albumId}#offline` },
	offlineplay: { path: `/playlist/${playlistId}#offline`, act: async (p) => { await wait(600); const rows = await p.$$('[data-offline-missing]'); if (rows[0]) { await rows[0].click(); await wait(400); } } },
	offlinedl: { path: '/downloads#offline' },
	more2: { path: '/', act: clickSel('[data-mobile-nav="more"]') },
	settingsdl: { path: '/', act: async (p) => { await clickSel('[data-mobile-nav="more"]')(p); await clickSel('[data-mobile-more="settings"]')(p); await clickSel('[data-settings-nav] button:nth-of-type(3)')(p); await p.evaluate(() => document.querySelector('[data-settings-downloads]')?.scrollIntoView()); await wait(500); } },
};

const browser = await puppeteer.launch({
	executablePath: process.env.CHROME || '/usr/bin/google-chrome',
	headless: 'new',
	args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars'],
});
const imgDir = path.join(fxDir, 'img');
const keyOf = (u) => crypto.createHash('sha1').update(u.replace(/=[wsh]\d+[^/?#]*$/, '')).digest('hex');
const names = wanted.length ? wanted : Object.keys(SCENES);
for (const name of names) {
	const sc = SCENES[name];
	if (!sc) { console.log('unknown scene', name); continue; }
	const page = await browser.newPage();
	await page.setViewport({ width: 412, height: 892, deviceScaleFactor: Number(process.env.LAB_DPR || 1.5), isMobile: true, hasTouch: true });
	await page.setUserAgent('Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36');
	await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: process.env.LAB_LIGHT ? 'light' : 'dark' }]);
	await page.evaluateOnNewDocument(`window.__FIXTURES__=${JSON.stringify(fixtures)};`);
	await page.evaluateOnNewDocument(mock);
	await page.setRequestInterception(true);
	page.on('request', (req) => {
		const u = req.url();
		if (/googleusercontent|ytimg|ggpht/.test(u)) {
			const f = path.join(imgDir, keyOf(u.split('?')[0].replace(/=[^/]*$/, (m) => m)));
			const g = path.join(imgDir, keyOf(u));
			const file = fs.existsSync(g) ? g : fs.existsSync(f) ? f : null;
			const cors = { 'Access-Control-Allow-Origin': '*' };
			if (file) return req.respond({ status: 200, contentType: 'image/jpeg', headers: cors, body: fs.readFileSync(file) });
			return req.respond({ status: 404, headers: cors, body: '' });
		}
		if (!u.startsWith(BASE) && !u.startsWith('data:') && !u.startsWith('blob:')) return req.respond({ status: 404, body: '' });
		req.continue();
	});
	const errors = [];
	page.on('pageerror', (e) => errors.push(String(e.message || e)));
	page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
	await page.goto(BASE + sc.path, { waitUntil: 'networkidle0', timeout: 60000 }).catch((e) => errors.push('goto: ' + e.message));
	await wait(1200);
	if (sc.act) await sc.act(page);
	await wait(600);
	await page.screenshot({ path: path.join(outDir, `${name}.png`) });
	const log = await page.evaluate(() => window.__labLog || []);
	console.log(`${name}: ${errors.length} errors${errors.length ? ' — ' + [...new Set(errors)].slice(0, 4).join(' | ').slice(0, 400) : ''}${log.length ? ' · unmocked: ' + [...new Set(log)].join(',') : ''}`);
	await page.close();
}
await browser.close();
