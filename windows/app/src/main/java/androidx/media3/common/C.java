package androidx.media3.common;

public final class C {
    public static final long TIME_UNSET = Long.MIN_VALUE + 1, TIME_END_OF_SOURCE = Long.MIN_VALUE;
    public static final int LENGTH_UNSET = -1, RESULT_END_OF_INPUT = -1, INDEX_UNSET = -1, POSITION_UNSET = -1;
    public static final int ENCODING_INVALID = 0, ENCODING_PCM_16BIT = 2, ENCODING_PCM_8BIT = 3, ENCODING_PCM_FLOAT = 4, ENCODING_PCM_24BIT = 0x50000000, ENCODING_PCM_32BIT = 0x60000000;
    public static final int TRACK_TYPE_UNKNOWN = -1, TRACK_TYPE_DEFAULT = 0, TRACK_TYPE_AUDIO = 1, TRACK_TYPE_VIDEO = 2, TRACK_TYPE_TEXT = 3;
    public static final int USAGE_MEDIA = 1, AUDIO_CONTENT_TYPE_MUSIC = 2, AUDIO_CONTENT_TYPE_SPEECH = 1, AUDIO_CONTENT_TYPE_MOVIE = 3, CONTENT_TYPE_MUSIC = 2, CONTENT_TYPE_SPEECH = 1;
    public static final int WAKE_MODE_NONE = 0, WAKE_MODE_LOCAL = 1, WAKE_MODE_NETWORK = 2;
    public static final int AUDIO_SESSION_ID_UNSET = 0, VIDEO_SCALING_MODE_SCALE_TO_FIT = 1;
    public static final int SELECTION_FLAG_DEFAULT = 1, ROLE_FLAG_MAIN = 1;
    public static final int DATA_TYPE_MEDIA = 1, CRYPTO_TYPE_NONE = 0;
    public static final String LANGUAGE_UNDETERMINED = "und";
    public static final long MICROS_PER_SECOND = 1_000_000L, NANOS_PER_SECOND = 1_000_000_000L;
    public static final int CONTENT_TYPE_DASH = 0, CONTENT_TYPE_HLS = 2, CONTENT_TYPE_OTHER = 4, CONTENT_TYPE_SS = 1, CONTENT_TYPE_RTSP = 3;
    public static int bytesPerSample(int encoding) { switch (encoding) { case ENCODING_PCM_8BIT: return 1; case ENCODING_PCM_24BIT: return 3; case ENCODING_PCM_FLOAT: case ENCODING_PCM_32BIT: return 4; default: return 2; } }
}
