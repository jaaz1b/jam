package androidx.webkit;

import app.jam.desktop.web.Edge;
import java.util.List;

public class ProfileStore {
    private static final ProfileStore INSTANCE = new ProfileStore();
    public static ProfileStore getInstance() { return INSTANCE; }
    public Profile getOrCreateProfile(String name) { Edge.Companion.profileDir(name).mkdirs(); return new Profile(name); }
    public Profile getProfile(String name) { return Edge.Companion.profileDir(name).isDirectory() || Profile.DEFAULT.equals(name) ? new Profile(name) : null; }
    public List<String> getAllProfileNames() { return Edge.Companion.profiles(); }
    public boolean deleteProfile(String name) {
        if (Profile.DEFAULT.equals(name)) throw new IllegalStateException("the default profile cannot be deleted");
        Edge.Companion.stop(name);
        return kotlin.io.FilesKt.deleteRecursively(Edge.Companion.profileDir(name));
    }
}
