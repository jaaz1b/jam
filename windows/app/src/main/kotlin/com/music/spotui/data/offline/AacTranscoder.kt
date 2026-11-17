// Jam for Windows: replaces the Android file of the same name (the phone's AAC encoder and muxer).
// The same result, an AAC song in MP4, made by ffmpeg.
package com.music.spotui.data.offline

import java.io.File
import java.io.IOException

object AacTranscoder {
    const val BITRATE = 96_000

    /** Writes [output]; throws on any codec error (the caller marks the song failed). */
    fun transcode(input: File, output: File, bitrate: Int = BITRATE) {
        val p = ProcessBuilder(app.jam.desktop.media.Ffmpeg.exe, "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-i", input.path,
            "-vn", "-map", "0:a:0", "-ac", "2", "-ar", "48000", "-c:a", "aac", "-b:a", "$bitrate", "-movflags", "+faststart", "-f", "mp4", output.path)
            .redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0 || !output.isFile || output.length() == 0L) { output.delete(); throw IOException("ffmpeg: ${log.lines().lastOrNull { it.isNotBlank() } ?: "failed"}") }
    }
}
