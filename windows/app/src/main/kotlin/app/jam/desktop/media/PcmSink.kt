package app.jam.desktop.media

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import app.jam.desktop.shim.Desk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * media3's DefaultAudioSink on the PC: the app's audio processors (equalizer, spatial, AutoMix's
 * segue), then the speed (pitch kept), the silence skipping and the volume, into a Java Sound line
 * (Windows' audio mixer). Knows which moment of the song is heard now.
 */
class PcmSink(private val processors: Array<AudioProcessor>, private val bufferUs: Int) : AudioSink {
    private var listener: AudioSink.Listener? = null
    private var line: SourceDataLine? = null
    private var outFormat = AudioProcessor.AudioFormat(Ffmpeg.RATE, 2, C.ENCODING_PCM_16BIT)
    private var active = emptyList<AudioProcessor>()
    private val stretch = Stretch()
    private var params = PlaybackParameters.DEFAULT
    private var skipSilence = false
    private var silentRun = 0
    @Volatile private var volume = 1f
    private var attributes = AudioAttributes.DEFAULT
    private var playing = false
    private var ended = false
    private var offsetUs = 0L
    // What has been written to the line: (line frame at the end, media time at the end), for the position heard.
    private val marks = ArrayDeque<LongArray>()
    private var written = 0L
    private var lineBase = 0L
    private var mediaUs = 0L
    private var inputFrameUs = 0L

    override fun setListener(l: AudioSink.Listener?) { listener = l }
    override fun supportsFormat(f: Format) = true
    override fun getFormatSupport(f: Format) = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

    override fun configure(input: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        var fmt = AudioProcessor.AudioFormat(input.sampleRate.takeIf { it > 0 } ?: Ffmpeg.RATE, input.channelCount.takeIf { it > 0 } ?: 2, input.pcmEncoding.takeIf { it > 0 } ?: C.ENCODING_PCM_16BIT)
        val on = ArrayList<AudioProcessor>()
        for (p in processors) {
            val out = runCatching { p.configure(fmt) }.getOrElse { Desk.log('W', "PcmSink", "processor ${p.javaClass.simpleName}: $it", null); AudioProcessor.AudioFormat.NOT_SET }
            if (p.isActive) { on += p; fmt = out }
        }
        active = on
        for (p in processors) p.flush()
        if (line == null || fmt.sampleRate != outFormat.sampleRate || fmt.channelCount != outFormat.channelCount) openLine(fmt)
        outFormat = fmt
    }

    private fun openLine(fmt: AudioProcessor.AudioFormat) {
        line?.let { runCatching { it.stop(); it.close() } }
        val af = AudioFormat(fmt.sampleRate.toFloat(), 16, fmt.channelCount, true, false)
        val l = AudioSystem.getSourceDataLine(af)
        val bytes = (fmt.sampleRate.toLong() * fmt.channelCount * 2 * bufferUs.coerceAtLeast(100_000) / 1_000_000).toInt()
        l.open(af, bytes - bytes % (fmt.channelCount * 2))
        line = l; lineBase = l.longFramePosition; written = 0; marks.clear()
        if (playing) l.start()
    }

    override fun play() { playing = true; line?.start() }
    override fun pause() { playing = false; line?.stop() }
    override fun handleDiscontinuity() {}
    override fun setOutputStreamOffsetUs(o: Long) { offsetUs = o }

    /** Takes the whole buffer (blocking while the line is full) and says so. */
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, count: Int): Boolean {
        ended = false
        val frames0 = buffer.remaining() / 4
        val endUs = presentationTimeUs - offsetUs + frames0 * 1_000_000L / Ffmpeg.RATE
        var data: ByteBuffer = buffer
        for (p in active) {
            val out = ArrayList<ByteBuffer>()
            p.queueInput(data)
            out += copyOf(p.output)
            while (data.hasRemaining()) { p.queueInput(data); out += copyOf(p.output) }
            data = join(out)
        }
        if (buffer.hasRemaining()) buffer.position(buffer.limit())
        write(data, endUs)
        return true
    }

    private fun copyOf(b: ByteBuffer): ByteBuffer { val c = ByteBuffer.allocate(b.remaining()).order(ByteOrder.nativeOrder()); c.put(b); c.flip(); return c }
    private fun join(list: List<ByteBuffer>): ByteBuffer { val all = ByteBuffer.allocate(list.sumOf { it.remaining() }).order(ByteOrder.nativeOrder()); list.forEach { all.put(it) }; all.flip(); return all }

    private fun write(pcm: ByteBuffer, endUs: Long) {
        val l = line ?: return
        val ch = outFormat.channelCount
        val float = outFormat.encoding == C.ENCODING_PCM_FLOAT
        val frames = pcm.remaining() / (ch * if (float) 4 else 2)
        var f = FloatArray(frames * ch)
        if (float) pcm.asFloatBuffer().get(f) else { val s = pcm.asShortBuffer(); for (i in f.indices) f[i] = s.get(i) / 32768f }
        stretch.speed = params.speed
        f = stretch.process(f, frames)
        if (skipSilence) f = dropSilence(f, ch)
        val v = volume
        val out = ByteArray(f.size * 2)
        for (i in f.indices) {
            val s = (f[i] * v * 32767f).toInt().coerceIn(-32768, 32767)
            out[2 * i] = s.toByte(); out[2 * i + 1] = (s shr 8).toByte()
        }
        var at = 0
        while (at < out.size) {
            val n = l.write(out, at, out.size - at)
            if (n <= 0) { if (!l.isOpen) return; Thread.sleep(2) }
            at += n
        }
        written += f.size / ch
        synchronized(marks) { marks.addLast(longArrayOf(written, endUs)); while (marks.size > 512) marks.removeFirst() }
        mediaUs = endUs
    }

    private fun dropSilence(f: FloatArray, ch: Int): FloatArray {
        val keep = ArrayList<Float>(f.size)
        val limit = (Ffmpeg.RATE * 0.15).toInt()
        var i = 0
        while (i < f.size) {
            var quiet = true
            for (c in 0 until ch) if (kotlin.math.abs(f[i + c]) > 0.0032f) quiet = false
            silentRun = if (quiet) silentRun + 1 else 0
            if (silentRun < limit) for (c in 0 until ch) keep += f[i + c]
            i += ch
        }
        return keep.toFloatArray()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val l = line ?: return AudioSink.CURRENT_POSITION_NOT_SET
        val heard = l.longFramePosition - lineBase
        synchronized(marks) {
            if (marks.isEmpty()) return AudioSink.CURRENT_POSITION_NOT_SET
            var prevF = 0L; var prevUs = marks.first()[1] - marks.first()[0] * 1_000_000L / outFormat.sampleRate
            for (m in marks) {
                if (heard <= m[0]) {
                    val span = (m[0] - prevF).coerceAtLeast(1)
                    return prevUs + (m[1] - prevUs) * (heard - prevF).coerceAtLeast(0) / span
                }
                prevF = m[0]; prevUs = m[1]
            }
            return marks.last()[1]
        }
    }

    override fun playToEndOfStream() {
        val l = line ?: return
        for (p in active) p.queueEndOfStream()
        val rest = stretch.drain()
        if (rest.isNotEmpty()) {
            val b = ByteBuffer.allocate(rest.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (x in rest) b.putShort((x * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            b.flip(); stretch.speed = 1f; write(b, mediaUs)
        }
        if (playing) l.drain()
        ended = true
    }
    override fun isEnded() = ended
    override fun hasPendingData(): Boolean = line?.let { it.available() < it.bufferSize } ?: false
    override fun setPlaybackParameters(p: PlaybackParameters) { params = p }
    override fun getPlaybackParameters() = params
    override fun setSkipSilenceEnabled(on: Boolean) { skipSilence = on; silentRun = 0 }
    override fun getSkipSilenceEnabled() = skipSilence
    override fun setAudioAttributes(a: AudioAttributes) { attributes = a }
    override fun getAudioAttributes() = attributes
    override fun setAudioSessionId(id: Int) {}
    override fun enableTunnelingV21() {}
    override fun disableTunneling() {}
    override fun setVolume(v: Float) { volume = v }
    override fun getAudioTrackBufferSizeUs(): Long = bufferUs.toLong()
    override fun flush() {
        line?.let { it.flush(); lineBase = it.longFramePosition }
        written = 0; synchronized(marks) { marks.clear() }
        stretch.reset(); silentRun = 0; ended = false
        for (p in processors) p.flush()
    }
    override fun reset() { flush(); line?.let { runCatching { it.stop(); it.close() } }; line = null; for (p in processors) p.reset() }
    override fun release() = reset()
}
