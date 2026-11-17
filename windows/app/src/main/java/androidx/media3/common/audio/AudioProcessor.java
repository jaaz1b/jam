package androidx.media3.common.audio;

public interface AudioProcessor {
    java.nio.ByteBuffer EMPTY_BUFFER = java.nio.ByteBuffer.allocateDirect(0).order(java.nio.ByteOrder.nativeOrder());
    final class AudioFormat {
        public static final AudioFormat NOT_SET = new AudioFormat(-1, -1, -1);
        public final int sampleRate, channelCount, encoding, bytesPerFrame;
        public AudioFormat(int sampleRate, int channelCount, int encoding) { this.sampleRate = sampleRate; this.channelCount = channelCount; this.encoding = encoding; bytesPerFrame = encoding > 0 && channelCount > 0 ? androidx.media3.common.C.bytesPerSample(encoding) * channelCount : -1; }
        public AudioFormat(androidx.media3.common.Format f) { this(f.sampleRate, f.channelCount, f.pcmEncoding); }
        @Override public String toString() { return "AudioFormat[sampleRate=" + sampleRate + ", channelCount=" + channelCount + ", encoding=" + encoding + "]"; }
        @Override public boolean equals(Object o) { return o instanceof AudioFormat && ((AudioFormat) o).sampleRate == sampleRate && ((AudioFormat) o).channelCount == channelCount && ((AudioFormat) o).encoding == encoding; }
        @Override public int hashCode() { return sampleRate * 31 * 31 + channelCount * 31 + encoding; }
    }
    final class UnhandledAudioFormatException extends Exception {
        public final AudioFormat inputAudioFormat;
        public UnhandledAudioFormatException(AudioFormat f) { super("Unhandled input format: " + f); inputAudioFormat = f; }
        public UnhandledAudioFormatException(String m, AudioFormat f) { super(m + " " + f); inputAudioFormat = f; }
    }
    final class StreamMetadata { public static final StreamMetadata DEFAULT = new StreamMetadata(0); public final long positionOffsetUs; public StreamMetadata(long p) { positionOffsetUs = p; } }
    AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException;
    boolean isActive();
    void queueInput(java.nio.ByteBuffer inputBuffer);
    void queueEndOfStream();
    java.nio.ByteBuffer getOutput();
    boolean isEnded();
    @Deprecated void flush();
    default void flush(StreamMetadata m) { flush(); }
    void reset();
    default long getDurationAfterProcessorApplied(long durationUs) { return durationUs; }
}
