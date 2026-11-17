package app.jam.desktop.media

import app.jam.desktop.shim.Desk
import java.io.File
import java.io.InputStream

/**
 * The decoder: ffmpeg (shipped with Jam on Windows), reading an item's bytes from [MediaServer] and
 * writing 16-bit stereo PCM at [RATE] Hz to its output, from a given position.
 */
object Ffmpeg {
    const val RATE = 48_000
    const val CHANNELS = 2
    val exe: String by lazy {
        val name = if (Desk.isWindows) "ffmpeg.exe" else "ffmpeg"
        listOfNotNull(System.getProperty("compose.application.resources.dir"), System.getProperty("jam.ffmpeg.dir"))
            .map { File(it, name) }.firstOrNull { it.isFile }?.absolutePath ?: name
    }

    class Decode(val process: Process, val pcm: InputStream) {
        @Volatile var durationMs: Long = -1
        @Volatile var sampleMime: String? = null
        @Volatile var codec: String? = null
        @Volatile var sourceRate = 0
        @Volatile var sourceChannels = 0
        @Volatile var bitrate = 0
        val log = StringBuilder()
        fun stop() { runCatching { process.destroyForcibly() }; runCatching { pcm.close() } }
    }

    fun decode(url: String, startMs: Long, endMs: Long? = null, extraInput: List<String> = emptyList()): Decode {
        val args = mutableListOf(exe, "-hide_banner", "-nostdin", "-loglevel", "info", "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "4")
        if (startMs > 0) args += listOf("-ss", "%.3f".format(java.util.Locale.ROOT, startMs / 1000.0))
        if (endMs != null) args += listOf("-to", "%.3f".format(java.util.Locale.ROOT, endMs / 1000.0))
        args += extraInput
        args += listOf("-i", url, "-vn", "-sn", "-dn", "-map", "0:a:0?", "-ac", "$CHANNELS", "-ar", "$RATE", "-f", "s16le", "-acodec", "pcm_s16le", "pipe:1")
        val p = ProcessBuilder(args).start()
        val d = Decode(p, p.inputStream.buffered(256 * 1024))
        Thread({
            p.errorStream.bufferedReader().useLines { lines ->
                for (l in lines) {
                    if (d.log.length < 8000) d.log.append(l).append('\n')
                    Regex("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)").find(l)?.let { m ->
                        val (h, mi, s, cs) = m.destructured
                        if (d.durationMs < 0) d.durationMs = (h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()) * 1000 + cs.padEnd(3, '0').take(3).toLong()
                    }
                    Regex("bitrate: (\\d+) kb/s").find(l)?.let { if (d.bitrate == 0) d.bitrate = it.groupValues[1].toInt() * 1000 }
                    Regex("Stream #0:\\d+.*Audio: (\\w+)[^,]*, (\\d+) Hz, ([^,]+)").find(l)?.let { m ->
                        if (d.codec == null) { d.codec = m.groupValues[1]; d.sourceRate = m.groupValues[2].toInt(); d.sourceChannels = if ("mono" in m.groupValues[3]) 1 else 2 }
                    }
                }
            }
        }, "ffmpeg-log").apply { isDaemon = true }.start()
        return d
    }

    fun mimeOf(codec: String?): String? = when (codec) {
        "mp3" -> "audio/mpeg"; "aac" -> "audio/mp4a-latm"; "opus" -> "audio/opus"; "vorbis" -> "audio/vorbis"; "flac" -> "audio/flac"
        "alac" -> "audio/alac"; null -> null; else -> "audio/$codec"
    }
}
