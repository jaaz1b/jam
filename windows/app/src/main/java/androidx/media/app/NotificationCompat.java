package androidx.media.app;

public class NotificationCompat {
    public static class MediaStyle extends androidx.core.app.NotificationCompat.Style {
        public MediaStyle setMediaSession(android.support.v4.media.session.MediaSessionCompat.Token t) { return this; }
        public MediaStyle setShowActionsInCompactView(int... i) { return this; }
        public MediaStyle setShowCancelButton(boolean b) { return this; }
        public MediaStyle setCancelButtonIntent(android.app.PendingIntent p) { return this; }
    }
}
