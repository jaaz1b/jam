package androidx.media3.exoplayer.upstream;

public class DefaultLoadErrorHandlingPolicy implements LoadErrorHandlingPolicy {
    public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;
    public DefaultLoadErrorHandlingPolicy() {}
    public DefaultLoadErrorHandlingPolicy(int min) {}
    @Override public long getRetryDelayMsFor(LoadErrorInfo info) { return Math.min((info.errorCount - 1) * 1000L, 5000L); }
    @Override public int getMinimumLoadableRetryCount(int dataType) { return DEFAULT_MIN_LOADABLE_RETRY_COUNT; }
}
