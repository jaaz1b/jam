// YouTube Music engine test: Jam opens on YouTube Music, starts the engine (ytm/, jam-ytm), and the
// UI it shows talks to it. Prints what the UI shows (its home page, when YouTube can be reached).
// Needs xvfb (and ideally a D-Bus session) on Linux:
//   xvfb-run -a node desktop/test/ytm-engine.mjs                      this folder, ../ytm-dist
//   JAM_PACKAGED=/opt/Jam/jam dbus-run-session -- xvfb-run -a node …  the installed app
// Exit code 1 if the engine didn't answer.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const puppeteer = require(process.env.PUPPETEER || 'puppeteer-core');

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const home = fs.mkdtempSync(path.join(os.tmpdir(), 'jam-ytm-'));
const userData = path.join(home, 'config', 'Jam');
fs.mkdirSync(userData, { recursive: true });
fs.writeFileSync(path.join(userData, 'settings.json'), JSON.stringify({ defaultSource: 'ytm', firstRunDone: true }));
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const env = { ...process.env, HOME: home, XDG_CONFIG_HOME: path.join(home, 'config'), JAM_YTM_DEBUG: '1' };
const args = ['--no-sandbox', '--remote-debugging-port=9334'];
const app = process.env.JAM_PACKAGED
  ? spawn(process.env.JAM_PACKAGED, args, { env, stdio: ['ignore', 'pipe', 'pipe'] })
  : spawn(process.env.JAM_ELECTRON || path.join(root, 'node_modules', '.bin', 'electron'), [root, ...args], { env, stdio: ['ignore', 'pipe', 'pipe'] });
let out = '';
app.stdout.on('data', (d) => { out += d; });
app.stderr.on('data', (d) => { out += d; });

let ok = false;
try {
  let b = null;
  for (let i = 0; i < 60 && !b; i++) {
    try { b = await puppeteer.connect({ browserURL: 'http://127.0.0.1:9334', defaultViewport: null }); } catch { await wait(500); }
  }
  if (!b) throw new Error('no Electron');
  let target = null;
  for (let i = 0; i < 60 && !target; i++) {
    target = b.targets().find((t) => t.type() === 'page' && t.url().startsWith('jamytm://'));
    if (!target) await wait(500);
  }
  if (!target) throw new Error('no YouTube Music page');
  const p = await target.page();
  let settings = null;
  for (let i = 0; i < 30 && !settings; i++) {
    settings = await p.evaluate(async () => {
      try { return JSON.stringify(await window.__TAURI_INTERNALS__.invoke('get_settings')).slice(0, 160); } catch { return null; }
    });
    if (!settings) await wait(1000);
  }
  console.log('engine answers: ' + (settings || 'NO'));
  ok = !!settings;
  await wait(20000); // the home page loads (when YouTube can be reached from here)
  console.log('UI shows: ' + (await p.evaluate(() => document.body.innerText)).replace(/\s+/g, ' ').slice(0, 500));
  const shot = path.join(process.env.JAM_TEST_OUT || home, 'ytm.png');
  await p.screenshot({ path: shot });
  console.log('screenshot: ' + shot);
  b.disconnect();
} catch (e) {
  console.log('FAILED: ' + e.message);
} finally {
  app.kill();
  await wait(1000);
  const log = path.join(process.env.JAM_TEST_OUT || home, 'ytm-electron.log');
  fs.writeFileSync(log, out);
  console.log('log: ' + log);
  for (const l of out.split('\n')) if (/panicked|ERROR app_lib|error while loading shared/i.test(l)) console.log('  ' + l.trim().slice(0, 300));
  process.exit(ok ? 0 : 1);
}
