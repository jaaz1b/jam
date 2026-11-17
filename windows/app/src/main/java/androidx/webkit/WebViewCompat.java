package androidx.webkit;

import android.webkit.WebView;
import java.util.Set;

public class WebViewCompat {
    public interface WebMessageListener {
        void onPostMessage(WebView view, WebMessageCompat message, android.net.Uri sourceOrigin, boolean isMainFrame, JavaScriptReplyProxy replyProxy);
    }
    public static void addWebMessageListener(WebView view, String name, Set<String> origins, WebMessageListener listener) {
        view.desktopPage().addWebMessageListener(name, origins, listener);
    }
    public static void removeWebMessageListener(WebView view, String name) { view.desktopPage().removeWebMessageListener(name); }
    public static ScriptHandler addDocumentStartJavaScript(WebView view, String script, Set<String> origins) {
        int h = view.desktopPage().addDocumentStart(script, origins);
        return () -> view.desktopPage().removeDocumentStart(h);
    }
    public static void setProfile(WebView view, String profile) { view.desktopPage().setProfile(profile); }
    public static Profile getProfile(WebView view) { return ProfileStore.getInstance().getOrCreateProfile(view.desktopPage().getProfile()); }
    public static android.content.pm.PackageInfo getCurrentWebViewPackage(android.content.Context c) { return WebView.getCurrentWebViewPackage(); }
    public static void startSafeBrowsing(android.content.Context c, android.webkit.ValueCallback<Boolean> cb) {}
    public static boolean isMultiProcessEnabled() { return true; }
    public static void postVisualStateCallback(WebView v, long id, Object cb) {}
}
