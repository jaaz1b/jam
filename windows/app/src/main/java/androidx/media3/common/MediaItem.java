package androidx.media3.common;

public final class MediaItem {
    public static final String DEFAULT_MEDIA_ID = "";
    public static final MediaItem EMPTY = new Builder().build();
    public final String mediaId;
    public final LocalConfiguration localConfiguration;
    public final MediaMetadata mediaMetadata;
    public final ClippingConfiguration clippingConfiguration;
    public final RequestMetadata requestMetadata;
    public final LiveConfiguration liveConfiguration = new LiveConfiguration();
    private MediaItem(Builder b) {
        mediaId = b.mediaId; mediaMetadata = b.metadata; clippingConfiguration = new ClippingConfiguration(b.clipStart, b.clipEnd); requestMetadata = b.requestMetadata;
        localConfiguration = b.uri == null ? null : new LocalConfiguration(b.uri, b.mimeType, b.tag, b.customCacheKey);
    }
    public static MediaItem fromUri(String uri) { return new Builder().setUri(uri).build(); }
    public static MediaItem fromUri(android.net.Uri uri) { return new Builder().setUri(uri).build(); }
    public Builder buildUpon() { Builder b = new Builder(); b.mediaId = mediaId; b.metadata = mediaMetadata; b.clipStart = clippingConfiguration.startPositionMs; b.clipEnd = clippingConfiguration.endPositionMs; b.requestMetadata = requestMetadata;
        if (localConfiguration != null) { b.uri = localConfiguration.uri; b.mimeType = localConfiguration.mimeType; b.tag = localConfiguration.tag; b.customCacheKey = localConfiguration.customCacheKey; } return b; }
    @Override public boolean equals(Object o) { if (!(o instanceof MediaItem)) return false; MediaItem m = (MediaItem) o; return java.util.Objects.equals(mediaId, m.mediaId) && java.util.Objects.equals(localConfiguration == null ? null : localConfiguration.uri, m.localConfiguration == null ? null : m.localConfiguration.uri); }
    @Override public int hashCode() { return java.util.Objects.hash(mediaId, localConfiguration == null ? null : localConfiguration.uri); }
    public static final class Builder {
        String mediaId = DEFAULT_MEDIA_ID, mimeType, customCacheKey; android.net.Uri uri; Object tag; MediaMetadata metadata = MediaMetadata.EMPTY; long clipStart = 0, clipEnd = C.TIME_END_OF_SOURCE; RequestMetadata requestMetadata = RequestMetadata.EMPTY;
        public Builder setMediaId(String id) { mediaId = id; return this; }
        public Builder setUri(String u) { uri = u == null ? null : android.net.Uri.parse(u); return this; }
        public Builder setUri(android.net.Uri u) { uri = u; return this; }
        public Builder setMimeType(String m) { mimeType = m; return this; }
        public Builder setTag(Object t) { tag = t; return this; }
        public Builder setCustomCacheKey(String k) { customCacheKey = k; return this; }
        public Builder setMediaMetadata(MediaMetadata m) { metadata = m; return this; }
        public Builder setRequestMetadata(RequestMetadata r) { requestMetadata = r; return this; }
        public Builder setClippingConfiguration(ClippingConfiguration c) { clipStart = c.startPositionMs; clipEnd = c.endPositionMs; return this; }
        public Builder setLiveConfiguration(LiveConfiguration c) { return this; }
        public MediaItem build() { return new MediaItem(this); }
    }
    public static final class LocalConfiguration {
        public final android.net.Uri uri; public final String mimeType, customCacheKey; public final Object tag;
        LocalConfiguration(android.net.Uri u, String m, Object t, String k) { uri = u; mimeType = m; tag = t; customCacheKey = k; }
    }
    public static class ClippingConfiguration {
        public static final ClippingConfiguration UNSET = new ClippingConfiguration(0, C.TIME_END_OF_SOURCE);
        public final long startPositionMs, endPositionMs;
        ClippingConfiguration(long s, long e) { startPositionMs = s; endPositionMs = e; }
        public static final class Builder { long s = 0, e = C.TIME_END_OF_SOURCE;
            public Builder setStartPositionMs(long v) { s = v; return this; } public Builder setEndPositionMs(long v) { e = v; return this; }
            public ClippingConfiguration build() { return new ClippingConfiguration(s, e); } }
    }
    public static final class RequestMetadata {
        public static final RequestMetadata EMPTY = new Builder().build();
        public final android.net.Uri mediaUri; public final String searchQuery; public final android.os.Bundle extras;
        RequestMetadata(Builder b) { mediaUri = b.u; searchQuery = b.q; extras = b.e; }
        public static final class Builder { android.net.Uri u; String q; android.os.Bundle e;
            public Builder setMediaUri(android.net.Uri v) { u = v; return this; } public Builder setSearchQuery(String v) { q = v; return this; } public Builder setExtras(android.os.Bundle v) { e = v; return this; }
            public RequestMetadata build() { return new RequestMetadata(this); } }
    }
    public static final class LiveConfiguration { public static final class Builder { public Builder setTargetOffsetMs(long v) { return this; } public LiveConfiguration build() { return new LiveConfiguration(); } } }
}
