package androidx.media3.datasource.cache;
public interface CacheKeyFactory {
    CacheKeyFactory DEFAULT = spec -> spec.key != null ? spec.key : spec.uri.toString();
    String buildCacheKey(androidx.media3.datasource.DataSpec spec);
}
