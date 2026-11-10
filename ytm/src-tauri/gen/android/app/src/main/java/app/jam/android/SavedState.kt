package app.jam.android

import android.util.Log

/**
 * Saved states for web apps that work offline (Octave): the site's pages, frozen as they were when
 * saved, opened with no network at all, so the live site being down, broken or changed doesn't
 * matter.
 *
 * Only the pages are saved. installed-app.js copies the site's own offline caches (pages, page
 * data, scripts, images: a few MB) into a database in the site's own storage; the downloaded songs
 * are never copied. The saved state runs in the live site's own WebView profile with the network
 * blocked, so it plays the downloads from where the site keeps them, and installed-app.js writes the
 * saved pages back over the site's caches when it opens.
 *
 * (Jam 2.2 copied the whole profile, downloads included, which doubled the space they took. Its
 * copy, profile `saved_<id>`, is deleted on the first start of this version: [cleanUp].)
 */
object SavedState {
    private const val TAG = "JamSavedState"

    /** The source opens its saved state instead of the live site. */
    fun enabled(id: String) = Prefs.secret("savedMode.$id") == "1" && exists(id)
    fun setEnabled(id: String, on: Boolean) = Prefs.setSecretNow("savedMode.$id", if (on) "1" else null)
    /** When the saved state was made (ms), or 0. */
    fun savedAt(id: String) = Prefs.secret("savedAt.$id")?.toLongOrNull() ?: 0L
    fun exists(id: String) = savedAt(id) > 0
    /** Bytes of saved pages (not songs), or 0 when unknown. */
    fun size(id: String) = Prefs.secret("savedSize.$id")?.toLongOrNull() ?: 0L
    fun pages(id: String) = Prefs.secret("savedCount.$id")?.toIntOrNull() ?: 0
    fun lastError(id: String) = Prefs.secret("savedError.$id")

    fun recordSave(id: String, count: Int, bytes: Long) {
        Prefs.setSecretNow("savedAt.$id", System.currentTimeMillis().toString())
        Prefs.setSecretNow("savedSize.$id", bytes.toString())
        Prefs.setSecretNow("savedCount.$id", count.toString())
        Prefs.setSecretNow("savedError.$id", null)
        Prefs.setSecretNow("savedMode.$id", "1")
    }

    fun recordError(id: String, error: String) = Prefs.setSecretNow("savedError.$id", error.take(200))

    fun forget(id: String) {
        for (k in listOf("savedAt", "savedSize", "savedCount", "savedError", "savedMode")) Prefs.setSecretNow("$k.$id", null)
    }

    /**
     * Before any page opens: delete Jam 2.2's whole-profile copies (`saved_<id>`), which held a
     * second copy of every downloaded song. A save made with 2.2 keeps working: its pages are the
     * live site's own, and Save again freezes them the new way.
     */
    fun cleanUp() {
        Prefs.setSecretNow("savedPending", null)
        Prefs.setSecretNow("savedDeletePending", null)
        Prefs.setSecretNow("savedJustMade", null)
        try {
            val store = androidx.webkit.ProfileStore.getInstance()
            for (name in store.allProfileNames) {
                if (!name.startsWith("saved_")) continue
                val ok = runCatching { store.deleteProfile(name) }.getOrDefault(false)
                Log.i(TAG, "deleted the old full copy $name: $ok")
            }
        } catch (e: Exception) {
            Log.w(TAG, "clean-up", e)
        }
    }
}
