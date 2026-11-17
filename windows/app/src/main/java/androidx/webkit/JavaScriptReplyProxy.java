package androidx.webkit;
public abstract class JavaScriptReplyProxy {
    public abstract void postMessage(String message);
    public static final JavaScriptReplyProxy NONE = new JavaScriptReplyProxy() { public void postMessage(String m) {} };
}
