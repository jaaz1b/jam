package app.jam.desktop.media

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Where the sink's PCM goes: the PC's sound output (Java Sound), or, with none, a clock at the same pace. */
interface Out {
    val bufferSize: Int
    val longFramePosition: Long
    val isOpen: Boolean
    fun available(): Int
    fun write(b: ByteArray, off: Int, len: Int): Int
    fun start(); fun stop(); fun flush(); fun drain(); fun close()

    companion object {
        fun open(rate: Int, channels: Int, bytes: Int): Out = runCatching {
            val af = AudioFormat(rate.toFloat(), 16, channels, true, false)
            val l = AudioSystem.getSourceDataLine(af)
            l.open(af, bytes - bytes % (channels * 2))
            Sound(l)
        }.getOrElse { app.jam.desktop.shim.Desk.log('W', "Out", "no sound output ($it): playing to a clock", null); Clock(rate, channels, bytes) }
    }

    private class Sound(val l: SourceDataLine) : Out {
        override val bufferSize get() = l.bufferSize
        override val longFramePosition get() = l.longFramePosition
        override val isOpen get() = l.isOpen
        override fun available() = l.available()
        override fun write(b: ByteArray, off: Int, len: Int) = l.write(b, off, len)
        override fun start() = l.start(); override fun stop() = l.stop(); override fun flush() = l.flush(); override fun drain() = l.drain(); override fun close() = l.close()
    }

    /** No sound device: frames are "played" in real time, so positions and song ends work as with one. */
    private class Clock(private val rate: Int, channels: Int, override val bufferSize: Int) : Out {
        private val frameBytes = channels * 2
        private var queued = 0L; private var played = 0L; private var since = 0L; private var running = false
        @Synchronized private fun tick() { if (running) { val now = System.nanoTime(); val f = (now - since) * rate / 1_000_000_000L; val n = minOf(f, queued); played += n; queued -= n; if (f > 0) since = now } }
        override val longFramePosition: Long get() { tick(); return played }
        override val isOpen get() = true
        override fun available(): Int { tick(); return (bufferSize - queued * frameBytes).toInt().coerceAtLeast(0) }
        override fun write(b: ByteArray, off: Int, len: Int): Int {
            while (true) { val a = available(); if (a >= frameBytes || !running) break; Thread.sleep(5) }
            val n = minOf(len, maxOf(available(), if (running) 0 else len)) / frameBytes * frameBytes
            synchronized(this) { queued += n / frameBytes }
            return n
        }
        @Synchronized override fun start() { if (!running) { running = true; since = System.nanoTime() } }
        @Synchronized override fun stop() { tick(); running = false }
        @Synchronized override fun flush() { queued = 0 }
        override fun drain() { while (running && synchronized(this) { tick(); queued > 0 }) Thread.sleep(5) }
        override fun close() { running = false }
    }
}
