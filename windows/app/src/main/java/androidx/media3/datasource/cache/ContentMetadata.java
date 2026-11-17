package androidx.media3.datasource.cache;

public interface ContentMetadata {
    String KEY_CONTENT_LENGTH = "exo_len", KEY_REDIRECTED_URI = "exo_redir";
    long get(String key, long defaultValue);
    String get(String key, String defaultValue);
    static long getContentLength(ContentMetadata m) { return m.get(KEY_CONTENT_LENGTH, -1L); }
}
