package android.webkit;

public class WebStorage {
    private final String profile;
    public WebStorage(String profile) { this.profile = profile; }
    public void deleteAllData() { app.jam.desktop.web.Cookies.Companion.clearData(profile); }
    public void deleteOrigin(String origin) {}
    public static WebStorage getInstance() { return new WebStorage(app.jam.desktop.web.Edge.DEFAULT); }
}
