package androidx.media3.exoplayer.source;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;

/** A media item and where its bytes come from (the factory's DataSource), as the PC's engine plays it. */
public class DesktopMediaSource implements MediaSource {
    public final MediaItem item; public final DataSource.Factory data; public final MediaSource[] merged; public final String kind;
    public DesktopMediaSource(MediaItem item, DataSource.Factory data, String kind) { this(item, data, kind, null); }
    public DesktopMediaSource(MediaItem item, DataSource.Factory data, String kind, MediaSource[] merged) { this.item = item; this.data = data; this.kind = kind; this.merged = merged; }
    @Override public MediaItem getMediaItem() { return item; }
}
