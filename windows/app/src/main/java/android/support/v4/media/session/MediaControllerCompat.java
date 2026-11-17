package android.support.v4.media.session;

public final class MediaControllerCompat {
    private final MediaSessionCompat s;
    public MediaControllerCompat(MediaSessionCompat s) { this.s = s; }
    public PlaybackStateCompat getPlaybackState() { return s.getPlaybackState(); }
    public android.support.v4.media.MediaMetadataCompat getMetadata() { return s.getMetadata(); }
    public TransportControls getTransportControls() { return new TransportControls(s.getCallback()); }
    public boolean dispatchMediaButtonEvent(android.view.KeyEvent e) { return false; }
    public static final class TransportControls { private final MediaSessionCompat.Callback cb;
        TransportControls(MediaSessionCompat.Callback cb) { this.cb = cb; }
        public void play() { if (cb != null) cb.onPlay(); } public void pause() { if (cb != null) cb.onPause(); } public void stop() { if (cb != null) cb.onStop(); }
        public void skipToNext() { if (cb != null) cb.onSkipToNext(); } public void skipToPrevious() { if (cb != null) cb.onSkipToPrevious(); } public void seekTo(long p) { if (cb != null) cb.onSeekTo(p); } }
}
