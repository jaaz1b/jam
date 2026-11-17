package androidx.webkit;

/** A web profile: on the PC, a browser profile of its own (its own cookies and storage). */
public class Profile {
    public static final String DEFAULT = "Default", DEFAULT_PROFILE_NAME = "Default";
    private final String name;
    Profile(String name) { this.name = name; }
    public String getName() { return name; }
    public android.webkit.CookieManager getCookieManager() { return app.jam.desktop.web.Cookies.Companion.of(name); }
    public android.webkit.WebStorage getWebStorage() { return new android.webkit.WebStorage(name); }
    public Object getGeolocationPermissions() { return null; }
    public Object getServiceWorkerController() { return null; }
}
