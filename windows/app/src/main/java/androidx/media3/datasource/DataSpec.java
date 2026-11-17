package androidx.media3.datasource;

import android.net.Uri;
import java.util.Collections;
import java.util.Map;

public final class DataSpec {
    public static final int HTTP_METHOD_GET = 1, HTTP_METHOD_POST = 2, HTTP_METHOD_HEAD = 3;
    public static final int FLAG_ALLOW_GZIP = 1, FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN = 2, FLAG_ALLOW_CACHE_FRAGMENTATION = 4, FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED = 8;
    public final Uri uri; public final long uriPositionOffset, position, length, absoluteStreamPosition; public final int httpMethod, flags; public final byte[] httpBody; public final Map<String, String> httpRequestHeaders; public final String key; public final Object customData;
    public DataSpec(Uri uri) { this(uri, 0, -1); }
    public DataSpec(Uri uri, long position, long length) { this(uri, HTTP_METHOD_GET, null, Collections.emptyMap(), position, length, null, 0, null); }
    public DataSpec(Uri uri, long position, long length, String key) { this(uri, HTTP_METHOD_GET, null, Collections.emptyMap(), position, length, key, 0, null); }
    private DataSpec(Uri uri, int method, byte[] body, Map<String, String> headers, long position, long length, String key, int flags, Object custom) {
        this.uri = uri; this.httpMethod = method; this.httpBody = body; this.httpRequestHeaders = headers; this.position = position; this.absoluteStreamPosition = position; this.uriPositionOffset = 0; this.length = length; this.key = key; this.flags = flags; this.customData = custom;
    }
    public boolean isFlagSet(int f) { return (flags & f) == f; }
    public String getHttpMethodString() { return httpMethod == HTTP_METHOD_POST ? "POST" : httpMethod == HTTP_METHOD_HEAD ? "HEAD" : "GET"; }
    public DataSpec subrange(long offset) { return subrange(offset, length == -1 ? -1 : length - offset); }
    public DataSpec subrange(long offset, long len) { return new DataSpec(uri, httpMethod, httpBody, httpRequestHeaders, position + offset, len, key, flags, customData); }
    public DataSpec withUri(Uri u) { return new DataSpec(u, httpMethod, httpBody, httpRequestHeaders, position, length, key, flags, customData); }
    public DataSpec withRequestHeaders(Map<String, String> h) { return new DataSpec(uri, httpMethod, httpBody, h, position, length, key, flags, customData); }
    public DataSpec withAdditionalHeaders(Map<String, String> h) { java.util.HashMap<String, String> m = new java.util.HashMap<>(httpRequestHeaders); m.putAll(h); return withRequestHeaders(m); }
    public Builder buildUpon() { return new Builder().setUri(uri).setHttpMethod(httpMethod).setHttpBody(httpBody).setHttpRequestHeaders(httpRequestHeaders).setPosition(position).setLength(length).setKey(key).setFlags(flags).setCustomData(customData); }
    @Override public String toString() { return "DataSpec[" + getHttpMethodString() + " " + uri + ", " + position + ", " + length + ", " + key + ", " + flags + "]"; }
    public static final class Builder {
        Uri uri; int method = HTTP_METHOD_GET; byte[] body; Map<String, String> headers = Collections.emptyMap(); long position, length = -1; String key; int flags; Object custom;
        public Builder setUri(Uri u) { uri = u; return this; } public Builder setUri(String u) { uri = Uri.parse(u); return this; }
        public Builder setHttpMethod(int m) { method = m; return this; } public Builder setHttpBody(byte[] b) { body = b; return this; }
        public Builder setHttpRequestHeaders(Map<String, String> h) { headers = h; return this; }
        public Builder setPosition(long p) { position = p; return this; } public Builder setLength(long l) { length = l; return this; }
        public Builder setKey(String k) { key = k; return this; } public Builder setFlags(int f) { flags = f; return this; } public Builder setCustomData(Object c) { custom = c; return this; }
        public Builder setUriPositionOffset(long o) { return this; }
        public DataSpec build() { return new DataSpec(uri, method, body, headers, position, length, key, flags, custom); }
    }
}
