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
        for (ms in longArrayOf(0, 60, 150, 300, 600, 1200, 2500, 5000)) Desk.timer.schedule({ runCatching { pass(process) } }, ms, TimeUnit.MILLISECONDS)
    }

    private fun pass(process: Process) {
        val pids = HashSet<Long>().apply { add(process.pid()); process.toHandle().descendants().forEach { add(it.pid()) } }
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
