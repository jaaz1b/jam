package app.jam.desktop

import app.myspoti.api.PlayerState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SocketChannel

/**
 * Discord Rich Presence through the Discord app on this PC (its local IPC: the named pipe
 * \\.\pipe\discord-ipc-N on Windows), as Jam's Windows app does it: "Listening to" the song, its
 * cover (a web address Discord loads), the time bar, a "Listen on Spotify" button. Needs a Discord
 * application ID (Settings), the same one Jam's other Windows app uses.
 */
object Discord {
    private var pipe: Pipe? = null
    private var connectedId: String? = null
    private var lastKey: String? = null
    @Volatile var status: String = "off"; private set

    private interface Pipe { fun write(b: ByteArray); fun read(b: ByteArray): Int; fun close() }

    private fun open(): Pipe? {
        if (Paths.isWindows) {
            for (i in 0..9) runCatching {
                val f = RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw")
                return object : Pipe {
                    override fun write(b: ByteArray) = f.write(b)
                    override fun read(b: ByteArray) = f.read(b)
                    override fun close() = f.close()
                }
            }
            return null
        }
        val base = System.getenv("XDG_RUNTIME_DIR") ?: System.getenv("TMPDIR") ?: "/tmp"
        for (dir in listOf(base, "$base/app/com.discordapp.Discord", "$base/snap.discord")) for (i in 0..9) {
            val f = File(dir, "discord-ipc-$i")
            if (!f.exists()) continue
            runCatching {
                val ch = SocketChannel.open(StandardProtocolFamily.UNIX).apply { connect(UnixDomainSocketAddress.of(f.toPath())) }
                val input = Channels.newInputStream(ch); val output = Channels.newOutputStream(ch)
                return object : Pipe {
                    override fun write(b: ByteArray) { output.write(b); output.flush() }
                    override fun read(b: ByteArray) = input.read(b)
                    override fun close() = ch.close()
                }
            }
        }
        return null
    }

    private fun frame(op: Int, payload: String): ByteArray {
        val data = payload.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(data.size).put(data).array()
    }

    @Synchronized private fun ensure(): Pipe? {
        val id = Settings.discordClientId
        if (!Settings.discordEnabled) { close(); status = "off"; return null }
        if (id.isBlank()) { close(); status = "no application ID"; return null }
        if (pipe != null && connectedId == id) return pipe
        close()
        val p = open() ?: run { status = "Discord isn't running"; return null }
        runCatching {
            p.write(frame(0, buildJsonObject { put("v", 1); put("client_id", id) }.toString()))
            // Discord answers and keeps writing: read it all, on the side, so the pipe never fills.
            Thread({ val buf = ByteArray(16 * 1024); runCatching { while (p.read(buf) >= 0) Unit } }, "discord-read").apply { isDaemon = true; start() }
        }.onFailure { runCatching { p.close() }; status = "Discord isn't running"; return null }
        pipe = p; connectedId = id; status = "connected"; lastKey = null
        return p
    }

    @Synchronized private fun close() {
        runCatching { pipe?.close() }; pipe = null; connectedId = null
    }

    /** What plays now (null: nothing). Called on each player change; repeats are skipped. */
    @Synchronized fun update(s: PlayerState?) {
        val item = s?.let { st -> st.item?.takeIf { !st.isPaused || Settings.showPaused } }
        val now = System.currentTimeMillis()
        val pos = s?.positionAt(now) ?: 0
        val paused = s?.isPaused == true
        val key = if (item == null) "none" else "${item.uri}|$paused|${(now - pos) / 2000}"
        if (key == lastKey && pipe != null) return
        val p = ensure() ?: return
        val activity: JsonObject? = if (item == null) null else buildJsonObject {
            put("type", 2) // Listening to
            put("details", item.name.take(128))
            put("state", item.artists.joinToString(", ") { it.name }.ifBlank { "Spotify" }.take(128))
            if (!paused && item.durationMs > 0) putJsonObject("timestamps") {
                put("start", now - pos); put("end", now - pos + item.durationMs)
            }
            putJsonObject("assets") {
                item.imageUrl?.takeIf { it.startsWith("https://") }?.let { put("large_image", it) }
                item.album?.name?.let { put("large_text", it.take(128)) }
                if (paused) put("small_text", "Paused")
            }
            val id = item.uri.split(':').getOrNull(2)
            if (item.uri.startsWith("spotify:track:") && id != null) putJsonArray("buttons") {
                addJsonObject { put("label", "Listen on Spotify"); put("url", "https://open.spotify.com/track/$id") }
            }
        }
        val msg = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                if (activity != null) put("activity", activity)
            }
            put("nonce", java.util.UUID.randomUUID().toString())
        }
        runCatching { p.write(frame(1, msg.toString())); lastKey = key }
            .onFailure { close(); status = "Discord isn't running" }
    }

    fun clear() = runCatching { update(null) }
}

