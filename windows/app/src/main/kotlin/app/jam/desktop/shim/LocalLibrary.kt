package app.jam.desktop.shim

import android.database.Cursor
import app.jam.desktop.media.Ffmpeg
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The phone's MediaStore on the PC: the audio files under the user's Music folder (and any folders
 * added in jam_local_roots), read once with ffmpeg and kept in an index, so Local Files lists them.
 * content://media/external/audio/media/<id> opens the file; …/albumart/<id> the embedded cover.
 */
object LocalLibrary {
    data class Track(
        val id: Long, val path: String, val title: String, val artist: String, val album: String,
        val durationMs: Long, val albumId: Long, val relative: String, val trackNo: Int, val size: Long, val modified: Long, val year: Int,
    )

    private val exts = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "aiff", "aif", "alac", "ape", "wv", "mka", "webm")
    private val index = File(Desk.dataDir, "local-index.tsv")
    private val covers get() = File(Desk.cacheDir, "local-art").apply { mkdirs() }
    @Volatile private var tracks: List<Track>? = null
    private val byId = ConcurrentHashMap<Long, Track>()
    private val coverOf = ConcurrentHashMap<Long, String>()

    fun roots(): List<File> {
        val home = System.getProperty("user.home")
        val extra = android.content.Prefs.of("jam_local_roots").getStringSet("roots", emptySet()).orEmpty().map(::File)
        val music = listOfNotNull(File(home, "Music"), System.getenv("OneDrive")?.let { File(it, "Music") })
        return (music + extra).filter { it.isDirectory }.distinctBy { it.canonicalPath }
    }

    fun id(path: String): Long = (path.lowercase().hashCode().toLong() and 0xffffffffL) or (path.length.toLong() shl 32)

    @Synchronized
    fun all(refresh: Boolean = false): List<Track> {
        tracks?.takeIf { !refresh }?.let { return it }
        val old = load()
        val files = roots().flatMap { root ->
            root.walkTopDown().onEnter { !it.name.startsWith('.') && it.name != "\$RECYCLE.BIN" }
                .filter { it.isFile && it.extension.lowercase() in exts }.map { root to it }.toList()
        }.distinctBy { it.second.absolutePath }
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
        val out = files.map { (root, f) ->
            pool.submit<Track?> {
                old[f.absolutePath]?.takeIf { it.modified == f.lastModified() && it.size == f.length() } ?: read(root, f)
            }
        }.mapNotNull { runCatching { it.get(60, TimeUnit.SECONDS) }.getOrNull() }
        pool.shutdown()
        save(out)
        byId.clear(); out.forEach { byId[it.id] = it }
        out.groupBy { it.albumId }.forEach { (a, list) -> coverOf[a] = list.first().path }
        tracks = out
        return out
    }

    private fun read(root: File, f: File): Track? {
        val p = runCatching { ProcessBuilder(Ffmpeg.exe, "-hide_banner", "-nostdin", "-i", f.absolutePath).redirectErrorStream(true).start() }.getOrNull() ?: return null
        val text = p.inputStream.bufferedReader().readText(); p.waitFor(20, TimeUnit.SECONDS)
        if (!text.contains("Audio:")) return null
        val tags = HashMap<String, String>()
        var inMeta = false
        for (l in text.lines()) {
            val t = l.trim()
            if (t.startsWith("Metadata:")) { inMeta = true; continue }
            if (t.startsWith("Duration:") || t.startsWith("Stream #") || t.startsWith("Input #")) { inMeta = t.startsWith("Input #") && false; if (t.startsWith("Stream #")) break; continue }
            if (inMeta) Regex("^([A-Za-z_ \\-]+?)\\s*:\\s?(.*)$").find(t)?.let { m -> tags.putIfAbsent(m.groupValues[1].trim().lowercase(), m.groupValues[2].trim()) }
        }
        val dur = Regex("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)").find(text)?.destructured?.let { (h, m, s, cs) ->
            (h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + cs.padEnd(3, '0').take(3).toLong()
        } ?: 0L
        val name = f.nameWithoutExtension
        val dashed = name.split(" - ", limit = 2).takeIf { it.size == 2 }
        val artist = tags["artist"] ?: tags["album_artist"] ?: dashed?.get(0)?.replace(Regex("^\\d+[ .-]+"), "")?.trim() ?: ""
        val title = tags["title"] ?: dashed?.get(1) ?: name
        val album = tags["album"] ?: f.parentFile?.name.orEmpty()
        val albumArtist = tags["album_artist"] ?: artist
        val rel = root.name + "/" + f.parentFile.relativeTo(root).invariantSeparatorsPath.let { if (it.isEmpty()) "" else "$it/" }
        return Track(
            id(f.absolutePath), f.absolutePath, title, artist, album, dur,
            id("album:" + albumArtist.lowercase() + "/" + album.lowercase() + "/" + f.parent.lowercase()), rel,
            tags["track"]?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0, f.length(), f.lastModified(),
            (tags["date"] ?: tags["year"])?.take(4)?.toIntOrNull() ?: 0,
        )
    }

    private fun load(): Map<String, Track> = runCatching {
        index.readLines().mapNotNull { l ->
            val c = l.split('\t'); if (c.size < 12) null
            else Track(c[0].toLong(), c[1], c[2], c[3], c[4], c[5].toLong(), c[6].toLong(), c[7], c[8].toInt(), c[9].toLong(), c[10].toLong(), c[11].toInt())
        }.associateBy { it.path }
    }.getOrDefault(emptyMap())

    private fun save(list: List<Track>) = runCatching {
        fun s(v: String) = v.replace('\t', ' ').replace('\n', ' ')
        index.writeText(list.joinToString("\n") { t -> listOf(t.id, s(t.path), s(t.title), s(t.artist), s(t.album), t.durationMs, t.albumId, s(t.relative), t.trackNo, t.size, t.modified, t.year).joinToString("\t") })
    }

    /** The file behind content://media/external/audio/media/<id> or …/albumart/<albumId> (extracted). */
    fun file(segments: List<String>): File? {
        if (segments.size < 4 || segments[0] != "external" || segments[1] != "audio") return null
        val id = segments.last().toLongOrNull() ?: return null
        if (tracks == null) all()
        return when (segments[2]) {
            "media" -> byId[id]?.let { File(it.path) }
            "albumart", "albums" -> art(id)
            else -> null
        }
    }

    private fun art(albumId: Long): File? {
        val out = File(covers, "$albumId.jpg")
        if (out.isFile) return out.takeIf { it.length() > 0 }
        val src = coverOf[albumId] ?: return null
        File(src).parentFile?.listFiles()?.firstOrNull { it.name.lowercase() in setOf("cover.jpg", "folder.jpg", "front.jpg", "cover.png", "folder.png", "albumart.jpg") }
            ?.let { it.copyTo(out, true); return out }
        runCatching {
            ProcessBuilder(Ffmpeg.exe, "-hide_banner", "-nostdin", "-y", "-i", src, "-an", "-map", "0:v:0?", "-frames:v", "1", "-vf", "scale='min(600,iw)':-2", out.absolutePath)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(20, TimeUnit.SECONDS)
        }
        if (!out.isFile) out.writeBytes(ByteArray(0))
        return out.takeIf { it.length() > 0 }
    }

    fun one(id: Long, projection: Array<String>?): Cursor {
        val c = cursor(projection) as ListCursor
        return c.only(id)
    }

    /** MediaStore's audio table, for a query's projection. */
    fun cursor(projection: Array<String>?): Cursor {
        val cols = projection ?: arrayOf("_id", "title", "artist", "album", "duration", "album_id", "relative_path", "track", "_data")
        val rows = all().sortedBy { it.title.lowercase() }.map { t ->
            cols.map { c ->
                when (c) {
                    "_id" -> t.id; "title" -> t.title; "artist" -> t.artist.ifEmpty { "<unknown>" }; "album" -> t.album
                    "duration" -> t.durationMs; "album_id" -> t.albumId; "relative_path" -> t.relative; "track" -> t.trackNo
                    "_data" -> t.path; "_display_name" -> File(t.path).name; "_size" -> t.size; "date_modified" -> t.modified / 1000
                    "is_music" -> 1; "is_podcast", "is_audiobook" -> 0; "year" -> t.year; "mime_type" -> "audio/" + File(t.path).extension.lowercase()
                    else -> null
                }
            }
        }
        return ListCursor(cols, rows)
    }
}

/** A cursor over rows in memory. */
class ListCursor(private val cols: Array<String>, private val rows: List<List<Any?>>) : Cursor {
    private var pos = -1
    override val count get() = rows.size
    override fun moveToFirst(): Boolean { pos = 0; return rows.isNotEmpty() }
    override fun moveToNext(): Boolean { if (pos < rows.size) pos++; return pos < rows.size }
    override fun getColumnIndex(name: String) = cols.indexOf(name)
    override fun getColumnIndexOrThrow(name: String) = cols.indexOf(name).also { require(it >= 0) { "no column $name" } }
    override fun getString(i: Int) = rows[pos][i]?.toString()
    override fun getLong(i: Int) = (rows[pos][i] as? Number)?.toLong() ?: rows[pos][i]?.toString()?.toLongOrNull() ?: 0L
    override fun getInt(i: Int) = getLong(i).toInt()
    override fun isNull(i: Int) = rows[pos][i] == null
    override fun close() {}
    fun only(id: Long): ListCursor { val i = cols.indexOf("_id"); return ListCursor(cols, if (i < 0) emptyList() else rows.filter { (it[i] as? Number)?.toLong() == id }) }
}
