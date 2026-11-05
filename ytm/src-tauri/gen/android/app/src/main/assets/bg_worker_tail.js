// Jam for Android: after bg_bundle.js. The page calls runBotguard / newMinter / mint by message.
(function () {
  const post = postMessage.bind(globalThis);
  globalThis.onmessage = async (e) => {
    const m = e.data;
    try { const v = await globalThis[m.fn](...m.args); post({ id: m.id, ok: 1, v: v }); }
    catch (x) { post({ id: m.id, ok: 0, e: String((x && (x.stack || x.message)) || x) }); }
  };
  post({ id: 0, ok: 1, v: 'ready' });
})();
