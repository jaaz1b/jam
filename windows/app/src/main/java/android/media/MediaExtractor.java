package android.media;

import app.jam.desktop.media.Decoded;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * MediaExtractor on the PC: ffmpeg decodes the file, and the "samples" read here are its PCM
 * (format audio/raw); the decoder made for audio/raw passes them through (MediaCodec).
 */
public final class MediaExtractor {
    public static final int SEEK_TO_PREVIOUS_SYNC = 0, SEEK_TO_NEXT_SYNC = 1, SEEK_TO_CLOSEST_SYNC = 2, SAMPLE_FLAG_SYNC = 1;
    private Decoded decoded;
    private int selected = -1;
    public void setDataSource(String path) throws IOException { decoded = Decoded.Companion.open(path, null); }
    public void setDataSource(String path, java.util.Map<String, String> headers) throws IOException { setDataSource(path); }
    public void setDataSource(java.io.FileDescriptor fd) throws IOException { throw new IOException("file descriptors are not read on the PC"); }
    public void setDataSource(MediaDataSource src) throws IOException { decoded = Decoded.Companion.open(null, src); }
    public void setDataSource(android.content.Context c, android.net.Uri uri, java.util.Map<String, String> h) throws IOException {
        java.io.File f = app.jam.desktop.shim.Files.INSTANCE.file(uri);
        setDataSource(f != null ? f.getAbsolutePath() : uri.toString());
    }
    public int getTrackCount() { return decoded == null ? 0 : 1; }
    public MediaFormat getTrackFormat(int i) { return decoded.format(); }
    public void selectTrack(int i) { selected = i; }
    public void unselectTrack(int i) { selected = -1; }
    public int readSampleData(ByteBuffer buf, int offset) { return decoded.read(buf, offset); }
    public long getSampleTime() { return decoded.sampleTimeUs(); }
    public int getSampleTrackIndex() { return decoded.atEnd() ? -1 : 0; }
    public int getSampleFlags() { return SAMPLE_FLAG_SYNC; }
    public long getCachedDuration() { return -1; }
    public boolean advance() { return !decoded.atEnd(); }
    public void seekTo(long timeUs, int mode) { decoded.seek(timeUs); }
    public void release() { if (decoded != null) decoded.close(); }
}
