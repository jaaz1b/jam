package app.jam.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.system.Os
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Saved states for web apps that work offline (Octave): a frozen copy of everything the site keeps
 * in Jam (its pages, its offline worker, its downloaded tracks, its login), which Jam opens with no
 * network at all, so the live site being down, broken or changed doesn't matter.
 *
 * A source's live site runs in WebView profile `src_<id>`; its saved state is profile `saved_<id>`.
 * Saving copies the first profile's folder over the second. Chromium keeps those folders open while
 * a profile is in use, so the copy happens at the start of a fresh process, before anything opens
 * either one: Save marks it pending and restarts Jam ([RestartActivity]).
 *
 * Large files that Chromium never changes once written (database tables, stored blobs such as the
 * downloaded tracks, cached responses) are hard-linked rather than copied: the saved state costs no
 * extra space for them, and deleting a download on the live site doesn't take it out of the save.
 */
object SavedState {
    private const val TAG = "JamSavedState"
    private val main = Handler(Looper.getMainLooper())

    fun liveProfile(id: String) = "src_$id"
    fun savedProfile(id: String) = "saved_$id"

    /** The source opens its saved state instead of the live site. */
    fun enabled(id: String) = Prefs.secret("savedMode.$id") == "1" && exists(id)
    fun setEnabled(id: String, on: Boolean) = Prefs.setSecretNow("savedMode.$id", if (on) "1" else null)
    /** When the saved state was made (ms), or 0. */
    fun savedAt(id: String) = Prefs.secret("savedAt.$id")?.toLongOrNull() ?: 0L
    fun exists(id: String) = savedAt(id) > 0
    fun size(id: String) = Prefs.secret("savedSize.$id")?.toLongOrNull() ?: 0L
    fun lastError(id: String) = Prefs.secret("savedError.$id")

    // ------------------------------------------------------------------ the copy, at startup

    @Volatile var busy: String? = null
        private set
    private val waiting = mutableListOf<() -> Unit>()

    /** Run [then] on the main thread once no save is being applied. */
    fun whenReady(then: () -> Unit) {
        synchronized(waiting) { if (busy != null) { waiting.add(then); return } }
        then()
    }

    /** From [JamApp]: finish a save or a delete asked for before the restart. Off the main thread. */
    fun applyPending(context: Context) {
        val save = Prefs.secret("savedPending")
        val delete = Prefs.secret("savedDeletePending")
        if (save == null && delete == null) return
        busy = save ?: delete
        Thread({
            try {
                if (delete != null) {
                    Prefs.setSecretNow("savedDeletePending", null)
                    profileDir(context, savedProfile(delete))?.let { clear(it) }
                    Prefs.setSecretNow("savedAt.$delete", null)
                    Prefs.setSecretNow("savedSize.$delete", null)
                    Prefs.setSecretNow("savedMode.$delete", null)
                }
                if (save != null) {
                    Prefs.setSecretNow("savedPending", null)
                    copy(context, save)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "saved state failed", e)
                if (save != null) Prefs.setSecretNow("savedError.$save", e.message ?: e.toString())
            } finally {
                synchronized(waiting) { busy = null }
                val run = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
                main.post { run.forEach { it() } }
            }
        }, "jam-saved-state").start()
    }

    private fun copy(context: Context, id: String) {
        val from = profileDir(context, liveProfile(id)) ?: error("Couldn't find the live site's data. Open it once with internet first.")
        val to = profileDir(context, savedProfile(id)) ?: error("Couldn't prepare the saved state. Try again.")
        if (from.canonicalPath == to.canonicalPath) error("The saved state and the live site share a folder.")
        val t0 = System.currentTimeMillis()
        clear(to)
        val stats = LongArray(3) // bytes, linked bytes, files
        copyTree(from, to, stats)
        Prefs.setSecretNow("savedAt.$id", System.currentTimeMillis().toString())
        Prefs.setSecretNow("savedSize.$id", stats[0].toString())
        Prefs.setSecretNow("savedError.$id", null)
        Prefs.setSecretNow("savedMode.$id", "1")
        Log.i(TAG, "saved $id: ${stats[2]} files, ${stats[0]} bytes (${stats[1]} linked) in ${System.currentTimeMillis() - t0} ms")
    }

    private fun clear(dir: File) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        dir.mkdirs()
    }

    private fun copyTree(from: File, to: File, stats: LongArray) {
        to.mkdirs()
        for (f in from.listFiles() ?: return) {
            val dest = File(to, f.name)
            if (f.isDirectory) { copyTree(f, dest, stats); continue }
            val len = f.length()
            stats[0] += len; stats[2]++
            if (len >= 256 * 1024 && immutable(f)) {
                try { Os.link(f.path, dest.path); stats[1] += len; continue } catch (e: Exception) { /* copy below */ }
            }
            f.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
        }
    }

    /** Files Chromium writes once and never changes in place: safe to share between the two. */
    private fun immutable(f: File): Boolean {
        val n = f.name
        val p = f.path
        return n.endsWith(".ldb") || n.endsWith(".sst") ||
            p.contains(".blob/") ||
            (p.contains("/CacheStorage/") && Regex("^[0-9a-f]{16}_[0-9s]$").matches(n))
    }

    /**
     * A profile's folder. WebView names them "Profile 1", "Profile 2" … and keeps the name → folder
     * map in its local-state file, which is read here rather than asking WebView (that would open
     * the profile).
     */
    fun profileDir(context: Context, name: String): File? {
        val roots = context.dataDir.listFiles { f -> f.isDirectory && f.name.startsWith("app_webview") } ?: return null
        for (root in roots) {
            for (f in candidates(root)) {
                val text = try { f.readText() } catch (e: Exception) { continue }
                if (!text.contains("\"$name\"")) continue
                val list = try { JSONObject(text).optJSONObject("profile")?.optJSONArray("list") } catch (e: Exception) { null } ?: continue
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    if (o.optString("name") == name) {
                        val rel = o.optString("path")
                        if (rel.isEmpty() || rel.contains('/') || rel == "." || rel == "..") continue
                        return File(root, rel)
                    }
                }
            }
        }
        return null
    }

    private fun candidates(root: File): List<File> {
        val out = mutableListOf<File>()
        fun walk(d: File, depth: Int) {
            for (f in d.listFiles() ?: return) {
                if (f.isFile && f.length() in 2..4_000_000) out.add(f)
                else if (f.isDirectory && depth < 1 && !f.name.startsWith("Profile ") && f.name != "Default") walk(f, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    // ------------------------------------------------------------------ asking for one

    /**
     * Prepare a save of [id] and restart Jam to make it. The saved profile has to be known to WebView
     * on disk first (it writes its local state a few seconds after a change), so this creates it if
     * needed and waits until it shows up. [done] gets an error message, or null as Jam restarts.
     */
    fun saveAndRestart(activity: Activity, id: String, done: (String?) -> Unit) {
        val store = try { androidx.webkit.ProfileStore.getInstance() } catch (e: Exception) { done("This phone's WebView can't keep a saved state."); return }
        store.getOrCreateProfile(liveProfile(id))
        store.getOrCreateProfile(savedProfile(id))
        Thread({
            var ready = false
            for (i in 0 until 60) {
                if (profileDir(activity, liveProfile(id)) != null && profileDir(activity, savedProfile(id)) != null) { ready = true; break }
                Thread.sleep(500)
            }
            main.post {
                if (!ready) { done("Jam couldn't find the site's data. Open it with internet, then try again."); return@post }
                Prefs.setSecretNow("savedPending", id)
                done(null)
                restart(activity)
            }
        }, "jam-saved-prepare").start()
    }

    fun deleteAndRestart(activity: Activity, id: String) {
        Prefs.setSecretNow("savedDeletePending", id)
        Prefs.setSecretNow("savedMode.$id", null)
        restart(activity)
    }

    /** Switching between the live site and the saved state needs no restart: a new page is made. */
    fun restart(activity: Activity) {
        val i = Intent(activity, RestartActivity::class.java)
            .putExtra(RestartActivity.PID, Process.myPid())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(i)
        activity.finishAffinity()
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 300)
    }
}
