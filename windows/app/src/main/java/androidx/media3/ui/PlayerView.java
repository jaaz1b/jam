package androidx.media3.ui;

/** media3's PlayerView: on the PC, a view whose video the engine draws (ViewTree). */
public class PlayerView extends android.view.View {
    public static final int ARTWORK_DISPLAY_MODE_OFF = 0, ARTWORK_DISPLAY_MODE_FIT = 1, ARTWORK_DISPLAY_MODE_FILL = 2;
    public static final int SHOW_BUFFERING_NEVER = 0;
    private androidx.media3.common.Player player;
    public PlayerView(android.content.Context c) { super(c); }
    public void setPlayer(androidx.media3.common.Player p) { player = p; }
    public androidx.media3.common.Player getPlayer() { return player; }
    public void setUseController(boolean b) {}
    public void setResizeMode(int m) {}
    public void setShutterBackgroundColor(int c) {}
    public void setKeepContentOnPlayerReset(boolean b) {}
    public void setShowBuffering(int m) {}
    public void setDefaultArtwork(android.graphics.drawable.Drawable d) {}
    public void setArtworkDisplayMode(int m) {}
    public void setUseArtwork(boolean b) {}
}
