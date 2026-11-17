package android.media;
/** The system's output switcher: on the PC, Windows' own sound output choice (its Settings page). */
public final class MediaRouter2 {
    public static MediaRouter2 getInstance(android.content.Context c) { return new MediaRouter2(); }
    public boolean showSystemOutputSwitcher() { app.jam.desktop.shim.Shell.INSTANCE.browse("ms-settings:sound"); return true; }
}
