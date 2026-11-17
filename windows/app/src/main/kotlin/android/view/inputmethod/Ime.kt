package android.view.inputmethod
class InputMethodManager { fun hideSoftInputFromWindow(t: android.os.IBinder?, f: Int): Boolean = true; fun showSoftInput(v: android.view.View?, f: Int): Boolean = true; fun isActive(): Boolean = false
    companion object { const val HIDE_NOT_ALWAYS = 2; const val SHOW_IMPLICIT = 1 } }
class EditorInfo { companion object { const val IME_ACTION_SEARCH = 3; const val IME_ACTION_DONE = 6; const val IME_ACTION_GO = 2 } }
