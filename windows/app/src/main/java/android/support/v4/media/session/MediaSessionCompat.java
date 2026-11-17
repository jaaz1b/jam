package android.support.v4.media.session;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

/**
 * The media session: what it says (song, state) goes to Windows' media controls (app.jam.desktop.SystemMedia),
 * and the media keys and Windows' controls come back as its callback's calls.
 */
public class MediaSessionCompat {
    public static final int FLAG_HANDLES_MEDIA_BUTTONS = 1, FLAG_HANDLES_TRANSPORT_CONTROLS = 2, FLAG_HANDLES_QUEUE_COMMANDS = 4;
    private Callback callback; private boolean active; private MediaMetadataCompat metadata; private PlaybackStateCompat state;
    public MediaSessionCompat(android.content.Context c, String tag) { app.jam.desktop.SystemMedia.INSTANCE.attach(this); }
    public MediaSessionCompat(android.content.Context c, String tag, android.content.ComponentName receiver, android.app.PendingIntent intent) { this(c, tag); }
    public void setCallback(Callback cb) { callback = cb; }
    public void setCallback(Callback cb, android.os.Handler h) { callback = cb; }
    public Callback getCallback() { return callback; }
    public void setActive(boolean a) { active = a; app.jam.desktop.SystemMedia.INSTANCE.update(this); }
    public boolean isActive() { return active; }
    public void setMetadata(MediaMetadataCompat m) { metadata = m; app.jam.desktop.SystemMedia.INSTANCE.update(this); }
    public MediaMetadataCompat getMetadata() { return metadata; }
    public void setPlaybackState(PlaybackStateCompat s) { state = s; app.jam.desktop.SystemMedia.INSTANCE.update(this); }
    public PlaybackStateCompat getPlaybackState() { return state; }
    public void setFlags(int f) {}
    public void setQueue(java.util.List<QueueItem> q) {}
    public void setQueueTitle(CharSequence t) {}
    public void setSessionActivity(android.app.PendingIntent p) {}
    public void setMediaButtonReceiver(android.app.PendingIntent p) {}
    public void setPlaybackToLocal(int stream) {}
    public void setPlaybackToRemote(androidx.media.VolumeProviderCompat v) {}
    public void setRepeatMode(int m) {}
    public void setShuffleMode(int m) {}
    public void setExtras(android.os.Bundle e) {}
    public void setRatingType(int t) {}
    public void sendSessionEvent(String e, android.os.Bundle b) {}
    public Token getSessionToken() { return new Token(); }
    public MediaControllerCompat getController() { return new MediaControllerCompat(this); }
    public void release() { active = false; app.jam.desktop.SystemMedia.INSTANCE.detach(this); }
    public static final class Token implements android.os.Parcelable {}
    public static final class QueueItem implements android.os.Parcelable {
        public static final int UNKNOWN_ID = -1;
        private final MediaDescriptionCompat d; private final long id;
        public QueueItem(MediaDescriptionCompat d, long id) { this.d = d; this.id = id; }
        public MediaDescriptionCompat getDescription() { return d; } public long getQueueId() { return id; }
    }
    public abstract static class Callback {
        public void onPlay() {} public void onPause() {} public void onStop() {} public void onPrepare() {}
        public void onSkipToNext() {} public void onSkipToPrevious() {} public void onFastForward() {} public void onRewind() {}
        public void onSeekTo(long pos) {} public void onSkipToQueueItem(long id) {}
        public boolean onMediaButtonEvent(android.content.Intent mediaButtonEvent) { return false; }
        public void onPlayFromMediaId(String mediaId, android.os.Bundle extras) {} public void onPlayFromSearch(String query, android.os.Bundle extras) {} public void onPlayFromUri(android.net.Uri uri, android.os.Bundle extras) {}
        public void onPrepareFromMediaId(String mediaId, android.os.Bundle extras) {} public void onPrepareFromSearch(String q, android.os.Bundle e) {}
        public void onSetShuffleMode(int mode) {} public void onSetRepeatMode(int mode) {} public void onSetPlaybackSpeed(float s) {} public void onSetRating(Object r) {}
        public void onCustomAction(String action, android.os.Bundle extras) {} public void onCommand(String c, android.os.Bundle e, Object cb) {}
    }
}
