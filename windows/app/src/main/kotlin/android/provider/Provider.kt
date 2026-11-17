package android.provider

object Settings {
    const val ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
    const val ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
    const val ACTION_BLUETOOTH_SETTINGS = "android.settings.BLUETOOTH_SETTINGS"
    const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    const val ACTION_SETTINGS = "android.settings.SETTINGS"
    const val ACTION_SOUND_SETTINGS = "android.settings.SOUND_SETTINGS"
    const val ACTION_WIFI_SETTINGS = "android.settings.WIFI_SETTINGS"
    const val ACTION_APP_LOCALE_SETTINGS = "android.settings.APP_LOCALE_SETTINGS"
    const val ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION = "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"
    const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
    const val EXTRA_CHANNEL_ID = "android.provider.extra.CHANNEL_ID"
    object Secure { const val ANDROID_ID = "android_id"
        @JvmStatic fun getString(r: android.content.ContentResolver, name: String): String? = if (name == ANDROID_ID) app.jam.desktop.shim.Desk.deviceId else null
        @JvmStatic fun getInt(r: android.content.ContentResolver, name: String, d: Int): Int = d
        @JvmStatic fun getFloat(r: android.content.ContentResolver, name: String, d: Float): Float = d }
    object Global { const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"; const val DEVICE_NAME = "device_name"; const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        @JvmStatic fun getString(r: android.content.ContentResolver, name: String): String? = if (name == DEVICE_NAME) app.jam.desktop.shim.Desk.deviceName else null
        @JvmStatic fun getFloat(r: android.content.ContentResolver, name: String, d: Float): Float = if (name == ANIMATOR_DURATION_SCALE) 1f else d
        @JvmStatic fun getFloat(r: android.content.ContentResolver, name: String): Float = 1f
        @JvmStatic fun getInt(r: android.content.ContentResolver, name: String, d: Int): Int = d }
    object System { @JvmStatic fun getString(r: android.content.ContentResolver, name: String): String? = null; @JvmStatic fun getInt(r: android.content.ContentResolver, name: String, d: Int): Int = d
        @JvmStatic fun canWrite(c: android.content.Context): Boolean = false; const val SCREEN_BRIGHTNESS = "screen_brightness" }
    @JvmStatic fun canDrawOverlays(c: android.content.Context): Boolean = false
}

object MediaStore {
    const val VOLUME_EXTERNAL = "external"; const val UNKNOWN_STRING = "<unknown>"; const val INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"
    const val EXTRA_MEDIA_FOCUS = "android.intent.extra.focus"; const val EXTRA_MEDIA_TITLE = "android.intent.extra.title"; const val EXTRA_MEDIA_ARTIST = "android.intent.extra.artist"; const val EXTRA_MEDIA_ALBUM = "android.intent.extra.album"
    object MediaColumns { const val SIZE = "_size"; const val DATE_MODIFIED = "date_modified"; const val DISPLAY_NAME = "_display_name"; const val DATA = "_data"; const val RELATIVE_PATH = "relative_path"; const val MIME_TYPE = "mime_type" }
    object Audio {
        object Media { const val _ID = "_id"; const val TITLE = "title"; const val ARTIST = "artist"; const val ALBUM = "album"; const val ALBUM_ID = "album_id"; const val DURATION = "duration"; const val TRACK = "track"
            const val IS_PODCAST = "is_podcast"; const val IS_AUDIOBOOK = "is_audiobook"; const val IS_MUSIC = "is_music"; const val RELATIVE_PATH = "relative_path"; const val DATA = "_data"; const val DISPLAY_NAME = "_display_name"
            const val SIZE = "_size"; const val DATE_MODIFIED = "date_modified"; const val MIME_TYPE = "mime_type"; const val YEAR = "year"; const val ARTIST_ID = "artist_id"
            @JvmField val EXTERNAL_CONTENT_URI: android.net.Uri = android.net.Uri.parse("content://media/external/audio/media")
            @JvmStatic fun getContentUri(volume: String): android.net.Uri = EXTERNAL_CONTENT_URI }
        object Albums { @JvmField val EXTERNAL_CONTENT_URI: android.net.Uri = android.net.Uri.parse("content://media/external/audio/albums") }
    }
}
object DocumentsContract { @JvmStatic fun getTreeDocumentId(u: android.net.Uri): String = u.path ?: ""; @JvmStatic fun buildChildDocumentsUriUsingTree(u: android.net.Uri, id: String): android.net.Uri = u
    @JvmStatic fun buildDocumentUriUsingTree(u: android.net.Uri, id: String): android.net.Uri = u; @JvmStatic fun getDocumentId(u: android.net.Uri): String = u.path ?: ""
    object Document { const val COLUMN_DOCUMENT_ID = "document_id"; const val COLUMN_DISPLAY_NAME = "_display_name"; const val COLUMN_MIME_TYPE = "mime_type"; const val COLUMN_SIZE = "_size"; const val COLUMN_LAST_MODIFIED = "last_modified"; const val MIME_TYPE_DIR = "vnd.android.document/directory" } }
object OpenableColumns { const val DISPLAY_NAME = "_display_name"; const val SIZE = "_size" }
