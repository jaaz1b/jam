package androidx.media3.common;

public class TrackSelectionParameters {
    public static final TrackSelectionParameters DEFAULT_WITHOUT_CONTEXT = new TrackSelectionParameters(AudioOffloadPreferences.DEFAULT, Integer.MAX_VALUE, Integer.MAX_VALUE);
    public final AudioOffloadPreferences audioOffloadPreferences; public final int maxVideoHeight, maxVideoWidth;
    public TrackSelectionParameters(AudioOffloadPreferences a, int maxH, int maxW) { audioOffloadPreferences = a; maxVideoHeight = maxH; maxVideoWidth = maxW; }
    public Builder buildUpon() { return new Builder(this); }
    public static class Builder {
        private AudioOffloadPreferences a; private int h, w;
        public Builder(android.content.Context c) { this(DEFAULT_WITHOUT_CONTEXT); }
        Builder(TrackSelectionParameters p) { a = p.audioOffloadPreferences; h = p.maxVideoHeight; w = p.maxVideoWidth; }
        public Builder setAudioOffloadPreferences(AudioOffloadPreferences p) { a = p; return this; }
        public Builder setMaxVideoSize(int maxW, int maxH) { w = maxW; h = maxH; return this; }
        public Builder setMaxVideoSizeSd() { return setMaxVideoSize(1279, 719); }
        public Builder clearVideoSizeConstraints() { return setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE); }
        public Builder setTrackTypeDisabled(int type, boolean disabled) { return this; }
        public Builder setPreferredAudioLanguage(String l) { return this; }
        public Builder setForceHighestSupportedBitrate(boolean b) { return this; }
        public Builder setMaxVideoBitrate(int b) { return this; }
        public TrackSelectionParameters build() { return new TrackSelectionParameters(a, h, w); }
    }
    public static final class AudioOffloadPreferences {
        public static final int AUDIO_OFFLOAD_MODE_DISABLED = 0, AUDIO_OFFLOAD_MODE_ENABLED = 1, AUDIO_OFFLOAD_MODE_REQUIRED = 2;
        public static final AudioOffloadPreferences DEFAULT = new Builder().build();
        public final int audioOffloadMode; public final boolean isGaplessSupportRequired, isSpeedChangeSupportRequired;
        AudioOffloadPreferences(int m, boolean g) { audioOffloadMode = m; isGaplessSupportRequired = g; isSpeedChangeSupportRequired = false; }
        public static final class Builder { int m; boolean g;
            public Builder setAudioOffloadMode(int v) { m = v; return this; } public Builder setIsGaplessSupportRequired(boolean v) { g = v; return this; } public Builder setIsSpeedChangeSupportRequired(boolean v) { return this; }
            public AudioOffloadPreferences build() { return new AudioOffloadPreferences(m, g); } }
    }
}
