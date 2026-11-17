package androidx.media3.exoplayer.source;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;

public final class ProgressiveMediaSource extends DesktopMediaSource {
    ProgressiveMediaSource(MediaItem i, DataSource.Factory d) { super(i, d, "progressive"); }
    public static final class Factory implements MediaSource.Factory {
        private final DataSource.Factory d;
        public Factory(DataSource.Factory d) { this.d = d; }
        public Factory(DataSource.Factory d, Object extractors) { this.d = d; }
        @Override public Factory setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy p) { return this; }
        public Factory setContinueLoadingCheckIntervalBytes(int b) { return this; }
        @Override public ProgressiveMediaSource createMediaSource(MediaItem item) { return new ProgressiveMediaSource(item, d); }
    }
}
