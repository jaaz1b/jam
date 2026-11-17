package androidx.media3.datasource;

import android.net.Uri;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Files and app resources here, http(s) through the given (or default) HTTP source. */
public final class DefaultDataSource implements DataSource {
    private final DataSource http; private DataSource active;
    private final java.util.ArrayList<TransferListener> listeners = new java.util.ArrayList<>();
    public DefaultDataSource(android.content.Context c, DataSource http) { this.http = http; }
    public DefaultDataSource(android.content.Context c, boolean redirects) { this(c, new DefaultHttpDataSource.Factory().createDataSource()); }
    @Override public void addTransferListener(TransferListener l) { listeners.add(l); http.addTransferListener(l); }
    @Override public long open(DataSpec s) throws IOException {
        String scheme = s.uri.getScheme();
        if ("http".equals(scheme) || "https".equals(scheme)) active = http;
        else if ("android.resource".equals(scheme) || "asset".equals(scheme)) active = new RawResourceDataSource(null);
        else active = new FileDataSource();
        if (active != http) for (TransferListener l : listeners) active.addTransferListener(l);
        return active.open(s);
    }
    @Override public int read(byte[] b, int off, int len) throws IOException { return active.read(b, off, len); }
    @Override public Uri getUri() { return active == null ? null : active.getUri(); }
    @Override public Map<String, List<String>> getResponseHeaders() { return active == null ? java.util.Collections.emptyMap() : active.getResponseHeaders(); }
    @Override public void close() throws IOException { if (active != null) { try { active.close(); } finally { active = null; } } }
    public static final class Factory implements DataSource.Factory {
        private final android.content.Context c; private final DataSource.Factory http; private TransferListener l;
        public Factory(android.content.Context c) { this(c, new DefaultHttpDataSource.Factory()); }
        public Factory(android.content.Context c, DataSource.Factory http) { this.c = c; this.http = http; }
        public Factory setTransferListener(TransferListener l) { this.l = l; return this; }
        @Override public DefaultDataSource createDataSource() { DefaultDataSource d = new DefaultDataSource(c, http.createDataSource()); if (l != null) d.addTransferListener(l); return d; }
    }
}
