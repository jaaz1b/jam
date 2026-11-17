package android.view;

import android.content.Context;
import androidx.compose.runtime.SnapshotStateKt;
import androidx.compose.runtime.snapshots.SnapshotStateList;

public class ViewGroup extends View implements ViewParent {
    public static final int FOCUS_BEFORE_DESCENDANTS = 0x20000, FOCUS_AFTER_DESCENDANTS = 0x40000, FOCUS_BLOCK_DESCENDANTS = 0x60000;
    /** The children, as Compose state (the window redraws when one comes or goes). */
    public final SnapshotStateList<View> children = SnapshotStateKt.mutableStateListOf();
    private int descendantFocusability = FOCUS_AFTER_DESCENDANTS;
    private boolean clipChildren = true, clipToPadding = true;

    public ViewGroup(Context context) { super(context); }
    public int getDescendantFocusability() { return descendantFocusability; }
    public void setDescendantFocusability(int f) { descendantFocusability = f; }
    public void setClipChildren(boolean c) { clipChildren = c; }
    public void setClipToPadding(boolean c) { clipToPadding = c; }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int i) { return i >= 0 && i < children.size() ? children.get(i) : null; }
    public int indexOfChild(View v) { return children.indexOf(v); }
    public void addView(View v) { addView(v, -1); }
    public void addView(View v, int index) {
        if (v.parent instanceof ViewGroup) ((ViewGroup) v.parent).removeView(v);
        v.parent = this;
        if (index < 0 || index > children.size()) children.add(v); else children.add(index, v);
        if (isAttachedToWindow()) v.dispatchAttach(true);
    }
    public void addView(View v, LayoutParams p) { v.setLayoutParams(p); addView(v); }
    public void addView(View v, int index, LayoutParams p) { v.setLayoutParams(p); addView(v, index); }
    public void addView(View v, int w, int h) { v.setLayoutParams(new LayoutParams(w, h)); addView(v); }
    public void removeView(View v) { if (children.remove(v)) { v.parent = null; v.dispatchAttach(false); } }
    public void removeViewAt(int i) { View v = getChildAt(i); if (v != null) removeView(v); }
    public void removeAllViews() { for (View v : new java.util.ArrayList<>(children)) removeView(v); }
    @Override void dispatchAttach(boolean on) {
        super.dispatchAttach(on);
        for (View v : new java.util.ArrayList<>(children)) v.dispatchAttach(on);
    }

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, WRAP_CONTENT = -2, FILL_PARENT = -1;
        public int width, height;
        public LayoutParams(int w, int h) { width = w; height = h; }
        public LayoutParams(LayoutParams src) { this(src.width, src.height); }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public MarginLayoutParams(int w, int h) { super(w, h); }
        public MarginLayoutParams(LayoutParams src) { super(src); }
        public void setMargins(int l, int t, int r, int b) { leftMargin = l; topMargin = t; rightMargin = r; bottomMargin = b; }
    }
}
