package androidx.media3.common;

public final class MediaMetadata {
    public static final MediaMetadata EMPTY = new Builder().build();
    public final CharSequence title, artist, albumTitle, albumArtist, displayTitle, subtitle, description, genre, station;
    public final android.net.Uri artworkUri; public final byte[] artworkData; public final Integer trackNumber, releaseYear, totalTrackCount; public final Long durationMs;
    public final Boolean isPlayable, isBrowsable; public final android.os.Bundle extras; public final Integer mediaType;
    private MediaMetadata(Builder b) { title = b.title; artist = b.artist; albumTitle = b.albumTitle; albumArtist = b.albumArtist; displayTitle = b.displayTitle; subtitle = b.subtitle; description = b.description; genre = b.genre; station = b.station;
        artworkUri = b.artworkUri; artworkData = b.artworkData; trackNumber = b.trackNumber; releaseYear = b.releaseYear; totalTrackCount = b.totalTrackCount; durationMs = b.durationMs; isPlayable = b.isPlayable; isBrowsable = b.isBrowsable; extras = b.extras; mediaType = b.mediaType; }
    public Builder buildUpon() { Builder b = new Builder(); b.title = title; b.artist = artist; b.albumTitle = albumTitle; b.albumArtist = albumArtist; b.artworkUri = artworkUri; b.extras = extras; b.durationMs = durationMs; return b; }
    public static final class Builder {
        CharSequence title, artist, albumTitle, albumArtist, displayTitle, subtitle, description, genre, station; android.net.Uri artworkUri; byte[] artworkData; Integer trackNumber, releaseYear, totalTrackCount, mediaType; Long durationMs; Boolean isPlayable, isBrowsable; android.os.Bundle extras;
        public Builder setTitle(CharSequence v) { title = v; return this; }
        public Builder setArtist(CharSequence v) { artist = v; return this; }
        public Builder setAlbumTitle(CharSequence v) { albumTitle = v; return this; }
        public Builder setAlbumArtist(CharSequence v) { albumArtist = v; return this; }
        public Builder setDisplayTitle(CharSequence v) { displayTitle = v; return this; }
        public Builder setSubtitle(CharSequence v) { subtitle = v; return this; }
        public Builder setDescription(CharSequence v) { description = v; return this; }
        public Builder setGenre(CharSequence v) { genre = v; return this; }
        public Builder setStation(CharSequence v) { station = v; return this; }
        public Builder setArtworkUri(android.net.Uri v) { artworkUri = v; return this; }
        public Builder setArtworkData(byte[] v, Integer type) { artworkData = v; return this; }
        public Builder setTrackNumber(Integer v) { trackNumber = v; return this; }
        public Builder setTotalTrackCount(Integer v) { totalTrackCount = v; return this; }
        public Builder setReleaseYear(Integer v) { releaseYear = v; return this; }
        public Builder setDurationMs(Long v) { durationMs = v; return this; }
        public Builder setIsPlayable(Boolean v) { isPlayable = v; return this; }
        public Builder setIsBrowsable(Boolean v) { isBrowsable = v; return this; }
        public Builder setMediaType(Integer v) { mediaType = v; return this; }
        public Builder setExtras(android.os.Bundle v) { extras = v; return this; }
        public MediaMetadata build() { return new MediaMetadata(this); }
    }
    public static final int PICTURE_TYPE_FRONT_COVER = 3, MEDIA_TYPE_MUSIC = 1, MEDIA_TYPE_PODCAST_EPISODE = 7;
}
