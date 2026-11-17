package androidx.media3.exoplayer.dash.manifest;

/** A parsed DASH manifest: kept as its XML and base URI, the engine reads the audio representation from it. */
public class DashManifest {
    public final String xml; public final android.net.Uri baseUri;
    public DashManifest(String xml, android.net.Uri baseUri) { this.xml = xml; this.baseUri = baseUri; }
    public int getPeriodCount() { return 1; }
}
