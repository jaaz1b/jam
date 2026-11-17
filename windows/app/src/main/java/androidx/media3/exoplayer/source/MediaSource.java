package androidx.media3.exoplayer.source;

import androidx.media3.common.MediaItem;

public interface MediaSource {
    MediaItem getMediaItem();
    interface Factory {
        MediaSource createMediaSource(MediaItem item);
        default Factory setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy p) { return this; }
        default Factory setDrmSessionManagerProvider(Object p) { return this; }
        default int[] getSupportedTypes() { return new int[] { androidx.media3.common.C.CONTENT_TYPE_OTHER, androidx.media3.common.C.CONTENT_TYPE_DASH, androidx.media3.common.C.CONTENT_TYPE_HLS }; }
    }
}
