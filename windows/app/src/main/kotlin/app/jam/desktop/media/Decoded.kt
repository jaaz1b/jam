package app.jam.desktop.media

import android.media.MediaDataSource
import android.media.MediaFormat
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.nio.ByteBuffer

/**
 * A file decoded by ffmpeg at its own rate and channels, read as MediaExtractor samples (PCM, 16 bit).
 * From a path or URL, or from a MediaDataSource (served to ffmpeg by MediaServer).
 */
class Decoded private constructor(private val url: String, private val served: Boolean) {
    private var decode = Ffmpeg.decode(url, 0, native = true)
    private var frames = 0L
    private var startUs = 0L
    private var ended = false
    private val pending = ByteArray(8192)
    private var pendingLen = 0
    private val rate: Int
    private val channels: Int

    init {
        val until = System.currentTimeMillis() + 15_000
        while (decode.sourceRate == 0 && decode.process.isAlive && System.currentTimeMillis() < until) Thread.sleep(10)
        rate = decode.sourceRate.takeIf { it > 0 } ?: 44100
        channels = decode.sourceChannels.takeIf { it > 0 } ?: 2
        fill()
        if (ended && frames == 0L && pendingLen == 0) { close(); throw java.io.IOException("ffmpeg could not read the file: " + decode.log.lines().lastOrNull { it.isNotBlank() }) }
    }

    fun format(): MediaFormat = MediaFormat.createAudioFormat("audio/raw", rate, channels).apply {
        setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
        if (decode.durationMs > 0) setLong(MediaFormat.KEY_DURATION, decode.durationMs * 1000)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
    }

    private fun fill() {
        if (ended) return
        pendingLen = 0
        while (pendingLen < pending.size) { val n = decode.pcm.read(pending, pendingLen, pending.size - pendingLen); if (n < 0) { ended = pendingLen == 0; break }; pendingLen += n }
        pendingLen -= pendingLen % (2 * channels)
    }

    fun atEnd() = ended && pendingLen == 0
    fun sampleTimeUs(): Long = if (atEnd()) -1 else startUs + frames * 1_000_000 / rate
    /** One sample (the next PCM chunk) into [buf] at [offset]; -1 at the end. Moves on (advance() is then a no-op). */
    fun read(buf: ByteBuffer, offset: Int): Int {
        if (atEnd()) return -1
        val n = minOf(pendingLen, buf.capacity() - offset)
        buf.clear(); buf.position(offset); buf.put(pending, 0, n); buf.flip(); buf.position(offset)
        frames += n / (2 * channels)
        if (n < pendingLen) { System.arraycopy(pending, n, pending, 0, pendingLen - n); pendingLen -= n } else fill()
        return n
    }
    fun seek(us: Long) {
        decode.stop()
        decode = Ffmpeg.decode(url, us / 1000, native = true)
        startUs = us; frames = 0; ended = false; fill()
    }
    fun close() { decode.stop(); if (served) MediaServer.unregister(url) }

    companion object {
        fun open(path: String?, src: MediaDataSource?): Decoded {
            if (path != null) return Decoded(path, false)
            val ds = src!!
            val factory = DataSource.Factory { MediaDataSourceAdapter(ds) }
            return Decoded(MediaServer.register(MediaServer.Entry(factory, android.net.Uri.parse("mds://source"), null, emptyMap())), true)
        }
        /** MediaMetadataRetriever's keys, from a probe. */
        fun probe(path: String): Map<Int, String> {
            val d = runCatching { Ffmpeg.probe(path) }.getOrNull() ?: return emptyMap()
            val out = HashMap<Int, String>()
            if (d.durationMs > 0) out[android.media.MediaMetadataRetriever.METADATA_KEY_DURATION] = d.durationMs.toString()
            Ffmpeg.mimeOf(d.codec)?.let { out[android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE] = it }
            if (d.bitrate > 0) out[android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE] = d.bitrate.toString()
            if (d.sourceRate > 0) out[android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE] = d.sourceRate.toString()
            return out
        }
    }
}

/** A MediaDataSource read as a DataSource (for MediaServer). */
private class MediaDataSourceAdapter(private val src: MediaDataSource) : BaseDataSource(false) {
    private var pos = 0L; private var end = -1L
    override fun open(spec: DataSpec): Long { pos = spec.position; val size = src.size; end = size; return if (size >= 0) size - pos else -1 }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (end >= 0 && pos >= end) return -1
        val n = src.readAt(pos, b, off, len)
        if (n <= 0) return -1
        pos += n; return n
    }
    override fun getUri(): android.net.Uri? = null
    override fun close() {}
}

/** Jam's own media volume (the phone's media volume on Android): the local players and the web page follow it. */
object Volume {
    private val prefs by lazy { android.content.Prefs.of("jam_volume") }
    @Volatile var level: Float = 1f
        get() { if (!loaded) { loaded = true; field = prefs.getFloat("level", 1f) }; return field }
        set(v) { field = v.coerceIn(0f, 1f); loaded = true; prefs.edit().putFloat("level", field).apply(); listeners.forEach { it(field) } }
    private var loaded = false
    val listeners = java.util.concurrent.CopyOnWriteArrayList<(Float) -> Unit>()
    fun outputName(): String = runCatching { javax.sound.sampled.AudioSystem.getMixerInfo().firstOrNull { "Primary" in it.name || "default" in it.name.lowercase() }?.name }.getOrNull() ?: "Speakers"
}
