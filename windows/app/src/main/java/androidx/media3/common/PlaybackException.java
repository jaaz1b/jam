package androidx.media3.common;

public class PlaybackException extends Exception {
    public static final int ERROR_CODE_UNSPECIFIED = 1000, ERROR_CODE_REMOTE_ERROR = 1001, ERROR_CODE_BEHIND_LIVE_WINDOW = 1002, ERROR_CODE_TIMEOUT = 1003, ERROR_CODE_FAILED_RUNTIME_CHECK = 1004;
    public static final int ERROR_CODE_IO_UNSPECIFIED = 2000, ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001, ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002, ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;
    public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004, ERROR_CODE_IO_FILE_NOT_FOUND = 2005, ERROR_CODE_IO_NO_PERMISSION = 2006, ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007, ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008;
    public static final int ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001, ERROR_CODE_PARSING_MANIFEST_MALFORMED = 3002, ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003;
    public static final int ERROR_CODE_DECODER_INIT_FAILED = 4001, ERROR_CODE_DECODING_FAILED = 4003, ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4005, ERROR_CODE_AUDIO_TRACK_INIT_FAILED = 5001, ERROR_CODE_AUDIO_TRACK_WRITE_FAILED = 5002;
    public static final int ERROR_CODE_DRM_UNSPECIFIED = 6000;
    public final int errorCode;
    public final long timestampMs;
    public PlaybackException(String message, Throwable cause, int errorCode) { super(message, cause); this.errorCode = errorCode; this.timestampMs = android.os.SystemClock.elapsedRealtime(); }
    public String getErrorCodeName() { return getErrorCodeName(errorCode); }
    public static String getErrorCodeName(int code) {
        for (java.lang.reflect.Field fl : PlaybackException.class.getFields()) {
            try { if (fl.getName().startsWith("ERROR_CODE_") && fl.getInt(null) == code) return fl.getName(); } catch (Exception ignored) {}
        }
        return "invalid error code";
    }
}
