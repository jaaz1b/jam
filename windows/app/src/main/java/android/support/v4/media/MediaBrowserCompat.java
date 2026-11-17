package android.support.v4.media;

public final class MediaBrowserCompat {
    public static class MediaItem implements android.os.Parcelable {
        public static final int FLAG_BROWSABLE = 1, FLAG_PLAYABLE = 2;
        private final MediaDescriptionCompat description; private final int flags;
        public MediaItem(MediaDescriptionCompat d, int flags) { description = d; this.flags = flags; }
        public MediaDescriptionCompat getDescription() { return description; } public String getMediaId() { return description.getMediaId(); } public int getFlags() { return flags; }
        public boolean isPlayable() { return (flags & FLAG_PLAYABLE) != 0; } public boolean isBrowsable() { return (flags & FLAG_BROWSABLE) != 0; }
    }
}
