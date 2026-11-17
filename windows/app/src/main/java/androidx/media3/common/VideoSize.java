package androidx.media3.common;

public final class VideoSize {
    public static final VideoSize UNKNOWN = new VideoSize(0, 0);
    public final int width, height, unappliedRotationDegrees; public final float pixelWidthHeightRatio;
    public VideoSize(int w, int h) { this(w, h, 1f); }
    public VideoSize(int w, int h, float r) { width = w; height = h; pixelWidthHeightRatio = r; unappliedRotationDegrees = 0; }
}
