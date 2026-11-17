package androidx.media;

import android.support.v4.media.MediaBrowserCompat;
import java.util.List;

/** The media browser service (Android Auto, other apps' browsing): on the PC only its Service side runs. */
public abstract class MediaBrowserServiceCompat extends android.app.Service {
    public static final String SERVICE_INTERFACE = "android.media.browse.MediaBrowserService";
    private android.support.v4.media.session.MediaSessionCompat.Token token;
    public abstract BrowserRoot onGetRoot(String clientPackageName, int clientUid, android.os.Bundle rootHints);
    public abstract void onLoadChildren(String parentId, Result<List<MediaBrowserCompat.MediaItem>> result);
    public void onLoadChildren(String parentId, Result<List<MediaBrowserCompat.MediaItem>> result, android.os.Bundle options) { onLoadChildren(parentId, result); }
    public void onSearch(String query, android.os.Bundle extras, Result<List<MediaBrowserCompat.MediaItem>> result) { result.sendResult(null); }
    public void onLoadItem(String itemId, Result<MediaBrowserCompat.MediaItem> result) { result.sendResult(null); }
    public void setSessionToken(android.support.v4.media.session.MediaSessionCompat.Token t) { token = t; }
    public android.support.v4.media.session.MediaSessionCompat.Token getSessionToken() { return token; }
    public void notifyChildrenChanged(String parentId) {}
    public void notifyChildrenChanged(String parentId, android.os.Bundle options) {}
    public final android.os.Bundle getBrowserRootHints() { return null; }
    public final RemoteUserInfo getCurrentBrowserInfo() { return new RemoteUserInfo("app.jam.android", 0, 10000); }
    public static final class RemoteUserInfo { private final String pkg; private final int pid, uid;
        public RemoteUserInfo(String pkg, int pid, int uid) { this.pkg = pkg; this.pid = pid; this.uid = uid; }
        public String getPackageName() { return pkg; } public int getPid() { return pid; } public int getUid() { return uid; } }
    public static final class BrowserRoot { public static final String EXTRA_RECENT = "android.service.media.extra.RECENT", EXTRA_OFFLINE = "android.service.media.extra.OFFLINE", EXTRA_SUGGESTED = "android.service.media.extra.SUGGESTED";
        private final String id; private final android.os.Bundle extras;
        public BrowserRoot(String id, android.os.Bundle extras) { this.id = id; this.extras = extras; } public String getRootId() { return id; } public android.os.Bundle getExtras() { return extras; } }
    public static class Result<T> {
        private boolean sent;
        public void sendResult(T result) { sent = true; }
        public void detach() {}
        public void sendError(android.os.Bundle e) { sent = true; }
        public boolean isDone() { return sent; }
    }
}
