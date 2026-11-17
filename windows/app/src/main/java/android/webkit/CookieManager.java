package android.webkit;

/** The cookies of a web profile (its browser's): read and written over the DevTools protocol. */
public abstract class CookieManager {
    public abstract void setAcceptCookie(boolean accept);
    public abstract boolean acceptCookie();
    public abstract void setAcceptThirdPartyCookies(WebView view, boolean accept);
    public abstract boolean acceptThirdPartyCookies(WebView view);
    public abstract void setCookie(String url, String value);
    public abstract void setCookie(String url, String value, ValueCallback<Boolean> callback);
    public abstract String getCookie(String url);
    public abstract void removeAllCookies(ValueCallback<Boolean> callback);
    public abstract void removeSessionCookies(ValueCallback<Boolean> callback);
    public abstract boolean hasCookies();
    public abstract void flush();
    public static CookieManager getInstance() { return app.jam.desktop.web.Cookies.Companion.of(app.jam.desktop.web.Edge.DEFAULT); }
}
