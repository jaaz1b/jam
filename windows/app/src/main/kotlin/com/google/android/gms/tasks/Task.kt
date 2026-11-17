package com.google.android.gms.tasks

/** Play services' Task, as the app uses it: listeners told on the main thread once it ends. */
open class Task<T> {
    private var done = false; private var value: T? = null; private var error: Exception? = null; private var canceled = false
    private val ok = ArrayList<(T) -> Unit>(); private val bad = ArrayList<(Exception) -> Unit>(); private val cancel = ArrayList<() -> Unit>(); private val any = ArrayList<(Task<T>) -> Unit>()
    val isComplete: Boolean get() = done
    val isSuccessful: Boolean get() = done && error == null && !canceled
    val isCanceled: Boolean get() = canceled
    val result: T? get() = value
    val exception: Exception? get() = error
    @Synchronized fun addOnSuccessListener(l: (T) -> Unit): Task<T> { if (done) { if (isSuccessful) post { l(value as T) } } else ok += l; return this }
    @Synchronized fun addOnFailureListener(l: (Exception) -> Unit): Task<T> { if (done) { error?.let { e -> post { l(e) } } } else bad += l; return this }
    @Synchronized fun addOnCanceledListener(l: () -> Unit): Task<T> { if (done) { if (canceled) post(l) } else cancel += l; return this }
    @Synchronized fun addOnCompleteListener(l: (Task<T>) -> Unit): Task<T> { if (done) post { l(this) } else any += l; return this }
    @Synchronized fun finish(v: T?, e: Exception?, c: Boolean) {
        if (done) return
        done = true; value = v; error = e; canceled = c
        post { when { c -> cancel.forEach { it() }; e != null -> bad.forEach { it(e) }; else -> ok.forEach { it(v as T) } }; any.forEach { it(this) } }
    }
    private fun post(b: () -> Unit) = app.jam.desktop.shim.Desk.post { b() }
}
class TaskCompletionSource<T> {
    val task = Task<T>()
    fun setResult(v: T) = task.finish(v, null, false)
    fun trySetResult(v: T): Boolean { setResult(v); return true }
    fun setException(e: Exception) = task.finish(null, e, false)
    fun trySetException(e: Exception): Boolean { setException(e); return true }
}
