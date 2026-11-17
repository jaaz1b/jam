package androidx.media3.exoplayer;

public final class ExoPlaybackException extends androidx.media3.common.PlaybackException {
    public static final int TYPE_SOURCE = 0, TYPE_RENDERER = 1, TYPE_UNEXPECTED = 2, TYPE_REMOTE = 3;
    public final int type;
    public ExoPlaybackException(String m, Throwable c, int code, int type) { super(m, c, code); this.type = type; }
    public java.io.IOException getSourceException() { return getCause() instanceof java.io.IOException ? (java.io.IOException) getCause() : new java.io.IOException(getCause()); }
}
