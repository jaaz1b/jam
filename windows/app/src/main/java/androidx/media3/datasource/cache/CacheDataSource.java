package androidx.media3.datasource.cache;

import android.net.Uri;
import androidx.media3.datasource.*;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Reads what the cache has, the rest from upstream (written to the cache as it is read, from the cached end). */
public final class CacheDataSource implements DataSource {
    public static final int FLAG_BLOCK_ON_CACHE = 1, FLAG_IGNORE_CACHE_ON_ERROR = 2, FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS = 4;
    private final SimpleCache cache; private final DataSource upstream; private final int flags;
    private RandomAccessFile file; private DataSource reading; private String key; private long pos, remaining, total; private Uri uri;
    private final CacheKeyFactory keys;
    CacheDataSource(SimpleCache cache, DataSource upstream, int flags, CacheKeyFactory keys) { this.cache = cache; this.upstream = upstream; this.flags = flags; this.keys = keys; }
    public Cache getCache() { return cache; }
    @Override public void addTransferListener(TransferListener l) { upstream.addTransferListener(l); }
    @Override public long open(DataSpec s) throws IOException {
        key = keys.buildCacheKey(s); uri = s.uri; pos = s.position;
        total = cache.length(key);
        long have = cache.data(key).length();
        if (total >= 0 && pos + (s.length == -1 ? total - pos : s.length) <= have) {
            file = new RandomAccessFile(cache.data(key), "r"); file.seek(pos);
            remaining = s.length == -1 ? total - pos : s.length; return remaining;
        }
        reading = upstream;
        long len = upstream.open(s);
        if (s.position == 0 && s.length == -1 && len >= 0) { total = len; cache.setLength(key, len); }
        remaining = len; return len;
    }
    @Override public int read(byte[] b, int off, int len) throws IOException {
        if (remaining == 0) return -1;
        int n;
        if (file != null) { n = file.read(b, off, remaining > 0 ? (int) Math.min(len, remaining) : len); }
        else { n = reading.read(b, off, len); if (n > 0) cache.append(key, pos, b, off, n); }
        if (n > 0) { pos += n; if (remaining > 0) remaining -= n; }
        return n;
    }
    @Override public Uri getUri() { return reading != null ? reading.getUri() : uri; }
    @Override public java.util.Map<String, java.util.List<String>> getResponseHeaders() { return reading != null ? reading.getResponseHeaders() : java.util.Collections.emptyMap(); }
    @Override public void close() throws IOException { if (file != null) { file.close(); file = null; } if (reading != null) { try { reading.close(); } finally { reading = null; } } }
    public static final class Factory implements DataSource.Factory {
        private Cache cache; private DataSource.Factory upstream = new DefaultHttpDataSource.Factory(); private int flags;
        public Factory setCache(Cache c) { cache = c; return this; }
        public Factory setUpstreamDataSourceFactory(DataSource.Factory f) { upstream = f; return this; }
        public Factory setFlags(int f) { flags = f; return this; }
        public Factory setCacheWriteDataSinkFactory(Object f) { return this; }
        public Factory setCacheReadDataSourceFactory(Object f) { return this; }
        public Factory setEventListener(Object l) { return this; }
        private CacheKeyFactory keys = CacheKeyFactory.DEFAULT;
        public Factory setCacheKeyFactory(CacheKeyFactory f) { keys = f; return this; }
        public Cache getCache() { return cache; }
        @Override public CacheDataSource createDataSource() { return new CacheDataSource((SimpleCache) cache, upstream.createDataSource(), flags, keys); }
        public CacheDataSource createDataSourceForDownloading() { return createDataSource(); }
    }
}
