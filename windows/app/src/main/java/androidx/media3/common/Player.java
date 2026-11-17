package androidx.media3.common;

public interface Player {
    int STATE_IDLE = 1, STATE_BUFFERING = 2, STATE_READY = 3, STATE_ENDED = 4;
    int REPEAT_MODE_OFF = 0, REPEAT_MODE_ONE = 1, REPEAT_MODE_ALL = 2;
    int MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0, MEDIA_ITEM_TRANSITION_REASON_AUTO = 1, MEDIA_ITEM_TRANSITION_REASON_SEEK = 2, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3;
    int PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1, PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS = 2, PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY = 3, PLAY_WHEN_READY_CHANGE_REASON_REMOTE = 4, PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM = 5, PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG = 6;
    int PLAYBACK_SUPPRESSION_REASON_NONE = 0, PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS = 1, PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT = 3;
    int DISCONTINUITY_REASON_AUTO_TRANSITION = 0, DISCONTINUITY_REASON_SEEK = 1, DISCONTINUITY_REASON_SEEK_ADJUSTMENT = 2, DISCONTINUITY_REASON_SKIP = 3, DISCONTINUITY_REASON_REMOVE = 4, DISCONTINUITY_REASON_INTERNAL = 5;
    int TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED = 0, TIMELINE_CHANGE_REASON_SOURCE_UPDATE = 1;
    int EVENT_TIMELINE_CHANGED = 0, EVENT_MEDIA_ITEM_TRANSITION = 1, EVENT_TRACKS_CHANGED = 2, EVENT_IS_LOADING_CHANGED = 3, EVENT_PLAYBACK_STATE_CHANGED = 4, EVENT_PLAY_WHEN_READY_CHANGED = 5, EVENT_IS_PLAYING_CHANGED = 7, EVENT_REPEAT_MODE_CHANGED = 8, EVENT_POSITION_DISCONTINUITY = 11, EVENT_PLAYER_ERROR = 10, EVENT_MEDIA_METADATA_CHANGED = 14;
    int COMMAND_PLAY_PAUSE = 1, COMMAND_PREPARE = 2, COMMAND_STOP = 3, COMMAND_SEEK_TO_DEFAULT_POSITION = 4, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM = 5, COMMAND_SEEK_TO_PREVIOUS = 7, COMMAND_SEEK_TO_NEXT = 9, COMMAND_SET_REPEAT_MODE = 15, COMMAND_GET_CURRENT_MEDIA_ITEM = 16;

    interface Listener {
        default void onEvents(Player player, Events events) {}
        default void onTimelineChanged(Timeline timeline, int reason) {}
        default void onMediaItemTransition(MediaItem mediaItem, int reason) {}
        default void onTracksChanged(Tracks tracks) {}
        default void onMediaMetadataChanged(MediaMetadata m) {}
        default void onIsLoadingChanged(boolean isLoading) {}
        default void onPlaybackStateChanged(int playbackState) {}
        default void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {}
        default void onPlaybackSuppressionReasonChanged(int reason) {}
        default void onIsPlayingChanged(boolean isPlaying) {}
        default void onRepeatModeChanged(int repeatMode) {}
        default void onShuffleModeEnabledChanged(boolean on) {}
        default void onPlayerError(PlaybackException error) {}
        default void onPlayerErrorChanged(PlaybackException error) {}
        default void onPositionDiscontinuity(PositionInfo oldPosition, PositionInfo newPosition, int reason) {}
        default void onPlaybackParametersChanged(PlaybackParameters p) {}
        default void onVolumeChanged(float volume) {}
        default void onAudioSessionIdChanged(int id) {}
        default void onSkipSilenceEnabledChanged(boolean on) {}
        default void onVideoSizeChanged(VideoSize size) {}
        default void onRenderedFirstFrame() {}
        default void onAudioAttributesChanged(AudioAttributes a) {}
    }
    final class Events {
        private final java.util.Set<Integer> set;
        public Events(java.util.Set<Integer> s) { set = s; }
        public boolean contains(int e) { return set.contains(e); }
        public boolean containsAny(int... es) { for (int e : es) if (set.contains(e)) return true; return false; }
        public int size() { return set.size(); }
    }
    final class PositionInfo {
        public final Object windowUid, periodUid; public final MediaItem mediaItem; public final int mediaItemIndex, periodIndex, adGroupIndex = -1, adIndexInAdGroup = -1; public final long positionMs, contentPositionMs;
        public PositionInfo(Object w, int idx, MediaItem item, Object p, int pi, long pos, long cpos, int ag, int ai) { windowUid = w; mediaItemIndex = idx; mediaItem = item; periodUid = p; periodIndex = pi; positionMs = pos; contentPositionMs = cpos; }
    }
    final class Commands { public static final Commands EMPTY = new Commands(); public boolean contains(int c) { return true; } }

    android.os.Looper getApplicationLooper();
    void addListener(Listener l);
    void removeListener(Listener l);
    void setMediaItems(java.util.List<MediaItem> items);
    void setMediaItems(java.util.List<MediaItem> items, boolean resetPosition);
    void setMediaItems(java.util.List<MediaItem> items, int startIndex, long startPositionMs);
    void setMediaItem(MediaItem item);
    void setMediaItem(MediaItem item, long startPositionMs);
    void setMediaItem(MediaItem item, boolean resetPosition);
    void addMediaItem(MediaItem item);
    void addMediaItem(int index, MediaItem item);
    void addMediaItems(java.util.List<MediaItem> items);
    void addMediaItems(int index, java.util.List<MediaItem> items);
    void moveMediaItem(int from, int to);
    void moveMediaItems(int from, int to, int newIndex);
    void replaceMediaItem(int index, MediaItem item);
    void replaceMediaItems(int from, int to, java.util.List<MediaItem> items);
    void removeMediaItem(int index);
    void removeMediaItems(int from, int to);
    void clearMediaItems();
    void prepare();
    int getPlaybackState();
    int getPlaybackSuppressionReason();
    boolean isPlaying();
    PlaybackException getPlayerError();
    void play();
    void pause();
    void setPlayWhenReady(boolean playWhenReady);
    boolean getPlayWhenReady();
    void setRepeatMode(int mode);
    int getRepeatMode();
    void setShuffleModeEnabled(boolean on);
    boolean getShuffleModeEnabled();
    boolean isLoading();
    void seekToDefaultPosition();
    void seekToDefaultPosition(int index);
    void seekTo(long positionMs);
    void seekTo(int index, long positionMs);
    long getSeekBackIncrement();
    void seekBack();
    long getSeekForwardIncrement();
    void seekForward();
    boolean hasPreviousMediaItem();
    void seekToPreviousMediaItem();
    long getMaxSeekToPreviousPosition();
    void seekToPrevious();
    boolean hasNextMediaItem();
    void seekToNextMediaItem();
    void seekToNext();
    void setPlaybackParameters(PlaybackParameters p);
    void setPlaybackSpeed(float speed);
    PlaybackParameters getPlaybackParameters();
    void stop();
    void release();
    Tracks getCurrentTracks();
    TrackSelectionParameters getTrackSelectionParameters();
    void setTrackSelectionParameters(TrackSelectionParameters p);
    MediaMetadata getMediaMetadata();
    Timeline getCurrentTimeline();
    int getCurrentPeriodIndex();
    int getCurrentMediaItemIndex();
    int getNextMediaItemIndex();
    int getPreviousMediaItemIndex();
    MediaItem getCurrentMediaItem();
    int getMediaItemCount();
    MediaItem getMediaItemAt(int index);
    long getDuration();
    long getCurrentPosition();
    long getBufferedPosition();
    int getBufferedPercentage();
    long getTotalBufferedDuration();
    boolean isCurrentMediaItemDynamic();
    boolean isCurrentMediaItemLive();
    boolean isCurrentMediaItemSeekable();
    long getContentDuration();
    long getContentPosition();
    long getContentBufferedPosition();
    AudioAttributes getAudioAttributes();
    void setVolume(float v);
    float getVolume();
    VideoSize getVideoSize();
    void setVideoTextureView(android.view.TextureView v);
    void clearVideoTextureView(android.view.TextureView v);
    void setVideoSurface(android.view.Surface s);
    void clearVideoSurface();
    boolean isCommandAvailable(int command);
    Commands getAvailableCommands();
}
