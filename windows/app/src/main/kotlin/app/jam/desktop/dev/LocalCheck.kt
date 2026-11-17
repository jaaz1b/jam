package app.jam.desktop.dev

import android.provider.MediaStore

/** Lists what Local Files would show (MediaStore over the PC's Music folder), and each cover's size. */
fun main() {
    val t0 = System.currentTimeMillis()
    val c = android.content.ContentResolver.query(MediaStore.Audio.Media.getContentUri("external"), arrayOf("_id", "title", "artist", "album", "duration", "album_id", "relative_path", "track"), null, null, null)!!
    while (c.moveToNext()) {
        val art = android.net.Uri.parse("content://media/external/audio/albumart/" + c.getLong(5))
        val bytes = runCatching { app.jam.desktop.shim.Files.open(art)?.use { it.readBytes().size } }.getOrNull()
        val audio = app.jam.desktop.shim.Files.file(android.net.Uri.parse("content://media/external/audio/media/" + c.getLong(0)))
        println("${c.getString(1)} | ${c.getString(2)} | ${c.getString(3)} | ${c.getLong(4)}ms | ${c.getString(6)} | #${c.getInt(7)} | art=$bytes | ${audio?.name}")
    }
    println("${c.count} tracks in ${System.currentTimeMillis() - t0} ms")
}
