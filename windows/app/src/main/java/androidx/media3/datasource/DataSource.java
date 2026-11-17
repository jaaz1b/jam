package androidx.media3.datasource;

import android.net.Uri;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface DataSource extends DataReader {
    interface Factory { DataSource createDataSource(); }
    void addTransferListener(TransferListener transferListener);
    long open(DataSpec dataSpec) throws IOException;
    Uri getUri();
    default Map<String, List<String>> getResponseHeaders() { return java.util.Collections.emptyMap(); }
    void close() throws IOException;
}
