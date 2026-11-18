package app.jam.desktop.web

import app.jam.desktop.shim.Desk
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.util.concurrent.TimeUnit

/**
 * Keeps the browser's windows out of sight on Windows: off screen, never activated, and off the
 * taskbar and Alt+Tab (a tool window). They stay "shown" for Chromium, so the pages are not
 * throttled as hidden ones (audio, timers). Elsewhere (Linux tests) the browser is headless.
 */
object HiddenWindows {
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_APPWINDOW = 0x00040000
    private const val WS_EX_NOACTIVATE = 0x08000000
    private val done = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Long, Boolean>())

    /** Hides the windows of [process] (and its children) now and again over the next seconds, as they appear. */
    fun hide(process: Process) {
        if (!Desk.isWindows) return
        browsers += process; pidsAt = 0; guard; sweep
        for (ms in longArrayOf(0, 60, 150, 300, 600, 1200, 2500, 5000)) Desk.timer.schedule({ runCatching { pass() } }, ms, TimeUnit.MILLISECONDS)
    }

    /**
     * The browser's own process, as it says (DevTools): Chrome or Edge can start again as another
     * process (an update waiting, a relaunch), whose windows were not ours by the started one's tree —
     * Chrome then showed, with its tabs (reported).
     */
    private val extraPids = java.util.concurrent.CopyOnWriteArraySet<Long>()
    fun track(pid: Long) { if (Desk.isWindows && extraPids.add(pid)) { pidsAt = 0; Desk.timer.schedule({ runCatching { pass() } }, 0, TimeUnit.MILLISECONDS) } }

    /** Every second and a half, any window of the browser that appeared since (a restored tab, a new window) goes too. */
    private val sweep: Unit by lazy {
        Desk.timer.scheduleWithFixedDelay({ runCatching { pass() } }, 1500, 1500, TimeUnit.MILLISECONDS)
        Unit
    }

    private val browsers = java.util.concurrent.CopyOnWriteArraySet<Process>()
    @Volatile private var lastOther: WinDef.HWND? = null

    /**
     * A new browser window can take the foreground as Windows shows it, before it is restyled: the
     * keyboard then goes to the hidden browser instead of Jam (typing into Spotify's sign-in page did
     * nothing). The foreground goes straight back to the window that had it.
     */
    private val guard: Unit by lazy {
        Desk.timer.scheduleWithFixedDelay({
            runCatching {
                val u = User32.INSTANCE
                val fg = u.GetForegroundWindow() ?: return@runCatching
                val pid = IntByReference(); u.GetWindowThreadProcessId(fg, pid)
                if (pid.value.toLong() !in browserPids()) { lastOther = fg; return@runCatching }
                val back = lastOther?.takeIf { u.IsWindow(it) } ?: return@runCatching
                val k = com.sun.jna.platform.win32.Kernel32.INSTANCE
                val fgThread = u.GetWindowThreadProcessId(fg, null)
                val me = k.GetCurrentThreadId()
                u.AttachThreadInput(WinDef.DWORD(fgThread.toLong()), WinDef.DWORD(me.toLong()), true)
                try { u.SetForegroundWindow(back); u.SetFocus(back) }
                finally { u.AttachThreadInput(WinDef.DWORD(fgThread.toLong()), WinDef.DWORD(me.toLong()), false) }
                Desk.log('D', "HiddenWindows", "the hidden browser took the foreground: given back", null)
            }
        }, 200, 150, TimeUnit.MILLISECONDS)
        Unit
    }

    @Volatile private var pids: Set<Long> = emptySet()
    @Volatile private var pidsAt = 0L
    private fun browserPids(): Set<Long> {
        if (System.currentTimeMillis() - pidsAt < 3000) return pids
        browsers.removeIf { !it.isAlive }
        pids = HashSet<Long>().apply {
            for (p in browsers) { add(p.pid()); runCatching { p.toHandle().descendants().forEach { add(it.pid()) } } }
            for (pid in extraPids) { add(pid); runCatching { ProcessHandle.of(pid).ifPresent { h -> h.descendants().forEach { add(it.pid()) } } } }
        }
        pidsAt = System.currentTimeMillis()
        return pids
    }

    private fun pass() {
        pidsAt = 0
        val pids = browserPids()
        if (pids.isEmpty()) return
        val u = User32.INSTANCE
        u.EnumWindows({ hwnd, _ ->
            val pid = IntByReference(); u.GetWindowThreadProcessId(hwnd, pid)
            if (pid.value.toLong() in pids) {
                val key = Pointer.nativeValue(hwnd.pointer)
                val ex = u.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
                val want = (ex or WS_EX_TOOLWINDOW or WS_EX_NOACTIVATE) and WS_EX_APPWINDOW.inv()
                val visible = u.IsWindowVisible(hwnd)
                if (ex != want || key !in done) {
                    // The taskbar reads the style when a window is shown: hidden, restyled, shown again (inactive).
                    if (visible) u.ShowWindow(hwnd, WinUser.SW_HIDE)
                    u.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, want)
                    u.SetWindowPos(hwnd, null, -32000, -32000, 1280, 800, 0x0010 /* NOACTIVATE */ or 0x0004 /* NOZORDER */)
                    if (visible) u.ShowWindow(hwnd, 4 /* SW_SHOWNOACTIVATE */)
                    done += key
                } else {
                    val r = WinDef.RECT(); u.GetWindowRect(hwnd, r)
                    if (r.left > -20000) u.SetWindowPos(hwnd, null, -32000, -32000, 0, 0, 0x0001 /* NOSIZE */ or 0x0010 or 0x0004)
                }
            }
            true
        }, null)
    }
}
