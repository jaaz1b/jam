package androidx.media3.datasource.cache;

import androidx.media3.datasource.DataSpec;
import java.io.IOException;

/** Reads a whole DataSpec through a CacheDataSource, so it ends up in the cache. */
public final class CacheWriter {
    public interface ProgressListener { void onProgress(long requestLength, long bytesCached, long newBytesCached); }
    private final CacheDataSource source; private final DataSpec spec; private final ProgressListener listener; private volatile boolean canceled;
    public CacheWriter(CacheDataSource source, DataSpec spec, byte[] buffer, ProgressListener l) { this.source = source; this.spec = spec; this.listener = l; }
    public void cache() throws IOException {
        long len = source.open(spec); long done = 0; byte[] b = new byte[64 * 1024];
        try { int n; while (!canceled && (n = source.read(b, 0, b.length)) != -1) { done += n; if (listener != null) listener.onProgress(len, done, n); } }
        finally { source.close(); }
        if (canceled) throw new java.io.InterruptedIOException();
    }
    public void cancel() { canceled = true; }
}
