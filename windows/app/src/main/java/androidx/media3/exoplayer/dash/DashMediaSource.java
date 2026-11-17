package androidx.media3.exoplayer.dash;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;

public final class DashMediaSource extends androidx.media3.exoplayer.source.DesktopMediaSource {
    public final androidx.media3.exoplayer.dash.manifest.DashManifest manifest;
    DashMediaSource(MediaItem i, DataSource.Factory d, androidx.media3.exoplayer.dash.manifest.DashManifest m) { super(i, d, "dash"); manifest = m; }
    public static final class Factory implements androidx.media3.exoplayer.source.MediaSource.Factory {
        private final DataSource.Factory d;
        public Factory(DataSource.Factory d) { this.d = d; }
        public Factory(Object chunkSourceFactory, DataSource.Factory d) { this.d = d; }
        @Override public Factory setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy p) { return this; }
        public Factory setManifestParser(Object p) { return this; }
        @Override public DashMediaSource createMediaSource(MediaItem item) { return new DashMediaSource(item, d, null); }
        public DashMediaSource createMediaSource(androidx.media3.exoplayer.dash.manifest.DashManifest m, MediaItem item) { return new DashMediaSource(item, d, m); }
        public DashMediaSource createMediaSource(androidx.media3.exoplayer.dash.manifest.DashManifest m) { return new DashMediaSource(MediaItem.fromUri("dash://manifest"), d, m); }
    }
}
