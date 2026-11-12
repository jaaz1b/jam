package com.music.spotui.di

import app.jam.JamStartup
import android.app.Application
import app.myspoti.api.account.AccountProviders
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.data.api.Api
import com.music.spotui.ui.repository.AppRepository

/** Manual replacement for Spotui's Hilt graph: app-wide singletons. */
object Graph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
        // The data saver first: on a metered network with it on, nothing may go out, not even the page.
        JamStartup.step("Graph.data.DataSaver.init") { com.music.spotui.data.DataSaver.init(application) }
        // The Spotify session: hidden Web Player + API (operation hashes refresh themselves). The page
        // loads now only with Spotify as the music account; else at its first use (an episode, a
        // Spotify page opened), and never while the data saver holds the network.
        val saver = com.music.spotui.data.DataSaver.active
        JamStartup.step("Graph.MyspotiRuntime.start") { MyspotiRuntime.start(application, loadPage = !saver && com.music.spotui.data.preferences.AccountProviderPref.id == AccountProviders.SPOTIFY, held = saver) }
        // Metadata (searches, albums, artists) from the providers in the user's order, Spotify among them when signed in.
        JamStartup.step("Graph.data.catalog.CatalogRuntime.install") { com.music.spotui.data.catalog.CatalogRuntime.install() }
        // The account provider other than Spotify the user chose (YouTube Music): its hidden page, once signed in.
        JamStartup.step("Graph.di.YtmRuntime.startIfChosen") { com.music.spotui.di.YtmRuntime.startIfChosen() }
        // The streaming quality the user chose, set on the player at every page load.
        JamStartup.step("Graph.data.preferences.applyStreamingQuality") { com.music.spotui.data.preferences.applyStreamingQuality(application) }
        // The crossfade length (0 = off), in the player from the first song on.
        JamStartup.step("Graph.data.preferences.applyCrossfade") { com.music.spotui.data.preferences.applyCrossfade(com.music.spotui.data.preferences.getCrossfadeSeconds(application)) }
        JamStartup.step("Graph.data.local.ListenHistory.init") { com.music.spotui.data.local.ListenHistory.init(application) }
        // Downloaded songs (private storage), and the downloads left unfinished last time.
        // Supporter thank-yous (accent colour) before any screen draws.
        JamStartup.step("Graph.data.api.SupportReminder.init") { com.music.spotui.data.api.SupportReminder.init(application) }
        JamStartup.step("Graph.data.offline.OfflineStore.init") { com.music.spotui.data.offline.OfflineStore.init(application) }
        JamStartup.step("Graph.data.offline.OfflineCollections.init") { com.music.spotui.data.offline.OfflineCollections.init(application) }
        JamStartup.step("Graph.SongPlayer.startLibraryOutbox") { SongPlayer.startLibraryOutbox(application) }
        JamStartup.step("Graph.data.offline.OfflineCollections.refreshAll") { com.music.spotui.data.offline.OfflineCollections.refreshAll(application) }
        JamStartup.step("Graph.data.local.LocalPlaylists.init") { com.music.spotui.data.local.LocalPlaylists.init(application) }
        JamStartup.step("Graph.data.local.LibraryOrder.init") { com.music.spotui.data.local.LibraryOrder.init(application) }
        runCatching { com.music.spotui.data.DataSaverGlide.install(application) }
        JamStartup.step("Graph.data.offline.OfflineDownloader.resumePending") { com.music.spotui.data.offline.OfflineDownloader.resumePending() }
        // Episodes have their own queue and their own file: resumed the same way.
        JamStartup.step("Graph.data.offline.PodcastDownloader.resumePending") { com.music.spotui.data.offline.PodcastDownloader.resumePending() }
        // Installed extensions: each enabled one loads in its own sandbox, off the main thread.
        JamStartup.step("Graph.extensions.Extensions.init") { com.music.spotui.extensions.Extensions.init(application) }
        // Live Radio: its own setting and favorites file (see com.music.spotui.radio).
        JamStartup.step("Graph.radio.RadioFeaturePref.init") { com.music.spotui.radio.RadioFeaturePref.init(application) }
        JamStartup.step("Graph.data.preferences.LocalFilesPref.init") { com.music.spotui.data.preferences.LocalFilesPref.init(application) }
        // A watch linked before reaches this phone over the Wi-Fi too, while the app runs.
        JamStartup.step("Graph.wear.WatchLan.startIfLinked") { com.music.spotui.wear.WatchLan.startIfLinked(application) }
        JamStartup.step("Graph.radio.RadioFavoritesStore.init") { com.music.spotui.radio.RadioFavoritesStore.init(application) }
        // SponsorBlock for podcasts: idle until turned on in the settings.
        JamStartup.step("Graph.podcast.SponsorSkip.init") { com.music.spotui.podcast.SponsorSkip.init() }
    }

    val api: Api by lazy { Api(app) }
    val repository: AppRepository by lazy { AppRepository(api) }
    val currentSongState: CurrentSongState by lazy { CurrentSongState() }
}
