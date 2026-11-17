package androidx.media3.datasource;

import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

public final class RawResourceDataSource extends BaseDataSource {
    private InputStream in; private Uri uri;
    public RawResourceDataSource(android.content.Context c) { super(false); }
    public static Uri buildRawResourceUri(int id) { return Uri.parse("android.resource:///" + id); }
    @Override public long open(DataSpec s) throws IOException {
        uri = s.uri; transferInitializing(s);
        in = app.jam.desktop.shim.Files.INSTANCE.open(s.uri);
        if (in == null) throw new DataSourceException(androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
        in.skip(s.position); transferStarted(s); return s.length;
    }
    @Override public int read(byte[] b, int off, int len) throws IOException { int n = in.read(b, off, len); if (n > 0) bytesTransferred(n); return n; }
    @Override public Uri getUri() { return uri; }
    @Override public void close() throws IOException { if (in != null) { in.close(); in = null; transferEnded(); } }
}
