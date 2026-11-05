// For services that only offer their web player to computers (Spotify, Apple Music):
// present as desktop Chrome, matching the user agent Jam sends.
(function () {
  if (window.__jamDesktopSite) return;
  window.__jamDesktopSite = true;
  try {
    Object.defineProperty(Navigator.prototype, 'platform', { configurable: true, get: function () { return 'Linux x86_64'; } });
    var real = navigator.userAgentData;
    if (real) {
      var brands = real.brands;
      var fake = {
        brands: brands, mobile: false, platform: 'Linux',
        toJSON: function () { return { brands: brands, mobile: false, platform: 'Linux' }; },
        getHighEntropyValues: function (hints) {
          return real.getHighEntropyValues(hints).then(function (v) { v.mobile = false; v.platform = 'Linux'; v.model = ''; return v; });
        },
      };
      Object.defineProperty(Navigator.prototype, 'userAgentData', { configurable: true, get: function () { return fake; } });
    }
  } catch (e) {}
})();
