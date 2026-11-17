package androidx.media3.exoplayer;

import androidx.media3.common.*;
import androidx.media3.exoplayer.source.MediaSource;

public interface ExoPlayer extends Player {
    int getAudioSessionId();
    void setAudioSessionId(int id);
    void setSkipSilenceEnabled(boolean on);
    boolean getSkipSilenceEnabled();
    void setAudioAttributes(AudioAttributes a, boolean handleAudioFocus);
    void setHandleAudioBecomingNoisy(boolean on);
    void setMediaSource(MediaSource s);
    void setMediaSource(MediaSource s, long startPositionMs);
    void setMediaSource(MediaSource s, boolean resetPosition);
    void setMediaSources(java.util.List<MediaSource> s);
    void addMediaSource(MediaSource s);
    void addMediaSource(int index, MediaSource s);
    void setSeekParameters(SeekParameters p);
    void setPauseAtEndOfMediaItems(boolean on);
    boolean getPauseAtEndOfMediaItems();
    void setWakeMode(int mode);
    Format getAudioFormat();
    Format getVideoFormat();
    void setVideoScalingMode(int mode);
    boolean isReleased();
    void setForegroundMode(boolean on);
    final class Builder {
        final android.content.Context context; RenderersFactory renderers; androidx.media3.exoplayer.source.MediaSource.Factory sources; AudioAttributes attributes; boolean focus, noisy; LoadControl loadControl; long seekBack = 5000, seekForward = 15000; android.os.Looper looper; boolean pauseAtEnd; int wakeMode;
        public Builder(android.content.Context c) { context = c; }
        public Builder(android.content.Context c, RenderersFactory r) { context = c; renderers = r; }
        public Builder(android.content.Context c, androidx.media3.exoplayer.source.MediaSource.Factory s) { context = c; sources = s; }
        public Builder setRenderersFactory(RenderersFactory r) { renderers = r; return this; }
        public Builder setMediaSourceFactory(androidx.media3.exoplayer.source.MediaSource.Factory s) { sources = s; return this; }
        public Builder setAudioAttributes(AudioAttributes a, boolean handleFocus) { attributes = a; focus = handleFocus; return this; }
        public Builder setHandleAudioBecomingNoisy(boolean n) { noisy = n; return this; }
        public Builder setLoadControl(LoadControl l) { loadControl = l; return this; }
        public Builder setSeekBackIncrementMs(long ms) { seekBack = ms; return this; }
        public Builder setSeekForwardIncrementMs(long ms) { seekForward = ms; return this; }
        public Builder setLooper(android.os.Looper l) { looper = l; return this; }
        public Builder setWakeMode(int m) { wakeMode = m; return this; }
        public Builder setPauseAtEndOfMediaItems(boolean p) { pauseAtEnd = p; return this; }
        public Builder setUseLazyPreparation(boolean b) { return this; }
        public Builder setReleaseTimeoutMs(long ms) { return this; }
        public Builder setDeviceVolumeControlEnabled(boolean b) { return this; }
        public Builder setSuppressPlaybackOnUnsuitableOutput(boolean b) { return this; }
        public Builder setTrackSelector(Object t) { return this; }
        public Builder setBandwidthMeter(Object b) { return this; }
        public Builder setSkipSilenceEnabled(boolean b) { return this; }
        public ExoPlayer build() { return new app.jam.desktop.media.DesktopPlayer(context, renderers != null ? renderers : new DefaultRenderersFactory(context), sources, attributes, pauseAtEnd, seekBack, seekForward); }
    }
}
