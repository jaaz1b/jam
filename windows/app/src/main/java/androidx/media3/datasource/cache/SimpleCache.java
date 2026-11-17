package androidx.media3.datasource.cache;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * media3's SimpleCache, a simpler layout: one file per key holding the bytes cached so far from the
 * start, plus the known length. Enough for the app's uses (streams cached as they play, fully
 * downloaded ahead with CacheWriter), with LRU eviction by file time.
 */
public final class SimpleCache implements Cache {
    private final File dir; private final long max;
    public SimpleCache(File dir, CacheEvictor evictor, Object databaseProvider) { this.dir = dir; dir.mkdirs(); max = evictor instanceof LeastRecentlyUsedCacheEvictor ? ((LeastRecentlyUsedCacheEvictor) evictor).maxBytes : Long.MAX_VALUE; }
    public SimpleCache(File dir, CacheEvictor evictor) { this(dir, evictor, null); }
    private static String name(String key) { return Integer.toHexString(key.hashCode()) + "_" + Integer.toHexString(key.length()) + "_" + java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().substring(0, 8); }
    File data(String key) { return new File(dir, name(key) + ".bin"); }
    private File meta(String key) { return new File(dir, name(key) + ".meta"); }
    @Override public synchronized Set<String> getKeys() {
        Set<String> out = new HashSet<>(); File[] fs = dir.listFiles((d, n) -> n.endsWith(".meta")); if (fs == null) return out;
        for (File f : fs) try { String[] l = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n", 2); if (l.length == 2) out.add(l[1]); } catch (Exception ignored) {}
        return out;
    }
    @Override public synchronized void removeResource(String key) { data(key).delete(); meta(key).delete(); }
    @Override public synchronized long getCacheSpace() { long s = 0; File[] fs = dir.listFiles(); if (fs != null) for (File f : fs) s += f.length(); return s; }
    @Override public void release() {}
    public static void delete(File dir, Object db) { File[] fs = dir.listFiles(); if (fs != null) for (File f : fs) f.delete(); }
    synchronized long length(String key) { try { return Long.parseLong(new String(java.nio.file.Files.readAllBytes(meta(key).toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n", 2)[0].trim()); } catch (Exception e) { return -1; } }
    synchronized void setLength(String key, long len) { try { java.nio.file.Files.write(meta(key).toPath(), (len + "\n" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (Exception ignored) {} }
    @Override public synchronized boolean isCached(String key, long position, long length) { long have = data(key).length(); long len = length(key); return position + (length == -1 ? (len < 0 ? Long.MAX_VALUE : len - position) : length) <= have; }
    @Override public synchronized long getCachedBytes(String key, long position, long length) { long have = data(key).length(); return Math.max(0, Math.min(have, length == -1 ? have : position + length) - position); }
    @Override public ContentMetadata getContentMetadata(String key) {
        long len = length(key);
        return new ContentMetadata() { public long get(String k, long d) { return KEY_CONTENT_LENGTH.equals(k) && len >= 0 ? len : d; } public String get(String k, String d) { return d; } };
    }
    /** Appends [n] bytes at [position] when it extends the cached prefix exactly. */
    synchronized void append(String key, long position, byte[] b, int off, int n) {
        File f = data(key);
        if (f.length() != position) return;
        try (RandomAccessFile r = new RandomAccessFile(f, "rw")) { r.seek(position); r.write(b, off, n); } catch (Exception ignored) {}
        if (getCacheSpace() > max) evict();
    }
    private void evict() {
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".bin")); if (fs == null) return;
        Arrays.sort(fs, Comparator.comparingLong(File::lastModified));
        long space = getCacheSpace();
        for (File f : fs) { if (space <= max * 9 / 10) break; space -= f.length(); new File(dir, f.getName().replace(".bin", ".meta")).delete(); f.delete(); }
    }
}
