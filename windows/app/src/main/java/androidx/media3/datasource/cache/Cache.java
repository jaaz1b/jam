package androidx.media3.datasource.cache;

public interface Cache {
    java.util.Set<String> getKeys();
    void removeResource(String key);
    long getCacheSpace();
    void release();
    boolean isCached(String key, long position, long length);
    long getCachedBytes(String key, long position, long length);
    ContentMetadata getContentMetadata(String key);
    interface Listener {}
    class CacheException extends java.io.IOException { public CacheException(String m) { super(m); } public CacheException(Throwable c) { super(c); } }
}
