package android.support.v4.media.session;

public final class PlaybackStateCompat implements android.os.Parcelable {
    public static final int STATE_NONE = 0, STATE_STOPPED = 1, STATE_PAUSED = 2, STATE_PLAYING = 3, STATE_FAST_FORWARDING = 4, STATE_REWINDING = 5, STATE_BUFFERING = 6, STATE_ERROR = 7, STATE_CONNECTING = 8, STATE_SKIPPING_TO_NEXT = 10, STATE_SKIPPING_TO_PREVIOUS = 9;
    public static final long ACTION_STOP = 1, ACTION_PAUSE = 2, ACTION_PLAY = 4, ACTION_REWIND = 8, ACTION_SKIP_TO_PREVIOUS = 16, ACTION_SKIP_TO_NEXT = 32, ACTION_FAST_FORWARD = 64, ACTION_SET_RATING = 128, ACTION_SEEK_TO = 256, ACTION_PLAY_PAUSE = 512, ACTION_PLAY_FROM_MEDIA_ID = 1024, ACTION_PLAY_FROM_SEARCH = 2048, ACTION_SKIP_TO_QUEUE_ITEM = 4096, ACTION_PLAY_FROM_URI = 8192, ACTION_PREPARE = 16384, ACTION_SET_REPEAT_MODE = 262144, ACTION_SET_SHUFFLE_MODE = 2097152, ACTION_SET_PLAYBACK_SPEED = 4194304;
    public static final int SHUFFLE_MODE_NONE = 0, SHUFFLE_MODE_ALL = 1, SHUFFLE_MODE_GROUP = 2, REPEAT_MODE_NONE = 0, REPEAT_MODE_ONE = 1, REPEAT_MODE_ALL = 2;
    public static final long PLAYBACK_POSITION_UNKNOWN = -1;
    public static final int ERROR_CODE_UNKNOWN_ERROR = 0, ERROR_CODE_NOT_SUPPORTED = 2;
    private final int state; private final long position, actions, updateTime, activeQueueItemId; private final float speed; private final CharSequence error; private final android.os.Bundle extras;
    private PlaybackStateCompat(Builder b) { state = b.state; position = b.pos; speed = b.speed; actions = b.actions; updateTime = b.time; activeQueueItemId = b.queueId; error = b.error; extras = b.extras; }
    public int getState() { return state; } public long getPosition() { return position; } public float getPlaybackSpeed() { return speed; } public long getActions() { return actions; } public long getLastPositionUpdateTime() { return updateTime; }
    public long getActiveQueueItemId() { return activeQueueItemId; } public CharSequence getErrorMessage() { return error; } public android.os.Bundle getExtras() { return extras; }
    public static final class CustomAction { public static final class Builder { public Builder(String a, CharSequence n, int icon) {} public Builder setExtras(android.os.Bundle e) { return this; } public CustomAction build() { return new CustomAction(); } } }
    public static final class Builder { int state; long pos, actions, time, queueId = -1; float speed = 1f; CharSequence error; android.os.Bundle extras;
        public Builder() {} public Builder(PlaybackStateCompat s) { state = s.state; pos = s.position; speed = s.speed; actions = s.actions; time = s.updateTime; }
        public Builder setState(int s, long p, float sp) { return setState(s, p, sp, android.os.SystemClock.elapsedRealtime()); }
        public Builder setState(int s, long p, float sp, long t) { state = s; pos = p; speed = sp; time = t; return this; }
        public Builder setActions(long a) { actions = a; return this; } public Builder setActiveQueueItemId(long id) { queueId = id; return this; }
        public Builder setErrorMessage(CharSequence e) { error = e; return this; } public Builder setErrorMessage(int code, CharSequence e) { error = e; return this; }
        public Builder setExtras(android.os.Bundle e) { extras = e; return this; } public Builder addCustomAction(CustomAction a) { return this; } public Builder addCustomAction(String a, String n, int i) { return this; } public Builder setBufferedPosition(long p) { return this; }
        public PlaybackStateCompat build() { return new PlaybackStateCompat(this); } }
}
