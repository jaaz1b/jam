package androidx.media3.common;

public final class PlaybackParameters {
    public static final PlaybackParameters DEFAULT = new PlaybackParameters(1f);
    public final float speed, pitch;
    public PlaybackParameters(float speed) { this(speed, 1f); }
    public PlaybackParameters(float speed, float pitch) { this.speed = speed; this.pitch = pitch; }
    public PlaybackParameters withSpeed(float s) { return new PlaybackParameters(s, pitch); }
    public long getMediaTimeUsForPlayoutTimeMs(long ms) { return (long) (ms * 1000 * speed); }
    @Override public boolean equals(Object o) { return o instanceof PlaybackParameters && ((PlaybackParameters) o).speed == speed && ((PlaybackParameters) o).pitch == pitch; }
    @Override public int hashCode() { return Float.hashCode(speed) * 31 + Float.hashCode(pitch); }
}
