package android.content;

/** A content provider: on the PC nothing outside the app asks it (Android Auto's artwork). */
public abstract class ContentProvider {
    public abstract boolean onCreate();
    public Context getContext() { return app.jam.desktop.shim.DesktopApp.INSTANCE.getContext(); }
    public abstract android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] args, String sort);
    public abstract String getType(android.net.Uri uri);
    public abstract android.net.Uri insert(android.net.Uri uri, ContentValues values);
    public abstract int delete(android.net.Uri uri, String selection, String[] args);
    public abstract int update(android.net.Uri uri, ContentValues values, String selection, String[] args);
    public android.os.ParcelFileDescriptor openFile(android.net.Uri uri, String mode) throws java.io.FileNotFoundException { throw new java.io.FileNotFoundException(String.valueOf(uri)); }
}
