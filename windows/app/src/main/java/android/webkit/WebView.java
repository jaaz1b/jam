package android.webkit;

import android.content.Context;
import android.graphics.Bitmap;
import app.jam.desktop.web.Page;
import java.util.Map;

/**
 * Android's WebView on the PC: a page of the hidden browser (app.jam.desktop.web.Page). Drawn in
 * Jam's window, from the page's screencast, when it is attached and visible (app.jam.desktop.ViewTree).
 */
public class WebView extends android.view.ViewGroup {
    public static final int RENDERER_PRIORITY_WAIVED = 0, RENDERER_PRIORITY_BOUND = 1, RENDERER_PRIORITY_IMPORTANT = 2;
    private final Page page = new Page(this);
    private final WebSettings settings = new WebSettings(this);
    private WebViewClient client = new WebViewClient();
    private WebChromeClient chrome;
    private boolean destroyed;

    public WebView(Context context) { super(context); }
    public final Page desktopPage() { return page; }

    public WebSettings getSettings() { return settings; }
    public void setWebViewClient(WebViewClient c) { client = c; }
    public WebViewClient getWebViewClient() { return client; }
    public void setWebChromeClient(WebChromeClient c) { chrome = c; }
    public WebChromeClient getWebChromeClient() { return chrome; }

    public void loadUrl(String url) { if (!destroyed) page.load(url, null); }
    public void loadUrl(String url, Map<String, String> headers) { if (!destroyed) page.load(url, headers); }
    public void postUrl(String url, byte[] body) { loadUrl(url); }
    public void loadData(String data, String mime, String enc) { page.loadHtml(data, null); }
    public void loadDataWithBaseURL(String base, String data, String mime, String enc, String history) { page.loadHtml(data, base); }
    public void evaluateJavascript(String script, ValueCallback<String> cb) { if (!destroyed) page.evaluate(script, cb); }
    public void addJavascriptInterface(Object obj, String name) { page.addJsInterface(obj, name); }
    public void removeJavascriptInterface(String name) { page.removeJsInterface(name); }
    public void stopLoading() { page.stop(); }
    public void reload() { page.reload(); }
    public boolean canGoBack() { return page.canGoBack(); }
    public void goBack() { page.goBack(); }
    public boolean canGoForward() { return false; }
    public void goForward() {}
    public String getUrl() { return page.getUrl(); }
    public String getOriginalUrl() { return page.getUrl(); }
    public String getTitle() { return page.getTitle(); }
    public int getProgress() { return page.getProgress(); }
    public void clearCache(boolean includeDiskFiles) { page.clearCache(); }
    public void clearHistory() {}
    public void clearFormData() {}
    public void clearSslPreferences() {}
    public void onPause() {}
    public void onResume() {}
    public void pauseTimers() {}
    public void resumeTimers() {}
    public void destroy() { destroyed = true; page.destroy(); }
    public boolean isDestroyed() { return destroyed; }
    public void setRendererPriorityPolicy(int p, boolean waivedWhenNotVisible) {}
    public int getRendererRequestedPriority() { return RENDERER_PRIORITY_IMPORTANT; }
    public void setInitialScale(int s) {}
    public void setNetworkAvailable(boolean b) {}
    public void setVerticalScrollBarEnabled(boolean b) {}
    public void setHorizontalScrollBarEnabled(boolean b) {}
    public void setOverScrollMode(int m) {}
    public void setScrollBarStyle(int s) {}
    public void setDownloadListener(Object l) {}
    public void setFindListener(Object l) {}
    public Bitmap getFavicon() { return null; }
    public void flingScroll(int vx, int vy) {}
    public void zoomBy(float f) {}
    @Override protected void onWindowVisibilityChanged(int visibility) {}

    public static void setWebContentsDebuggingEnabled(boolean on) {}
    public static String getDefaultUserAgent(Context c) { return WebSettings.getDefaultUserAgent(c); }
    public static android.content.pm.PackageInfo getCurrentWebViewPackage() {
        android.content.pm.PackageInfo p = new android.content.pm.PackageInfo();
        p.packageName = "com.microsoft.edge";
        p.versionName = app.jam.desktop.web.Browsers.INSTANCE.version();
        return p;
    }
    public static void setDataDirectorySuffix(String s) {}
    public static void disableWebView() {}
}
