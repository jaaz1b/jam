package androidx.media3.datasource;

public class DataSourceException extends java.io.IOException {
    public final int reason;
    public DataSourceException(int reason) { this.reason = reason; }
    public DataSourceException(String message, Throwable cause, int reason) { super(message, cause); this.reason = reason; }
    public DataSourceException(Throwable cause, int reason) { super(cause); this.reason = reason; }
    public DataSourceException(String message, int reason) { super(message); this.reason = reason; }
    public static boolean isCausedByPositionOutOfRange(java.io.IOException e) { Throwable t = e; while (t != null) { if (t instanceof DataSourceException && ((DataSourceException) t).reason == androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) return true; t = t.getCause(); } return false; }
}
