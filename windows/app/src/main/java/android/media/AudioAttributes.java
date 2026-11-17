package android.media;
public final class AudioAttributes {
    public static final int USAGE_MEDIA = 1, USAGE_UNKNOWN = 0, USAGE_GAME = 14, CONTENT_TYPE_MUSIC = 2, CONTENT_TYPE_SPEECH = 1, CONTENT_TYPE_MOVIE = 3, CONTENT_TYPE_UNKNOWN = 0;
    public static final class Builder { public Builder setUsage(int u) { return this; } public Builder setContentType(int c) { return this; } public Builder setFlags(int f) { return this; } public Builder setLegacyStreamType(int s) { return this; } public AudioAttributes build() { return new AudioAttributes(); } }
}
