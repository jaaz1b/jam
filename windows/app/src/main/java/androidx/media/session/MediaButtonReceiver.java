package androidx.media.session;

public class MediaButtonReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context c, android.content.Intent i) {}
    public static android.app.PendingIntent buildMediaButtonPendingIntent(android.content.Context c, long action) { return android.app.PendingIntent.getBroadcast(c, (int) action, new android.content.Intent("android.intent.action.MEDIA_BUTTON").putExtra("jam.action", action), 0); }
    public static android.app.PendingIntent buildMediaButtonPendingIntent(android.content.Context c, android.content.ComponentName cn, long action) { return buildMediaButtonPendingIntent(c, action); }
    public static android.view.KeyEvent handleIntent(android.support.v4.media.session.MediaSessionCompat s, android.content.Intent i) { return null; }
}
