package android.webkit;

import android.graphics.Bitmap;

public class WebViewClient {
    public static final int ERROR_UNKNOWN = -1, ERROR_HOST_LOOKUP = -2, ERROR_CONNECT = -6, ERROR_TIMEOUT = -8, ERROR_FAILED_SSL_HANDSHAKE = -11;
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return shouldOverrideUrlLoading(view, request.getUrl().toString()); }
    @Deprecated public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return null; }
    public void onPageStarted(WebView view, String url, Bitmap favicon) {}
    public void onPageFinished(WebView view, String url) {}
    public void onPageCommitVisible(WebView view, String url) {}
    public void onLoadResource(WebView view, String url) {}
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {}
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {}
    public void onReceivedSslError(WebView view, Object handler, Object error) {}
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { return false; }
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {}
}
