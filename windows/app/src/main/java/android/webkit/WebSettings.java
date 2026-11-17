package android.webkit;

public class WebSettings {
    public static final int LOAD_DEFAULT = -1, LOAD_NORMAL = 0, LOAD_CACHE_ELSE_NETWORK = 1, LOAD_NO_CACHE = 2, LOAD_CACHE_ONLY = 3;
    public static final int MIXED_CONTENT_ALWAYS_ALLOW = 0, MIXED_CONTENT_NEVER_ALLOW = 1, MIXED_CONTENT_COMPATIBILITY_MODE = 2;
    private final WebView view;
    private boolean js, dom, gesture = true, fileAccess, contentAccess, overview, wide, geo, multiWindows, zoom, builtInZoom, displayZoom = true, databases, blockImages, blockNetwork;
    private String ua;
    private int cacheMode = LOAD_DEFAULT, mixed = MIXED_CONTENT_NEVER_ALLOW, textZoom = 100;
    WebSettings(WebView v) { view = v; }
    public void setJavaScriptEnabled(boolean b) { js = b; } public boolean getJavaScriptEnabled() { return js; }
    public void setDomStorageEnabled(boolean b) { dom = b; } public boolean getDomStorageEnabled() { return dom; }
    public void setMediaPlaybackRequiresUserGesture(boolean b) { gesture = b; } public boolean getMediaPlaybackRequiresUserGesture() { return gesture; }
    public void setAllowFileAccess(boolean b) { fileAccess = b; } public boolean getAllowFileAccess() { return fileAccess; }
    public void setAllowContentAccess(boolean b) { contentAccess = b; } public boolean getAllowContentAccess() { return contentAccess; }
    public void setLoadWithOverviewMode(boolean b) { overview = b; } public boolean getLoadWithOverviewMode() { return overview; }
    public void setUseWideViewPort(boolean b) { wide = b; } public boolean getUseWideViewPort() { return wide; }
    public void setGeolocationEnabled(boolean b) { geo = b; }
    public void setSupportMultipleWindows(boolean b) { multiWindows = b; } public boolean supportMultipleWindows() { return multiWindows; }
    public void setJavaScriptCanOpenWindowsAutomatically(boolean b) {}
    public void setSupportZoom(boolean b) { zoom = b; }
    public void setBuiltInZoomControls(boolean b) { builtInZoom = b; }
    public void setDisplayZoomControls(boolean b) { displayZoom = b; }
    public void setDatabaseEnabled(boolean b) { databases = b; }
    public void setBlockNetworkImage(boolean b) { blockImages = b; }
    public void setBlockNetworkLoads(boolean b) { blockNetwork = b; }
    public void setLoadsImagesAutomatically(boolean b) {}
    public void setSafeBrowsingEnabled(boolean b) {}
    public void setOffscreenPreRaster(boolean b) {}
    public void setAllowFileAccessFromFileURLs(boolean b) {}
    public void setAllowUniversalAccessFromFileURLs(boolean b) {}
    public void setSaveFormData(boolean b) {}
    public void setTextZoom(int z) { textZoom = z; } public int getTextZoom() { return textZoom; }
    public void setMixedContentMode(int m) { mixed = m; } public int getMixedContentMode() { return mixed; }
    public void setCacheMode(int m) { cacheMode = m; view.desktopPage().setNoCache(m == LOAD_NO_CACHE); } public int getCacheMode() { return cacheMode; }
    public void setUserAgentString(String s) { ua = s; view.desktopPage().setUserAgent(s); }
    public String getUserAgentString() { return ua != null ? ua : getDefaultUserAgent(null); }
    public void setForceDark(int f) {}
    public void setAlgorithmicDarkeningAllowed(boolean b) {}
    public static String getDefaultUserAgent(android.content.Context c) { return app.jam.desktop.web.Browsers.INSTANCE.userAgent(); }
}
