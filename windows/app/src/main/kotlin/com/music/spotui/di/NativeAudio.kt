// Jam for Windows: replaces the Android file of the same name. Spotify's audio engine needs the phone's
// Widevine (MediaDrm) to play the account's songs; on the PC the Web Player in Edge plays them.
package com.music.spotui.di

import android.content.Context
import android.content.Intent
import app.myspoti.engine.NativeEngine

object NativeAudio : app.myspoti.spotify.AccountAudio, app.myspoti.spotify.sync.MirrorEngine {
    val enabled: Boolean get() = false
    override val on: Boolean get() = false
    override val deviceId: String? get() = null
    override val current: kotlinx.coroutines.flow.StateFlow<NativeEngine?> = kotlinx.coroutines.flow.MutableStateFlow(null)
    override suspend fun ready(): NativeEngine = throw IllegalStateException("no native engine on the PC")
    override suspend fun play(contextUri: String, trackUri: String?, index: Int?, positionMs: Long?, order: List<String>?, shuffle: Boolean?) {}
    override suspend fun pause() {}
    override suspend fun resume() {}
    override suspend fun seek(positionMs: Long) {}
    override suspend fun next() {}
    override suspend fun previous() {}
    fun leaveForMirror() {}
    fun set(context: Context, value: Boolean) {}
    fun attach() {}
    fun command(context: Context, cmd: String, intent: Intent): String = "no native engine on the PC"
}
