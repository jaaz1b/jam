package androidx.webkit;
public class WebViewFeature {
    public static final String WEB_MESSAGE_LISTENER = "WEB_MESSAGE_LISTENER", DOCUMENT_START_SCRIPT = "DOCUMENT_START_SCRIPT", MULTI_PROFILE = "MULTI_PROFILE";
    public static final String REQUESTED_WITH_HEADER_ALLOW_LIST = "REQUESTED_WITH_HEADER_ALLOW_LIST", USER_AGENT_METADATA = "USER_AGENT_METADATA";
    public static final String ALGORITHMIC_DARKENING = "ALGORITHMIC_DARKENING", FORCE_DARK = "FORCE_DARK", SAFE_BROWSING_ENABLE = "SAFE_BROWSING_ENABLE";
    /** What the PC's web views do: message listeners, document-start scripts and profiles (each a browser profile). */
    public static boolean isFeatureSupported(String f) { return WEB_MESSAGE_LISTENER.equals(f) || DOCUMENT_START_SCRIPT.equals(f) || MULTI_PROFILE.equals(f); }
    public static boolean isStartupFeatureSupported(android.content.Context c, String f) { return false; }
}
