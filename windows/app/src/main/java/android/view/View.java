package android.view;

import android.content.Context;
import androidx.compose.runtime.MutableState;
import androidx.compose.runtime.SnapshotStateKt;

/**
 * Android's View on the PC, as far as the app uses it: a node of the window's view tree whose
 * visibility, alpha and children are Compose state, drawn by app.jam.desktop.ViewTree. The kinds
 * that draw something are ComposeView (the app's UI) and WebView (pages, from the hidden browser).
 * Java, like Android's: the app's subclasses override its getters (getVisibility...).
 */
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8, NO_ID = -1;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_AUTO = 0, IMPORTANT_FOR_ACCESSIBILITY_YES = 1, IMPORTANT_FOR_ACCESSIBILITY_NO = 2, IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 4;
    public static final int LAYER_TYPE_NONE = 0, LAYER_TYPE_SOFTWARE = 1, LAYER_TYPE_HARDWARE = 2;
    public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 256, SYSTEM_UI_FLAG_FULLSCREEN = 4, SYSTEM_UI_FLAG_HIDE_NAVIGATION = 2, SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 4096;
    public static final int OVER_SCROLL_NEVER = 2, FOCUS_DOWN = 130, FOCUS_UP = 33, FOCUS_LEFT = 17, FOCUS_RIGHT = 66;
    private static final java.util.concurrent.atomic.AtomicInteger IDS = new java.util.concurrent.atomic.AtomicInteger(0x1000);
    public static int generateViewId() { return IDS.incrementAndGet(); }

    private final Context context;
    private final MutableState<Integer> visibility = SnapshotStateKt.mutableStateOf(VISIBLE, SnapshotStateKt.structuralEqualityPolicy());
    private final MutableState<Float> alpha = SnapshotStateKt.mutableStateOf(1f, SnapshotStateKt.structuralEqualityPolicy());
    ViewParent parent;
    private ViewGroup.LayoutParams layoutParams;
    private int id = NO_ID;
    private Object tag;
    private boolean focusable, focusableInTouchMode, clickable, enabled = true, keepScreenOn, soundEffects = true, attached;
    private int importantForAccessibility;
    private CharSequence contentDescription;
    private float translationX, translationY, scaleX = 1f, scaleY = 1f, elevation;
    private int backgroundColor;
    private final ViewTreeObserver observer = new ViewTreeObserver();
    private int width, height;

    public View(Context context) { this.context = context; }
    public final Context getContext() { return context; }

    public int getVisibility() { return visibility.getValue(); }
    public void setVisibility(int v) {
        if (visibility.getValue() == v) return;
        visibility.setValue(v);
        onVisibilityChanged(this, v);
    }
    /** What the window draws by (the state, not the overridable getter). */
    public final int drawnVisibility() { return visibility.getValue(); }
    public int getWindowVisibility() { return attached ? VISIBLE : GONE; }
    protected void onVisibilityChanged(View changed, int visibility) {}
    protected void onWindowVisibilityChanged(int visibility) {}
    public float getAlpha() { return alpha.getValue(); }
    public void setAlpha(float a) { alpha.setValue(a); }
    public final ViewParent getParent() { return parent; }
    public ViewGroup.LayoutParams getLayoutParams() { return layoutParams; }
    public void setLayoutParams(ViewGroup.LayoutParams p) { layoutParams = p; }
    public int getId() { return id; }
    public void setId(int i) { id = i; }
    public Object getTag() { return tag; }
    public void setTag(Object t) { tag = t; }
    public boolean isFocusable() { return focusable; }
    public void setFocusable(boolean f) { focusable = f; }
    public boolean isFocusableInTouchMode() { return focusableInTouchMode; }
    public void setFocusableInTouchMode(boolean f) { focusableInTouchMode = f; }
    public boolean isClickable() { return clickable; }
    public void setClickable(boolean c) { clickable = c; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { enabled = e; }
    public boolean getKeepScreenOn() { return keepScreenOn; }
    public void setKeepScreenOn(boolean k) { keepScreenOn = k; }
    public boolean isSoundEffectsEnabled() { return soundEffects; }
    public void setSoundEffectsEnabled(boolean s) { soundEffects = s; }
    public int getImportantForAccessibility() { return importantForAccessibility; }
    public void setImportantForAccessibility(int i) { importantForAccessibility = i; }
    public CharSequence getContentDescription() { return contentDescription; }
    public void setContentDescription(CharSequence c) { contentDescription = c; }
    public float getTranslationX() { return translationX; } public void setTranslationX(float v) { translationX = v; }
    public float getTranslationY() { return translationY; } public void setTranslationY(float v) { translationY = v; }
    public float getScaleX() { return scaleX; } public void setScaleX(float v) { scaleX = v; }
    public float getScaleY() { return scaleY; } public void setScaleY(float v) { scaleY = v; }
    public float getElevation() { return elevation; } public void setElevation(float v) { elevation = v; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    /** The window says how big the view is drawn (pixels). */
    public void setDrawnSize(int w, int h) { width = w; height = h; }
    public boolean isInEditMode() { return false; }
    public boolean isAttachedToWindow() { return attached; }
    public boolean hasWindowFocus() { return attached; }
    public boolean isShown() { return getVisibility() == VISIBLE && attached; }
    public View getRootView() { return parent instanceof View ? ((View) parent).getRootView() : this; }
    public android.os.Handler getHandler() { return new android.os.Handler(android.os.Looper.getMainLooper()); }
    public ViewTreeObserver getViewTreeObserver() { return observer; }
    public Display getDisplay() { return Display.Companion.getDEFAULT(); }
    public android.content.res.Resources getResources() { return context.getResources(); }
    public android.os.IBinder getWindowToken() { return null; }
    public void setBackgroundColor(int c) { backgroundColor = c; }
    public int backgroundColor() { return backgroundColor; }
    public void setLayerType(int t, android.graphics.Paint p) {}
    public void setOnTouchListener(OnTouchListener l) {}
    public void setOnClickListener(OnClickListener l) {}
    public void setOnKeyListener(OnKeyListener l) {}
    public void setPadding(int l, int t, int r, int b) {}
    public boolean requestFocus() { return true; }
    public boolean requestFocus(int direction) { return true; }
    public void clearFocus() {}
    public void requestLayout() {}
    public void invalidate() {}
    public void postInvalidateOnAnimation() {}
    public boolean post(Runnable r) { app.jam.desktop.shim.Desk.INSTANCE.post(r); return true; }
    public boolean postDelayed(Runnable r, long ms) { return getHandler().postDelayed(r, ms); }
    public boolean removeCallbacks(Runnable r) { return true; }
    public boolean performHapticFeedback(int c) { return false; }
    public boolean performHapticFeedback(int c, int f) { return false; }
    public void playSoundEffect(int s) {}
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener l) {}
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l) {}
    public void setOnApplyWindowInsetsListener(Object l) {}
    public void getLocationOnScreen(int[] out) { out[0] = 0; out[1] = 0; }
    public void getLocationInWindow(int[] out) { out[0] = 0; out[1] = 0; }
    public ViewPropertyAnimator animate() { return new ViewPropertyAnimator(this); }
    public <T extends View> T findViewById(int id) { return null; }
    public boolean dispatchKeyEvent(KeyEvent e) { return false; }
    public void setSystemGestureExclusionRects(java.util.List<android.graphics.Rect> r) {}
    public void setWillNotDraw(boolean b) {}
    public void setDescendantFocusabilityCompat(int f) {}
    public boolean isHardwareAccelerated() { return true; }
    protected void onAttachedToWindow() {}
    protected void onDetachedFromWindow() {}
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {}

    /** The view joins / leaves the window (its parent chain reached the activity's content). */
    void dispatchAttach(boolean on) {
        if (attached == on) return;
        attached = on;
        if (on) { onAttachedToWindow(); onWindowVisibilityChanged(VISIBLE); }
        else { onWindowVisibilityChanged(GONE); onDetachedFromWindow(); }
    }
    /** The activity's content view: attached to the window from now on. */
    public final void attachAsRoot(boolean on) { dispatchAttach(on); }

    public interface OnTouchListener { boolean onTouch(View v, MotionEvent e); }
    public interface OnClickListener { void onClick(View v); }
    public interface OnKeyListener { boolean onKey(View v, int keyCode, KeyEvent e); }
    public interface OnAttachStateChangeListener { void onViewAttachedToWindow(View v); void onViewDetachedFromWindow(View v); }
}
