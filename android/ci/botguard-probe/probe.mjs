// Jam for Android, dev only: runs the Android BotGuard worker (the same three assets the app
// loads) in headless Chrome against YouTube, N bootstraps, and prints which integrity-token class
// each lands in (accepted: <= 64 bytes, see ytm/src-tauri/src/potoken/botguard.rs). Usage:
//   node probe.mjs <assets dir> [bootstraps]
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const puppeteer = require('puppeteer-core');

const [assets, nArg] = process.argv.slice(2);
const N = Number(nArg || 6);
const KEY = 'AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw', REQ = 'O43z0dpjhgX20SCx4KAo';
const JSDOM_UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.10 Safari/605.1.1';
const WEB_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
const hdr = { 'content-type': 'application/json+protobuf', 'x-goog-api-key': KEY, 'x-user-agent': 'grpc-web-javascript/0.1', 'user-agent': WEB_UA };
const post = async (u, body) => { const r = await fetch(u, { method: 'POST', headers: hdr, body: JSON.stringify(body) }); if (!r.ok) throw new Error(u + ' ' + r.status); return r.json(); };

async function challenge() {
	const resp = await post('https://www.youtube.com/api/jnn/v1/Create', [REQ]);
	let c = typeof resp[1] === 'string'
		? JSON.parse(Buffer.from(Buffer.from(resp[1], 'base64').map((b) => (b + 97) & 255)).toString())
		: resp[0];
	const first = (a) => Array.isArray(a) ? a.find((x) => typeof x === 'string' && x) : null;
	let interp = first(c[1]);
	if (!interp) { let u = first(c[2]); if (u.startsWith('//')) u = 'https:' + u; interp = await (await fetch(u)).text(); }
	return { interp, program: c[4], globalName: c[5] };
}
const b64len = (s) => Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64').length;

const browser = await puppeteer.launch({ executablePath: process.env.CHROME || '/usr/bin/google-chrome', headless: 'new', args: ['--no-sandbox'] });
const results = [];
for (let i = 1; i <= N; i++) {
	const page = await browser.newPage();
	await page.setRequestInterception(true);
	page.on('request', (r) => {
		const u = new URL(r.url());
		if (u.host !== 'localhost') return r.continue();
		if (u.pathname.startsWith('/jam-asset/')) return r.respond({ status: 200, contentType: 'text/javascript', body: fs.readFileSync(path.join(assets, path.basename(u.pathname))) });
		return r.respond({ status: 200, contentType: 'text/html', body: '<!doctype html><title>h</title>' });
	});
	page.on('console', (m) => process.stdout.write(`  [page] ${m.text()}\n`));
	await page.goto('https://localhost/');
	try {
		await page.evaluate(async () => {
			const parts = ['/jam-asset/bg_worker_head.js', '/jam-asset/bg_bundle.js', '/jam-asset/bg_worker_tail.js'];
			const srcs = await Promise.all(parts.map((u) => fetch(u).then((r) => r.text())));
			const pending = {}; let n = 0;
			await new Promise((ready, failed) => {
				const w = new Worker(URL.createObjectURL(new Blob([srcs.join('\n;\n')], { type: 'text/javascript' })));
				w.onerror = (e) => failed(new Error(e.message));
				w.onmessage = (e) => { const m = e.data; if (m.id === 0) return ready(); const p = pending[m.id]; delete pending[m.id]; m.ok ? p.res(m.v) : p.rej(new Error(m.e)); };
				window.__bgCall = (fn, args) => new Promise((res, rej) => { const id = ++n; pending[id] = { res, rej }; w.postMessage({ id, fn, args }); });
			});
		});
		const c = await challenge();
		const bg = await page.evaluate((a) => window.__bgCall('runBotguard', a), [c.interp, c.program, c.globalName, JSDOM_UA]);
		const [it, ttl] = await post('https://www.youtube.com/api/jnn/v1/GenerateIT', [REQ, bg]);
		await page.evaluate((t) => window.__bgCall('newMinter', [t]), it);
		const pot = await page.evaluate(() => window.__bgCall('mint', ['limusicprobe']));
		const itLen = b64len(it), fromPot = b64len(pot) - 'limusicprobe'.length - 14;
		const cls = itLen <= 64 ? 'ACCEPTED' : 'rejected';
		results.push(cls);
		console.log(`bootstrap ${i}: integrity token ${itLen} bytes (from pot: ${fromPot}), ttl ${ttl}s -> ${cls}`);
	} catch (e) {
		results.push('error');
		console.log(`bootstrap ${i}: ERROR ${String(e.message || e).slice(0, 300)}`);
	}
	await page.close();
}
await browser.close();
console.log(`summary: ${results.filter((r) => r === 'ACCEPTED').length}/${N} accepted, ${results.filter((r) => r === 'error').length} errors`);
