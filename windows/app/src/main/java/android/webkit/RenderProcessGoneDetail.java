package android.webkit;

public class RenderProcessGoneDetail {
    private final boolean crashed;
    public RenderProcessGoneDetail(boolean crashed) { this.crashed = crashed; }
    public boolean didCrash() { return crashed; }
    public int rendererPriorityAtExit() { return WebView.RENDERER_PRIORITY_IMPORTANT; }
}
