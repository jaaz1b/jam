package androidx.media3.datasource.cache;

public final class LeastRecentlyUsedCacheEvictor implements CacheEvictor {
    public final long maxBytes;
    public LeastRecentlyUsedCacheEvictor(long maxBytes) { this.maxBytes = maxBytes; }
    @Override public boolean requiresCacheSpanTouches() { return true; }
}
