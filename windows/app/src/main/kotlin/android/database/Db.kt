package android.database

interface Cursor : java.io.Closeable {
    val count: Int
    fun moveToFirst(): Boolean
    fun moveToNext(): Boolean
    fun getColumnIndex(name: String): Int
    fun getColumnIndexOrThrow(name: String): Int
    fun getString(i: Int): String?
    fun getLong(i: Int): Long
    fun getInt(i: Int): Int
    fun isNull(i: Int): Boolean
}
open class ContentObserver(handler: android.os.Handler?) { open fun onChange(selfChange: Boolean) {}; open fun onChange(selfChange: Boolean, uri: android.net.Uri?) = onChange(selfChange) }
