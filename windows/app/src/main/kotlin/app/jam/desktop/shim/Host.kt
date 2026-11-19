package app.jam.desktop.shim

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** What the app asks of its window (app.jam.desktop.Window listens): close, minimise, recreate, flags. */
object Windows {
    /** Bumped when the app's language changes: string resources are read again. */
    val localeKey = mutableIntStateOf(0)
    val keepAwake = mutableStateOf(false)
    val fullScreen = mutableStateOf(false)
    @Volatile var onFinish: ((android.app.Activity) -> Unit)? = null
    @Volatile var onMinimize: (() -> Unit)? = null
    @Volatile var onRecreate: ((android.app.Activity) -> Unit)? = null
    /** Activities other than the main one (Jam's settings page...), shown over it, the last on top. */
    val stack = androidx.compose.runtime.mutableStateListOf<android.app.Activity>()
    fun openActivity(intent: android.content.Intent) = Desk.post {
        val a = runCatching { Class.forName(intent.component!!.className).getDeclaredConstructor().newInstance() as android.app.Activity }.getOrElse { Desk.log('E', "Windows", "activity: $it", it); return@post }
        a.intent = intent
        a.onCreate(null)
        if (a.isFinishing) return@post
        a.content.value?.attachAsRoot(true)
        a.onStart(); a.onResume()
        stack += a
    }
    fun finish(a: android.app.Activity) {
        if (stack.remove(a)) { runCatching { a.onPause(); a.onStop(); a.content.value?.attachAsRoot(false); a.onDestroy() }; return }
        onFinish?.invoke(a)
    }
    fun minimize(a: android.app.Activity) { onMinimize?.invoke() }
    fun recreate(a: android.app.Activity) { onRecreate?.invoke(a) }
    fun flagsChanged(a: android.app.Activity) { keepAwake.value = a.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
    fun localeChanged() { localeKey.intValue++ }
    fun systemBars(types: Int, show: Boolean) {}
}

/** Services started by intent (PlaybackService...): one instance each, created on first start. */
object ServiceHost {
    private val running = java.util.concurrent.ConcurrentHashMap<String, android.app.Service>()
    private val ids = java.util.concurrent.atomic.AtomicInteger()
    fun start(intent: android.content.Intent): Boolean {
        val cls = intent.component?.className ?: return false
        val k = runCatching { Class.forName(cls) }.getOrNull() ?: return false
        if (!android.app.Service::class.java.isAssignableFrom(k)) return false
        val run = {
            val s = running.getOrPut(cls) { (k.getDeclaredConstructor().newInstance() as android.app.Service).also { it.onCreate() } }
            runCatching { s.onStartCommand(intent, 0, ids.incrementAndGet()) }.onFailure { Desk.log('E', "ServiceHost", "$cls: $it", it) }
        }
        if (Desk.isMain()) run() else Desk.post { run() }
        return true
    }
    fun get(cls: String): android.app.Service? = running[cls]
    fun stop(s: android.app.Service) {
        val k = running.entries.firstOrNull { it.value === s }?.key ?: return
        running.remove(k); runCatching { s.onDestroy() }
    }
    fun stopAll() { running.values.toList().forEach { stop(it) } }
}

/** Notifications: the ongoing media one feeds Windows' media controls; others become toasts in the window. */
object Notices {
    /** A message for the person, in a dialog over the window (a refusal explained, what to do). */
    val alert = androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)
    fun alert(title: String, text: String) { alert.value = title to text; app.jam.desktop.Front.request() }

    @Volatile var onNotice: ((tag: String?, id: Int, n: android.app.Notification) -> Unit)? = null
    fun notify(tag: String?, id: Int, n: android.app.Notification) {
        onNotice?.invoke(tag, id, n) ?: run {
            if (n.ongoing || n.title == null) return@run
            // In the window: Jam's own toast. In the tray: a Windows notification.
            if (app.jam.desktop.Front.shown.value) Toasts.show(listOfNotNull(n.title, n.text).joinToString(" · "), true)
            else app.jam.desktop.Front.notify(n.title.toString(), n.text?.toString())
        }
    }
    fun cancel(tag: String?, id: Int) {}
}

/** Windows' own file dialogs (AWT FileDialog: the native one on Windows). */
object FilePicker {
    private fun extensionsFor(mimes: List<String>): List<String> = mimes.flatMap { m ->
        when {
            m.startsWith("image/") -> listOf("png", "jpg", "jpeg", "webp", "gif")
            m.startsWith("audio/") -> listOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac")
            m == "application/javascript" || m == "text/javascript" -> listOf("js")
            m == "application/json" -> listOf("json")
            m == "application/zip" -> listOf("zip", "jam")
            else -> emptyList()
        }
    }
    private fun dialog(mode: Int, mimes: List<String>, name: String? = null, multi: Boolean = false): List<java.io.File> {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "Jam", mode)
        val ext = extensionsFor(mimes)
        if (ext.isNotEmpty()) { d.filenameFilter = java.io.FilenameFilter { _, n -> ext.any { n.lowercase().endsWith(".$it") } }; if (Desk.isWindows) d.file = ext.joinToString(";") { "*.$it" } }
        if (name != null) d.file = name
        d.isMultipleMode = multi
        d.isVisible = true
        return d.files.toList()
    }
    fun open(mimes: List<String>, done: (java.io.File?) -> Unit) = Desk.post { done(dialog(java.awt.FileDialog.LOAD, mimes).firstOrNull()) }
    fun openMany(mimes: List<String>, done: (List<java.io.File>) -> Unit) = Desk.post { done(dialog(java.awt.FileDialog.LOAD, mimes, multi = true)) }
    fun save(name: String, done: (java.io.File?) -> Unit) = Desk.post { done(dialog(java.awt.FileDialog.SAVE, emptyList(), name).firstOrNull()) }
    fun folder(done: (java.io.File?) -> Unit) = Desk.post {
        val c = javax.swing.JFileChooser().apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
        done(if (c.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) c.selectedFile else null)
    }
}

/** ViewModels made by reflection (no-arg, or one Application / SavedStateHandle argument), as Android's default factory does. */
object ReflectiveViewModels : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
        val k = modelClass.java
        k.constructors.firstOrNull { it.parameterCount == 0 }?.let { @Suppress("UNCHECKED_CAST") return it.newInstance() as T }
        k.constructors.firstOrNull { it.parameterCount == 1 && android.app.Application::class.java.isAssignableFrom(it.parameterTypes[0]) }?.let {
            @Suppress("UNCHECKED_CAST") return it.newInstance(DesktopApp.context) as T
        }
        k.constructors.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0] == androidx.lifecycle.SavedStateHandle::class.java }?.let {
            @Suppress("UNCHECKED_CAST") return it.newInstance(androidx.lifecycle.SavedStateHandle()) as T
        }
        error("no way to make ${k.name}")
    }
}
