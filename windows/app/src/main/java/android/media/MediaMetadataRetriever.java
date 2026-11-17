package android.media;
public class MediaMetadataRetriever implements AutoCloseable {
    public static final int METADATA_KEY_DURATION = 9, METADATA_KEY_TITLE = 7, METADATA_KEY_ARTIST = 2, METADATA_KEY_ALBUM = 1, METADATA_KEY_MIMETYPE = 12, METADATA_KEY_BITRATE = 20, METADATA_KEY_SAMPLERATE = 38;
    private java.util.Map<Integer, String> meta = java.util.Collections.emptyMap();
    public void setDataSource(String path) { meta = app.jam.desktop.media.Decoded.Companion.probe(path); }
    public void setDataSource(android.content.Context c, android.net.Uri uri) { java.io.File f = app.jam.desktop.shim.Files.INSTANCE.file(uri); setDataSource(f != null ? f.getAbsolutePath() : uri.toString()); }
    public void setDataSource(java.io.FileDescriptor fd) {}
    public String extractMetadata(int key) { return meta.get(key); }
    public byte[] getEmbeddedPicture() { return null; }
    public void release() {}
    @Override public void close() {}
}
