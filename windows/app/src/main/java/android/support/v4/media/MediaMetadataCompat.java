package android.support.v4.media;

public final class MediaMetadataCompat implements android.os.Parcelable {
    public static final String METADATA_KEY_TITLE = "android.media.metadata.TITLE", METADATA_KEY_ARTIST = "android.media.metadata.ARTIST", METADATA_KEY_ALBUM = "android.media.metadata.ALBUM", METADATA_KEY_DURATION = "android.media.metadata.DURATION";
    public static final String METADATA_KEY_ALBUM_ART = "android.media.metadata.ALBUM_ART", METADATA_KEY_ART = "android.media.metadata.ART", METADATA_KEY_ALBUM_ART_URI = "android.media.metadata.ALBUM_ART_URI", METADATA_KEY_ART_URI = "android.media.metadata.ART_URI";
    public static final String METADATA_KEY_MEDIA_ID = "android.media.metadata.MEDIA_ID", METADATA_KEY_DISPLAY_TITLE = "android.media.metadata.DISPLAY_TITLE", METADATA_KEY_DISPLAY_SUBTITLE = "android.media.metadata.DISPLAY_SUBTITLE", METADATA_KEY_DISPLAY_ICON_URI = "android.media.metadata.DISPLAY_ICON_URI", METADATA_KEY_ALBUM_ARTIST = "android.media.metadata.ALBUM_ARTIST", METADATA_KEY_MEDIA_URI = "android.media.metadata.MEDIA_URI";
    private final java.util.HashMap<String, Object> map;
    private MediaMetadataCompat(java.util.HashMap<String, Object> m) { map = m; }
    public String getString(String k) { Object v = map.get(k); return v == null ? null : v.toString(); }
    public CharSequence getText(String k) { return (CharSequence) map.get(k); }
    public long getLong(String k) { Object v = map.get(k); return v instanceof Long ? (Long) v : 0; }
    public android.graphics.Bitmap getBitmap(String k) { return (android.graphics.Bitmap) map.get(k); }
    public boolean containsKey(String k) { return map.containsKey(k); }
    public MediaDescriptionCompat getDescription() { return new MediaDescriptionCompat.Builder().setMediaId(getString(METADATA_KEY_MEDIA_ID)).setTitle(getText(METADATA_KEY_TITLE)).setSubtitle(getText(METADATA_KEY_ARTIST)).build(); }
    public static final class Builder { private final java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        public Builder() {} public Builder(MediaMetadataCompat src) { m.putAll(src.map); }
        public Builder putString(String k, String v) { m.put(k, v); return this; } public Builder putText(String k, CharSequence v) { m.put(k, v); return this; }
        public Builder putLong(String k, long v) { m.put(k, v); return this; } public Builder putBitmap(String k, android.graphics.Bitmap v) { m.put(k, v); return this; } public Builder putRating(String k, Object r) { return this; }
        public MediaMetadataCompat build() { return new MediaMetadataCompat(new java.util.HashMap<>(m)); } }
}
