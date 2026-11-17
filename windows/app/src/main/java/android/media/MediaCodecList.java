package android.media;
public final class MediaCodecList {
    public static final int REGULAR_CODECS = 0, ALL_CODECS = 1;
    public MediaCodecList(int kind) {}
    public MediaCodecInfo[] getCodecInfos() { return new MediaCodecInfo[] { new MediaCodecInfo() }; }
    public String findDecoderForFormat(MediaFormat f) { return "jam.ffmpeg"; }
}
