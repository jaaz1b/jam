package android.webkit;

public class WebChromeClient {
    public boolean onConsoleMessage(ConsoleMessage message) { return false; }
    public void onPermissionRequest(PermissionRequest request) { request.deny(); }
    public void onPermissionRequestCanceled(PermissionRequest request) {}
    public void onProgressChanged(WebView view, int progress) {}
    public void onReceivedTitle(WebView view, String title) {}
    public void onReceivedIcon(WebView view, android.graphics.Bitmap icon) {}
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) { return false; }
    public void onCloseWindow(WebView window) {}
    public boolean onJsAlert(WebView view, String url, String message, Object result) { return false; }
    public void onShowCustomView(android.view.View view, Object callback) {}
    public void onHideCustomView() {}
    public boolean onShowFileChooser(WebView view, ValueCallback<android.net.Uri[]> cb, Object params) { return false; }
}
