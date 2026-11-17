package androidx.media3.common.util;

public final class Util {
    public static String getStringForTime(StringBuilder b, java.util.Formatter f, long ms) { long s = (ms + 500) / 1000; return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60); }
    public static long usToMs(long us) { return us == androidx.media3.common.C.TIME_UNSET ? us : us / 1000; }
    public static long msToUs(long ms) { return ms == androidx.media3.common.C.TIME_UNSET ? ms : ms * 1000; }
    public static int getPcmFrameSize(int encoding, int channels) { return androidx.media3.common.C.bytesPerSample(encoding) * channels; }
    public static String getUserAgent(android.content.Context c, String app) { return app + " (Windows) AndroidXMedia3/1.6"; }
    public static int constrainValue(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    public static long constrainValue(long v, long min, long max) { return Math.max(min, Math.min(max, v)); }
    public static float constrainValue(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    public static final int SDK_INT = 36;
}
