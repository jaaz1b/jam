package androidx.media3.exoplayer.dash.manifest;

public class DashManifestParser {
    public DashManifest parse(android.net.Uri uri, java.io.InputStream in) throws java.io.IOException { return new DashManifest(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), uri); }
}
