// Desktop saved-state test: Octave pointed at the offline test site (android/ci/offline-pwa),
// saved (pages only, Jam 3.2), reopened from the save with the site still up (no requests may reach
// it, its uncached cover must show from Jam's own copy), then switched back live. Also: a Jam 3.1
// saved state (a copy of the whole session) is deleted at start. Needs xvfb on Linux:
//   xvfb-run -a node desktop/test/saved-state.mjs
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const puppeteer = require(process.env.PUPPETEER || 'puppeteer-core');

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const repo = path.resolve(root, '..');
const home = fs.mkdtempSync(path.join(os.tmpdir(), 'jam-test-'));
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const say = (s) => { console.log(s); results.push(s); };

// The site, with a request log.
const log = [];
const server = spawn('python3', ['-u', path.join(repo, 'android', 'ci', 'serve.py'), '8765'], { stdio: ['ignore', 'ignore', 'pipe'] });
server.stderr.on('data', (d) => { for (const l of String(d).split('\n')) if (l.includes('GET /offline-pwa')) log.push(l); });
await wait(1000);

// What Jam 3.1 left: a whole-session copy and its record.
const userData = path.join(home, 'config', 'Jam');
fs.mkdirSync(path.join(userData, 'Partitions', 'jam-octave-saved', 'IndexedDB'), { recursive: true });
fs.writeFileSync(path.join(userData, 'Partitions', 'jam-octave-saved', 'IndexedDB', 'big.blob'), Buffer.alloc(3 << 20));
fs.writeFileSync(path.join(userData, 'saved-states.json'), JSON.stringify({ sources: { octave: { savedAt: 1, size: 3 << 20, enabled: true } }, pending: null }));

const electron = process.env.JAM_ELECTRON || path.join(root, 'node_modules', '.bin', 'electron');
const env = { ...process.env, HOME: home, XDG_CONFIG_HOME: path.join(home, 'config'), JAM_TEST: '1',
  JAM_DEV_SOURCES: JSON.stringify({ octave: 'http://localhost:8765/offline-pwa/' }) };
// JAM_PACKAGED=/opt/Jam/jam: the installed app (Arch package) instead of this folder.
const app = process.env.JAM_PACKAGED
  ? spawn(process.env.JAM_PACKAGED, ['--no-sandbox', '--remote-debugging-port=9333'], { env, stdio: ['ignore', 'pipe', 'pipe'] })
  : spawn(electron, [root, '--no-sandbox', '--remote-debugging-port=9333'], { env, stdio: ['ignore', 'pipe', 'pipe'] });
let out = '';
app.stdout.on('data', (d) => { out += d; });
app.stderr.on('data', (d) => { out += d; });

async function connect() {
  for (let i = 0; i < 60; i++) {
    try { return await puppeteer.connect({ browserURL: 'http://127.0.0.1:9333', defaultViewport: null }); } catch { await wait(500); }
  }
  throw new Error('no Electron');
}
async function page(b, match) {
  for (let i = 0; i < 40; i++) {
    for (const t of b.targets()) {
      if (t.type() === 'page' && match(t.url())) return t.page();
    }
    await wait(500);
  }
  throw new Error('no page ' + match);
}
const site = (u) => u.startsWith('http://localhost:8765/');
const bar = (u) => u.endsWith('titlebar.html');

try {
  let b = await connect();
  let p = await page(b, site);
  await wait(6000); // the worker installs, takes control
  await p.reload(); await wait(2500);
  say('live: ' + (await p.evaluate(() => document.getElementById('s')?.textContent || document.body.innerText.slice(0, 120))));
  say('3.1 copy deleted at start: ' + !fs.existsSync(path.join(userData, 'Partitions', 'jam-octave-saved')));
  say('live: ' + (await p.evaluate(() => document.getElementById('c')?.textContent)));
  const t = await page(b, bar);
  const before = log.length;
  say('requests so far: ' + before);
  say('live: bridge ' + (await p.evaluate(() => typeof window.JamNative)) + ', installed-app ' + (await p.evaluate(() => !!window.__jamInstalledApp)) + ', standalone ' + (await p.evaluate(() => matchMedia('(display-mode: standalone)').matches)));
  await t.evaluate(() => window.titlebar.action('test-save'));
  await wait(9000); // the save, the covers, the saved state opening (no restart)
  say('titlebar after save: ' + (await t.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 200))));
  say('covers kept: ' + (fs.existsSync(path.join(userData, 'covers')) ? fs.readdirSync(path.join(userData, 'covers')).length : 0));
  p = await page(b, site);
  await wait(3000);
  const mark = log.length;
  await p.reload(); await wait(3000);
  say('saved: ' + (await p.evaluate(() => document.getElementById('c')?.textContent)));
  say('saved: ' + (await p.evaluate(() => document.getElementById('s')?.textContent || document.body.innerText.slice(0, 120))));
  say('saved: navigator.onLine = ' + (await p.evaluate(() => navigator.onLine)));
  say('saved: requests to the site while open: ' + (log.length - mark));
  const t2 = await page(b, bar);
  await t2.screenshot({ path: path.join(home, 'titlebar-saved.png') });
  say('saved: source pill: ' + (await t2.evaluate(() => document.getElementById('srcName').textContent)) + ', button on: ' + (await t2.evaluate(() => document.getElementById('saved').classList.contains('on'))));
  // The site down entirely: the save still opens.
  server.kill(); await wait(500);
  await p.reload(); await wait(3000);
  say('saved, site down: ' + (await p.evaluate(() => document.getElementById('s')?.textContent || document.body.innerText.slice(0, 120))) + ' · ' + (await p.evaluate(() => document.getElementById('c')?.textContent)));
  // Back to the live site (still down): Octave's worker answers, or Jam's page offers the save.
  await t2.evaluate(() => window.titlebar.action('test-saved', false));
  await wait(5000);
  const p3 = await page(b, (u) => site(u) || u.includes('offline.html'));
  await wait(3000);
  say('live, site down: ' + p3.url().replace(/\?.*/, '') + ' — ' + (await p3.evaluate(() => (document.getElementById('s')?.textContent || document.body.innerText).slice(0, 160))));
  b.disconnect();
} catch (e) {
  say('FAILED: ' + e.message);
} finally {
  app.kill(); server.kill();
  await wait(500);
  const sv = path.join(home, 'config', 'Jam', 'saved-states.json');
  if (fs.existsSync(sv)) say('saved-states.json: ' + fs.readFileSync(sv, 'utf8').replace(/\s+/g, ' ').slice(0, 600));
  fs.writeFileSync(path.join(home, 'electron.log'), out);
  console.log('electron log: ' + path.join(home, 'electron.log'));
  console.log('home: ' + home);
  process.exit(0);
}
