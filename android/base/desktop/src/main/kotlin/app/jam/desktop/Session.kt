package app.jam.desktop

import app.myspoti.host.cdp.CdpHost
import app.myspoti.spotify.SpotifyApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the Spotify side is. */
sealed interface SessionState {
    data class Starting(val step: String) : SessionState
    /** Signed out: the Web Player's window is shown for the sign-in ([Session.signIn]). */
    data class SignedOut(val signingIn: Boolean) : SessionState
    data object Ready : SessionState
    data class Failed(val message: String) : SessionState
}

/**
 * Spotify mode's engine: the hidden browser ([Browser]), the Web Player in it driven over the
 * DevTools protocol ([CdpHost], from Orpheon), and Orpheon's Spotify API on top ([SpotifyApi]).
 */
object Session {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<SessionState>(SessionState.Starting("Opening Spotify"))
    val state: StateFlow<SessionState> = _state

    @Volatile var api: SpotifyApi? = null; private set
    private var browser: Browser? = null
    private var host: CdpHost? = null
    private var watch: Job? = null

    fun start() {
        scope.launch {
            try {
                val exe = Browser.find() ?: run {
                    _state.value = SessionState.Failed("Jam needs Microsoft Edge or Google Chrome to play Spotify. Install one, then open Jam again.")
                    return@launch
                }
                _state.value = SessionState.Starting("Starting the player")
                val b = Browser.start(exe).also { browser = it }
                _state.value = SessionState.Starting("Connecting to Spotify")
                val h = CdpHost.connect(b.cdpUrl).also { host = it }
                val a = SpotifyApi(h, scope, language = java.util.Locale.getDefault().language.ifBlank { "en" }).also { api = it }
                // Every page load (start, sign-in, a reload) needs the bridge injected again.
                scope.launch { while (true) { h.awaitLoad(); runCatching { a.onPageLoaded() } } }
                h.reload()
                withTimeoutOrNull(30_000) { a.awaitReady() }
                watchSignIn()
            } catch (t: Throwable) {
                _state.value = SessionState.Failed(t.message ?: t.toString())
            }
        }
    }

    /** Signed in or not, checked now and every few seconds while signed out. */
    private fun watchSignIn() {
        watch?.cancel()
        watch = scope.launch {
            var signingIn = false
            while (true) {
                val a = api ?: return@launch
                val inNow = runCatching { a.isSignedIn() }.getOrDefault(false)
                if (inNow) {
                    if (signingIn) runCatching { browser?.hide() }
                    withTimeoutOrNull(30_000) { a.awaitReady() }
                    _state.value = SessionState.Ready
                    return@launch
                }
                signingIn = (state.value as? SessionState.SignedOut)?.signingIn == true
                if (!signingIn) _state.value = SessionState.SignedOut(signingIn = false)
                delay(3_000)
            }
        }
    }

    /** Shows the Web Player's window for the Spotify sign-in; it hides again once signed in. */
    fun signIn() {
        _state.value = SessionState.SignedOut(signingIn = true)
        scope.launch { runCatching { browser?.show() } }
    }

    fun showPlayerWindow(show: Boolean) { scope.launch { runCatching { if (show) browser?.show() else browser?.hide() } } }

    suspend fun signOut() {
        runCatching { api?.forgetAccount() }
        runCatching { host?.reload() }
        delay(1_500)
        watchSignIn()
    }

    fun stop() {
        runCatching { host?.close() }
        runCatching { browser?.quit() }
    }
}
