package android.media;
public final class MediaMuxer {
    public static final class OutputFormat { public static final int MUXER_OUTPUT_MPEG_4 = 0, MUXER_OUTPUT_WEBM = 1, MUXER_OUTPUT_OGG = 4; }
    public MediaMuxer(String path, int format) throws java.io.IOException { throw new java.io.IOException("no muxer on the PC"); }
    public int addTrack(MediaFormat f) { return 0; }
    public void start() {}
    public void writeSampleData(int track, java.nio.ByteBuffer b, MediaCodec.BufferInfo i) {}
    public void stop() {}
    public void release() {}
}
