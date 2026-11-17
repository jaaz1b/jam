package android.media;

/** Widevine, as the app asks MediaDrm about it: on the PC it is the browser's (Edge plays the protected audio). */
public final class MediaDrm implements AutoCloseable {
    public MediaDrm(java.util.UUID scheme) throws UnsupportedSchemeException {}
    public String getPropertyString(String key) {
        switch (key) {
            case "version": return "18.0.0@edge";
            case "securityLevel": return "L3";
            case "vendor": return "Google (in " + app.jam.desktop.web.Browsers.INSTANCE.version() + ")";
            case "hdcpLevel": case "maxHdcpLevel": return "Unknown";
            case "algorithms": return "AES/CBC/NoPadding,HmacSHA256";
            default: return "";
        }
    }
    public byte[] getPropertyByteArray(String key) { return new byte[0]; }
    public static boolean isCryptoSchemeSupported(java.util.UUID u) { return true; }
    @Override public void close() {}
    @Deprecated public void release() {}
    public static final class UnsupportedSchemeException extends Exception {}
}
