package androidx.media3.exoplayer.audio;

public class DefaultAudioTrackBufferSizeProvider implements AudioTrackBufferSizeProvider {
    public final int minPcmBufferDurationUs, maxPcmBufferDurationUs;
    DefaultAudioTrackBufferSizeProvider(int min, int max) { minPcmBufferDurationUs = min; maxPcmBufferDurationUs = max; }
    public static class Builder { int min = 250_000, max = 750_000;
        public Builder setMinPcmBufferDurationUs(int us) { min = us; return this; } public Builder setMaxPcmBufferDurationUs(int us) { max = us; return this; }
        public Builder setPcmBufferMultiplicationFactor(int f) { return this; } public Builder setPassthroughBufferDurationUs(int us) { return this; } public Builder setOffloadBufferDurationUs(int us) { return this; }
        public DefaultAudioTrackBufferSizeProvider build() { return new DefaultAudioTrackBufferSizeProvider(min, max); } }
}
