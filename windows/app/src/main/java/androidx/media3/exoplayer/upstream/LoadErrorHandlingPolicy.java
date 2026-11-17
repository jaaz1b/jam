package androidx.media3.exoplayer.upstream;

public interface LoadErrorHandlingPolicy {
    final class LoadErrorInfo {
        public final java.io.IOException exception; public final int errorCount; public final Object loadEventInfo, mediaLoadData;
        public LoadErrorInfo(Object info, Object data, java.io.IOException e, int count) { loadEventInfo = info; mediaLoadData = data; exception = e; errorCount = count; }
    }
    long getRetryDelayMsFor(LoadErrorInfo info);
    int getMinimumLoadableRetryCount(int dataType);
}
