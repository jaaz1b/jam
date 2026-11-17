package androidx.media3.exoplayer;

public class DefaultLoadControl implements LoadControl {
    public static final int DEFAULT_MIN_BUFFER_MS = 50000, DEFAULT_MAX_BUFFER_MS = 50000, DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500, DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000;
    public static final class Builder {
        public Builder setBufferDurationsMs(int min, int max, int forPlayback, int afterRebuffer) { return this; }
        public Builder setBackBuffer(int ms, boolean retain) { return this; }
        public Builder setPrioritizeTimeOverSizeThresholds(boolean b) { return this; }
        public Builder setTargetBufferBytes(int b) { return this; }
        public DefaultLoadControl build() { return new DefaultLoadControl(); }
    }
}
