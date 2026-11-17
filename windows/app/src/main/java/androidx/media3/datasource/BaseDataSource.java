package androidx.media3.datasource;

import java.util.ArrayList;

public abstract class BaseDataSource implements DataSource {
    private final boolean isNetwork;
    private final ArrayList<TransferListener> listeners = new ArrayList<>(1);
    private DataSpec dataSpec;
    protected BaseDataSource(boolean isNetwork) { this.isNetwork = isNetwork; }
    @Override public final void addTransferListener(TransferListener l) { if (!listeners.contains(l)) listeners.add(l); }
    protected final void transferInitializing(DataSpec s) { for (TransferListener l : listeners) l.onTransferInitializing(this, s, isNetwork); }
    protected final void transferStarted(DataSpec s) { dataSpec = s; for (TransferListener l : listeners) l.onTransferStart(this, s, isNetwork); }
    protected final void bytesTransferred(int n) { if (dataSpec != null) for (TransferListener l : listeners) l.onBytesTransferred(this, dataSpec, isNetwork, n); }
    protected final void transferEnded() { if (dataSpec != null) { for (TransferListener l : listeners) l.onTransferEnd(this, dataSpec, isNetwork); dataSpec = null; } }
}
