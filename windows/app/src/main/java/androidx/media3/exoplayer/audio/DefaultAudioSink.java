package androidx.media3.exoplayer.audio;

import androidx.media3.common.audio.AudioProcessor;

/** media3's DefaultAudioSink: its builder makes the PC's sink (app.jam.desktop.media.PcmSink), with the app's processors. */
public final class DefaultAudioSink {
    public static final int OUTPUT_MODE_PCM = 0, OFFLOAD_MODE_DISABLED = 0;
    private DefaultAudioSink() {}
    public static final class Builder {
        AudioProcessor[] processors = new AudioProcessor[0]; boolean floatOut, trackParams; int bufferUs = 500_000;
        public Builder() {}
        public Builder(android.content.Context c) {}
        public Builder setAudioProcessors(AudioProcessor[] p) { processors = p; return this; }
        public Builder setAudioProcessorChain(Object chain) { return this; }
        public Builder setEnableFloatOutput(boolean b) { floatOut = b; return this; }
        public Builder setEnableAudioTrackPlaybackParams(boolean b) { trackParams = b; return this; }
        public Builder setAudioTrackBufferSizeProvider(AudioTrackBufferSizeProvider p) { if (p instanceof DefaultAudioTrackBufferSizeProvider) bufferUs = ((DefaultAudioTrackBufferSizeProvider) p).minPcmBufferDurationUs; return this; }
        public Builder setAudioCapabilities(Object c) { return this; }
        public Builder setOffloadMode(int m) { return this; }
        public AudioSink build() { return new app.jam.desktop.media.PcmSink(processors, bufferUs); }
    }
}
