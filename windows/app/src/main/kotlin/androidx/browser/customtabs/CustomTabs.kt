package androidx.browser.customtabs

/**
 * A Custom Tab: on the PC, a page in Jam's own window (app.jam.desktop.WebSheet). A redirect to the
 * app's own scheme (Discord's sign-in, jamdiscord://…) comes back to the app as on Android.
 */
class CustomTabsIntent private constructor() {
    fun launchUrl(context: android.content.Context, url: android.net.Uri) = app.jam.desktop.WebSheet.open(url.toString())
    val intent: android.content.Intent = android.content.Intent()
    class Builder {
        fun setShowTitle(b: Boolean) = this
        fun setUrlBarHidingEnabled(b: Boolean) = this
        fun setShareState(s: Int) = this
        fun setDefaultColorSchemeParams(p: CustomTabColorSchemeParams) = this
        fun setColorScheme(s: Int) = this
        fun setStartAnimations(c: android.content.Context, a: Int, b: Int) = this
        fun setExitAnimations(c: android.content.Context, a: Int, b: Int) = this
        fun setInstantAppsEnabled(b: Boolean) = this
        fun setBookmarksButtonEnabled(b: Boolean) = this
        fun setDownloadButtonEnabled(b: Boolean) = this
        fun build() = CustomTabsIntent()
    }
    companion object { const val SHARE_STATE_OFF = 2; const val COLOR_SCHEME_DARK = 2 }
}
class CustomTabColorSchemeParams private constructor() { class Builder { fun setToolbarColor(c: Int) = this; fun setNavigationBarColor(c: Int) = this; fun build() = CustomTabColorSchemeParams() } }
