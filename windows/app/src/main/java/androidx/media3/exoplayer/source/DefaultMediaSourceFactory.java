package androidx.media3.exoplayer.source;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;

public final class DefaultMediaSourceFactory implements MediaSource.Factory {
    private final DataSource.Factory data;
    public DefaultMediaSourceFactory(DataSource.Factory data) { this.data = data; }
    public DefaultMediaSourceFactory(android.content.Context c) { this(new androidx.media3.datasource.DefaultDataSource.Factory(c)); }
    @Override public DefaultMediaSourceFactory setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy p) { return this; }
    public DefaultMediaSourceFactory setDataSourceFactory(DataSource.Factory d) { return new DefaultMediaSourceFactory(d); }
    public DataSource.Factory dataSourceFactory() { return data; }
    @Override public MediaSource createMediaSource(MediaItem item) { return new DesktopMediaSource(item, data, "default"); }
}
