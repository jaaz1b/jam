package androidx.media3.exoplayer.audio;

import androidx.media3.common.*;
import java.nio.ByteBuffer;

public class ForwardingAudioSink implements AudioSink {
    private final AudioSink sink;
    public ForwardingAudioSink(AudioSink sink) { this.sink = sink; }
    public AudioSink delegate() { return sink; }
    @Override public void setListener(Listener l) { sink.setListener(l); }
    @Override public boolean supportsFormat(Format f) { return sink.supportsFormat(f); }
    @Override public int getFormatSupport(Format f) { return sink.getFormatSupport(f); }
    @Override public long getCurrentPositionUs(boolean e) { return sink.getCurrentPositionUs(e); }
    @Override public void configure(Format f, int s, int[] c) throws ConfigurationException { sink.configure(f, s, c); }
    @Override public void play() { sink.play(); }
    @Override public void handleDiscontinuity() { sink.handleDiscontinuity(); }
    @Override public boolean handleBuffer(ByteBuffer b, long t, int n) throws InitializationException, WriteException { return sink.handleBuffer(b, t, n); }
    @Override public void playToEndOfStream() throws WriteException { sink.playToEndOfStream(); }
    @Override public boolean isEnded() { return sink.isEnded(); }
    @Override public boolean hasPendingData() { return sink.hasPendingData(); }
    @Override public void setPlaybackParameters(PlaybackParameters p) { sink.setPlaybackParameters(p); }
    @Override public PlaybackParameters getPlaybackParameters() { return sink.getPlaybackParameters(); }
    @Override public void setSkipSilenceEnabled(boolean s) { sink.setSkipSilenceEnabled(s); }
    @Override public boolean getSkipSilenceEnabled() { return sink.getSkipSilenceEnabled(); }
    @Override public void setAudioAttributes(AudioAttributes a) { sink.setAudioAttributes(a); }
    @Override public AudioAttributes getAudioAttributes() { return sink.getAudioAttributes(); }
    @Override public void setAudioSessionId(int id) { sink.setAudioSessionId(id); }
    @Override public void setOutputStreamOffsetUs(long o) { sink.setOutputStreamOffsetUs(o); }
    @Override public long getAudioTrackBufferSizeUs() { return sink.getAudioTrackBufferSizeUs(); }
    @Override public void enableTunnelingV21() { sink.enableTunnelingV21(); }
    @Override public void disableTunneling() { sink.disableTunneling(); }
    @Override public void setVolume(float v) { sink.setVolume(v); }
    @Override public void pause() { sink.pause(); }
    @Override public void flush() { sink.flush(); }
    @Override public void reset() { sink.reset(); }
    @Override public void release() { sink.release(); }
}
