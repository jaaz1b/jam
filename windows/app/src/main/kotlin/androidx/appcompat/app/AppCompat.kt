package androidx.appcompat.app

open class AppCompatActivity : androidx.activity.ComponentActivity() {
    val delegate: AppCompatDelegate get() = AppCompatDelegate
}
/** The app's language (per-app locale): the resources follow it at once on the PC. */
object AppCompatDelegate {
    const val MODE_NIGHT_YES = 2; const val MODE_NIGHT_NO = 1; const val MODE_NIGHT_FOLLOW_SYSTEM = -1
    private var locales: androidx.core.os.LocaleListCompat = androidx.core.os.LocaleListCompat.getEmptyLocaleList()
    @JvmStatic fun setApplicationLocales(l: androidx.core.os.LocaleListCompat) {
        locales = l
        app.jam.desktop.shim.Res.locale = l.get(0) ?: java.util.Locale.getDefault()
        app.jam.desktop.shim.Desk.post { app.jam.desktop.shim.Windows.localeChanged() }
    }
    @JvmStatic fun getApplicationLocales(): androidx.core.os.LocaleListCompat = locales
    @JvmStatic fun setDefaultNightMode(m: Int) {}
    @JvmStatic fun getDefaultNightMode(): Int = MODE_NIGHT_YES
}
