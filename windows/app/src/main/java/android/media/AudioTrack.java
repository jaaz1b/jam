package android.media;
/** An AudioTrack: what the app writes (the speaker's keep-alive silence) goes nowhere on the PC. */
public class AudioTrack {
    public static final int MODE_STREAM = 1, MODE_STATIC = 0, PERFORMANCE_MODE_NONE = 0, PERFORMANCE_MODE_LOW_LATENCY = 1, PERFORMANCE_MODE_POWER_SAVING = 2;
    public static final int PLAYSTATE_STOPPED = 1, PLAYSTATE_PAUSED = 2, PLAYSTATE_PLAYING = 3, STATE_INITIALIZED = 1, WRITE_BLOCKING = 0, WRITE_NON_BLOCKING = 1, ERROR = -1, SUCCESS = 0;
    private int state = PLAYSTATE_STOPPED;
    public static int getMinBufferSize(int rate, int mask, int enc) { return 4096; }
    public void play() { state = PLAYSTATE_PLAYING; }
    public void pause() { state = PLAYSTATE_PAUSED; }
    public void stop() { state = PLAYSTATE_STOPPED; }
    public void flush() {}
    public void release() {}
    public int write(byte[] b, int off, int len) { return len; }
    public int write(short[] b, int off, int len) { return len; }
    public int write(float[] b, int off, int len, int mode) { return len; }
    public int write(java.nio.ByteBuffer b, int size, int mode) { b.position(b.position() + size); return size; }
    public int setVolume(float v) { return SUCCESS; }
    public int getPlayState() { return state; }
    public int getState() { return STATE_INITIALIZED; }
    public int getAudioSessionId() { return 1; }
    public static final class Builder {
        public Builder setAudioAttributes(AudioAttributes a) { return this; } public Builder setAudioFormat(AudioFormat f) { return this; } public Builder setBufferSizeInBytes(int b) { return this; }
        public Builder setTransferMode(int m) { return this; } public Builder setPerformanceMode(int m) { return this; } public Builder setSessionId(int s) { return this; }
        public AudioTrack build() { return new AudioTrack(); } }
}
