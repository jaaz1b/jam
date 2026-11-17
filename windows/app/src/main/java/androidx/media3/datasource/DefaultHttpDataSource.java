package androidx.media3.datasource;

import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** media3's HTTP source, on OkHttp. */
public class DefaultHttpDataSource extends BaseDataSource implements HttpDataSource {
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8000, DEFAULT_READ_TIMEOUT_MILLIS = 8000;
    private static final OkHttpClient BASE = new OkHttpClient.Builder().build();
    private final OkHttpClient client;
    private final String userAgent;
    private final RequestProperties defaults, props = new RequestProperties();
    private Response response; private InputStream body; private DataSpec spec; private long remaining = -1; private int code = -1;
    DefaultHttpDataSource(String ua, int connect, int read, boolean redirects, RequestProperties defaults) {
        super(true); userAgent = ua; this.defaults = defaults;
        client = BASE.newBuilder().connectTimeout(connect, java.util.concurrent.TimeUnit.MILLISECONDS).readTimeout(read, java.util.concurrent.TimeUnit.MILLISECONDS).followRedirects(true).followSslRedirects(redirects).build();
    }
    @Override public long open(DataSpec s) throws IOException {
        spec = s; transferInitializing(s);
        Request.Builder b = new Request.Builder().url(s.uri.toString());
        if (userAgent != null) b.header("User-Agent", userAgent);
        if (defaults != null) for (Map.Entry<String, String> e : defaults.getSnapshot().entrySet()) b.header(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : props.getSnapshot().entrySet()) b.header(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : s.httpRequestHeaders.entrySet()) b.header(e.getKey(), e.getValue());
        String range = HttpUtil.buildRangeRequestHeader(s.position, s.length);
        if (range != null) b.header("Range", range);
        if (s.httpMethod == DataSpec.HTTP_METHOD_POST) b.post(okhttp3.RequestBody.create(s.httpBody == null ? new byte[0] : s.httpBody));
        else if (s.httpMethod == DataSpec.HTTP_METHOD_HEAD) b.head();
        try { response = client.newCall(b.build()).execute(); }
        catch (IOException e) { throw new HttpDataSourceException(e, s, androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, HttpDataSourceException.TYPE_OPEN); }
        code = response.code();
        if (code < 200 || code > 299) {
            byte[] err = new byte[0]; try { err = response.body().bytes(); } catch (Exception ignored) {}
            Map<String, List<String>> h = response.headers().toMultimap(); response.close();
            if (code == 416) throw new DataSourceException(androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
            throw new InvalidResponseCodeException(code, response.message(), null, h, s, err);
        }
        body = response.body().byteStream();
        long len = HttpUtil.getContentLength(response.header("Content-Length"), response.header("Content-Range"));
        if (code == 200 && s.position > 0) { long skipped = body.skip(s.position); if (len >= 0) len -= skipped; }
        remaining = s.length != -1 ? s.length : len;
        transferStarted(s);
        return remaining;
    }
    @Override public int read(byte[] buf, int off, int len) throws IOException {
        if (len == 0) return 0;
        if (remaining == 0) return androidx.media3.common.C.RESULT_END_OF_INPUT;
        int n = body.read(buf, off, remaining > 0 ? (int) Math.min(len, remaining) : len);
        if (n == -1) return androidx.media3.common.C.RESULT_END_OF_INPUT;
        if (remaining > 0) remaining -= n;
        bytesTransferred(n);
        return n;
    }
    @Override public Uri getUri() { return response == null ? null : Uri.parse(response.request().url().toString()); }
    @Override public int getResponseCode() { return code; }
    @Override public Map<String, List<String>> getResponseHeaders() { return response == null ? java.util.Collections.emptyMap() : response.headers().toMultimap(); }
    @Override public void setRequestProperty(String n, String v) { props.set(n, v); }
    @Override public void clearRequestProperty(String n) { props.remove(n); }
    @Override public void clearAllRequestProperties() { props.clear(); }
    @Override public void close() { if (response != null) { response.close(); response = null; transferEnded(); } body = null; }

    public static final class Factory implements HttpDataSource.Factory {
        private String ua; private int connect = DEFAULT_CONNECT_TIMEOUT_MILLIS, read = DEFAULT_READ_TIMEOUT_MILLIS; private boolean crossProtocol; private final RequestProperties defaults = new RequestProperties();
        public Factory setUserAgent(String u) { ua = u; return this; }
        public Factory setConnectTimeoutMs(int ms) { connect = ms; return this; }
        public Factory setReadTimeoutMs(int ms) { read = ms; return this; }
        public Factory setAllowCrossProtocolRedirects(boolean b) { crossProtocol = b; return this; }
        public Factory setKeepPostFor302Redirects(boolean b) { return this; }
        public Factory setTransferListener(TransferListener l) { return this; }
        @Override public Factory setDefaultRequestProperties(Map<String, String> p) { defaults.clearAndSet(p); return this; }
        @Override public DefaultHttpDataSource createDataSource() { return new DefaultHttpDataSource(ua, connect, read, crossProtocol, defaults); }
    }
}
