package androidx.media3.exoplayer.audio;

import androidx.media3.common.*;
import java.nio.ByteBuffer;

/** media3's AudioSink: where decoded PCM goes (configure, handleBuffer, play/pause, position). */
public interface AudioSink {
    int SINK_FORMAT_SUPPORTED_DIRECTLY = 2, SINK_FORMAT_SUPPORTED_WITH_TRANSCODING = 1, SINK_FORMAT_UNSUPPORTED = 0;
    long CURRENT_POSITION_NOT_SET = Long.MIN_VALUE;
    interface Listener {
        default void onPositionDiscontinuity() {}
        default void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {}
        default void onSkipSilenceEnabledChanged(boolean on) {}
        default void onOffloadBufferEmptying() {}
        default void onAudioSinkError(Exception e) {}
    }
    class ConfigurationException extends Exception { public final Format format; public ConfigurationException(String m, Format f) { super(m); format = f; } public ConfigurationException(Throwable t, Format f) { super(t); format = f; } }
    class InitializationException extends Exception { public final boolean isRecoverable; public final Format format; public InitializationException(String m, Format f) { super(m); format = f; isRecoverable = false; } }
    class WriteException extends Exception { public final int errorCode; public final boolean isRecoverable; public final Format format; public WriteException(int code, Format f, boolean r) { super("write " + code); errorCode = code; format = f; isRecoverable = r; } }
    void setListener(Listener l);
    boolean supportsFormat(Format f);
    int getFormatSupport(Format f);
    long getCurrentPositionUs(boolean sourceEnded);
    void configure(Format inputFormat, int specifiedBufferSize, int[] outputChannels) throws ConfigurationException;
    void play();
    void handleDiscontinuity();
    boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount) throws InitializationException, WriteException;
    void playToEndOfStream() throws WriteException;
    boolean isEnded();
    boolean hasPendingData();
    void setPlaybackParameters(PlaybackParameters p);
    PlaybackParameters getPlaybackParameters();
    void setSkipSilenceEnabled(boolean on);
    boolean getSkipSilenceEnabled();
    void setAudioAttributes(AudioAttributes a);
    AudioAttributes getAudioAttributes();
    void setAudioSessionId(int id);
    default void setAuxEffectInfo(Object info) {}
    default void setPreferredDevice(Object d) {}
    default void setOutputStreamOffsetUs(long outputStreamOffsetUs) {}
    default long getAudioTrackBufferSizeUs() { return 0; }
    void enableTunnelingV21();
    void disableTunneling();
    default void setOffloadMode(int mode) {}
    default void setOffloadDelayPadding(int delay, int padding) {}
    void setVolume(float v);
    void pause();
    void flush();
    default void experimentalFlushWithoutAudioTrackRelease() { flush(); }
    void reset();
    default void release() {}
}
