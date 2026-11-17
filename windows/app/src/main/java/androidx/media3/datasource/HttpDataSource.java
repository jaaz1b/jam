package androidx.media3.datasource;

import java.util.List;
import java.util.Map;

public interface HttpDataSource extends DataSource {
    interface Factory extends DataSource.Factory { HttpDataSource createDataSource(); Factory setDefaultRequestProperties(Map<String, String> p); }
    final class RequestProperties {
        private final java.util.HashMap<String, String> map = new java.util.HashMap<>();
        public synchronized void set(String k, String v) { map.put(k, v); } public synchronized void set(Map<String, String> m) { map.putAll(m); }
        public synchronized void clearAndSet(Map<String, String> m) { map.clear(); map.putAll(m); } public synchronized void remove(String k) { map.remove(k); } public synchronized void clear() { map.clear(); }
        public synchronized Map<String, String> getSnapshot() { return new java.util.HashMap<>(map); }
    }
    class HttpDataSourceException extends DataSourceException {
        public static final int TYPE_OPEN = 1, TYPE_READ = 2, TYPE_CLOSE = 3;
        public final DataSpec dataSpec; public final int type;
        public HttpDataSourceException(DataSpec s, int reason, int type) { super(reason); dataSpec = s; this.type = type; }
        public HttpDataSourceException(String m, DataSpec s, int reason, int type) { super(m, reason); dataSpec = s; this.type = type; }
        public HttpDataSourceException(java.io.IOException cause, DataSpec s, int reason, int type) { super(cause, reason); dataSpec = s; this.type = type; }
        public HttpDataSourceException(String m, java.io.IOException cause, DataSpec s, int reason, int type) { super(m, cause, reason); dataSpec = s; this.type = type; }
    }
    final class InvalidResponseCodeException extends HttpDataSourceException {
        public final int responseCode; public final String responseMessage; public final Map<String, List<String>> headerFields; public final byte[] responseBody;
        public InvalidResponseCodeException(int code, String message, java.io.IOException cause, Map<String, List<String>> headers, DataSpec s, byte[] body) {
            super("Response code: " + code, cause, s, androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, TYPE_OPEN);
            responseCode = code; responseMessage = message; headerFields = headers; responseBody = body;
        }
    }
    final class InvalidContentTypeException extends HttpDataSourceException {
        public final String contentType;
        public InvalidContentTypeException(String t, DataSpec s) { super("Invalid content type: " + t, s, androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE, TYPE_OPEN); contentType = t; }
    }
    void setRequestProperty(String name, String value);
    void clearRequestProperty(String name);
    void clearAllRequestProperties();
    int getResponseCode();
    Map<String, List<String>> getResponseHeaders();
}
