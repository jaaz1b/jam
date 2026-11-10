// The core of what Octave's worker does: network first, the saved copy when the network fails,
// with navigation preload switched on (Octave's is) and the page answered from the cache offline.
const CACHE = 'jam-offline-test-v2';
self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(['./', './index.html'])).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => e.waitUntil((async () => {
  if (self.registration.navigationPreload) { try { await self.registration.navigationPreload.enable(); } catch {} }
  await self.clients.claim();
})()));
self.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET') return;
  const cached = () => caches.match(e.request, { ignoreSearch: true }).then((m) => m || caches.match('./'));
  if (e.request.mode === 'navigate') {
    e.respondWith((async () => {
      try { const p = await e.preloadResponse; if (p && p.ok) return p; } catch {}
      try { const r = await fetch(e.request); if (r.ok) return r; } catch {}
      return (await cached()) || new Response("<h1>You're offline</h1>", { status: 503, headers: { 'Content-Type': 'text/html' } });
    })());
    return;
  }
  // Covers aren't kept by this worker (Octave's keeps only its last few hundred images, and none
  // from sites that don't allow it): Jam's own cover store has to answer for them offline.
  if (new URL(e.request.url).pathname.includes('/covers/')) { e.respondWith(fetch(e.request)); return; }
  e.respondWith(
    fetch(e.request).then((r) => {
      if (r.ok) { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
      return r;
    }).catch(cached)
  );
});
