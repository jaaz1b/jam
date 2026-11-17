package android.support.v4.media;

public final class MediaDescriptionCompat implements android.os.Parcelable {
    private final String mediaId; private final CharSequence title, subtitle, description; private final android.net.Uri iconUri, mediaUri; private final android.graphics.Bitmap icon; private final android.os.Bundle extras;
    private MediaDescriptionCompat(Builder b) { mediaId = b.id; title = b.t; subtitle = b.s; description = b.d; iconUri = b.iu; mediaUri = b.mu; icon = b.ib; extras = b.e; }
    public String getMediaId() { return mediaId; } public CharSequence getTitle() { return title; } public CharSequence getSubtitle() { return subtitle; } public CharSequence getDescription() { return description; }
    public android.net.Uri getIconUri() { return iconUri; } public android.net.Uri getMediaUri() { return mediaUri; } public android.graphics.Bitmap getIconBitmap() { return icon; } public android.os.Bundle getExtras() { return extras; }
    public static final class Builder { String id; CharSequence t, s, d; android.net.Uri iu, mu; android.graphics.Bitmap ib; android.os.Bundle e;
        public Builder setMediaId(String v) { id = v; return this; } public Builder setTitle(CharSequence v) { t = v; return this; } public Builder setSubtitle(CharSequence v) { s = v; return this; }
        public Builder setDescription(CharSequence v) { d = v; return this; } public Builder setIconUri(android.net.Uri v) { iu = v; return this; } public Builder setMediaUri(android.net.Uri v) { mu = v; return this; }
        public Builder setIconBitmap(android.graphics.Bitmap v) { ib = v; return this; } public Builder setExtras(android.os.Bundle v) { e = v; return this; }
        public MediaDescriptionCompat build() { return new MediaDescriptionCompat(this); } }
}
