package androidx.media3.datasource;

import android.net.Uri;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class FileDataSource extends BaseDataSource {
    private RandomAccessFile file; private Uri uri; private long remaining;
    public FileDataSource() { super(false); }
    @Override public long open(DataSpec s) throws IOException {
        uri = s.uri; transferInitializing(s);
        // A content:// address (the PC's Music folder as MediaStore, a picked file): its file.
        java.io.File f = s.uri.getScheme() == null || "file".equals(s.uri.getScheme()) ? new java.io.File(s.uri.getPath()) : app.jam.desktop.shim.Files.INSTANCE.file(s.uri);
        if (f == null || !f.exists()) throw new DataSourceException(new java.io.FileNotFoundException(String.valueOf(s.uri)), androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
        file = new RandomAccessFile(f, "r"); file.seek(s.position);
        remaining = s.length == -1 ? file.length() - s.position : s.length;
        if (remaining < 0) throw new DataSourceException(androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        transferStarted(s); return remaining;
    }
    @Override public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0; if (remaining == 0) return -1;
        int n = file.read(b, off, (int) Math.min(len, remaining)); if (n > 0) { remaining -= n; bytesTransferred(n); } return n;
    }
    @Override public Uri getUri() { return uri; }
    @Override public void close() throws IOException { if (file != null) { file.close(); file = null; transferEnded(); } uri = null; }
    public static final class Factory implements DataSource.Factory { public FileDataSource createDataSource() { return new FileDataSource(); } public Factory setListener(TransferListener l) { return this; } }
}
