package androidx.media3.datasource.cache;

public final class NoOpCacheEvictor implements CacheEvictor { @Override public boolean requiresCacheSpanTouches() { return false; } }
