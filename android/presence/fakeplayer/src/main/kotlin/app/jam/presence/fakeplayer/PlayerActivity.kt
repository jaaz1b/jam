package app.jam.presence.fakeplayer

import android.app.Activity
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView

/** A media session "playing" a real song (extras: title, artist, album), as Apple Music would show it. */
class PlayerActivity : Activity() {
    private var session: MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: "Espresso"
        val artist = intent.getStringExtra("artist") ?: "Sabrina Carpenter"
        val album = intent.getStringExtra("album") ?: "Short n' Sweet"
        setContentView(TextView(this).apply { text = "Test player: $title · $artist"; textSize = 20f; setPadding(48, 200, 48, 48) })
        session = MediaSession(this, "test").apply {
            setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 175_000L)
                .build())
            setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 61_000L, 1f, SystemClock.elapsedRealtime())
                .build())
            isActive = true
        }
    }

    override fun onDestroy() {
        session?.release()
        super.onDestroy()
    }
}
