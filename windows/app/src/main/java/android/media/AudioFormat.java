package android.media;
public final class AudioFormat {
    public static final int ENCODING_INVALID = 0, ENCODING_DEFAULT = 1, ENCODING_PCM_16BIT = 2, ENCODING_PCM_8BIT = 3, ENCODING_PCM_FLOAT = 4, ENCODING_PCM_24BIT_PACKED = 21, ENCODING_PCM_32BIT = 22;
    public static final int CHANNEL_OUT_MONO = 4, CHANNEL_OUT_STEREO = 12, CHANNEL_IN_MONO = 16, CHANNEL_INVALID = 0;
    public static final int SAMPLE_RATE_UNSPECIFIED = 0;
    private final int encoding, rate, mask;
    private AudioFormat(int e, int r, int m) { encoding = e; rate = r; mask = m; }
    public int getEncoding() { return encoding; } public int getSampleRate() { return rate; } public int getChannelMask() { return mask; } public int getChannelCount() { return mask == CHANNEL_OUT_MONO ? 1 : 2; }
    public static final class Builder { int e = ENCODING_PCM_16BIT, r = 48000, m = CHANNEL_OUT_STEREO;
        public Builder setEncoding(int v) { e = v; return this; } public Builder setSampleRate(int v) { r = v; return this; } public Builder setChannelMask(int v) { m = v; return this; }
        public AudioFormat build() { return new AudioFormat(e, r, m); } }
}
