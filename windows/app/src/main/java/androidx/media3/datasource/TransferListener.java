package androidx.media3.datasource;

public interface TransferListener {
    void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork);
    void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork);
    void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred);
    void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork);
}
