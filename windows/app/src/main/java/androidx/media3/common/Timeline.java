package androidx.media3.common;

public abstract class Timeline {
    public static final Timeline EMPTY = new Timeline() { public int getWindowCount() { return 0; } public Window getWindow(int i, Window w, long p) { return w; } public int getPeriodCount() { return 0; } public Period getPeriod(int i, Period p, boolean s) { return p; } };
    public abstract int getWindowCount();
    public abstract Window getWindow(int index, Window window, long defaultPositionProjectionUs);
    public Window getWindow(int index, Window window) { return getWindow(index, window, 0); }
    public abstract int getPeriodCount();
    public abstract Period getPeriod(int index, Period period, boolean setIds);
    public boolean isEmpty() { return getWindowCount() == 0; }
    public static final class Window {
        public MediaItem mediaItem = MediaItem.EMPTY; public long durationUs = C.TIME_UNSET, defaultPositionUs; public boolean isSeekable = true, isDynamic, isLive, isPlaceholder; public Object uid = new Object(); public int firstPeriodIndex, lastPeriodIndex;
        public long getDurationMs() { return durationUs == C.TIME_UNSET ? C.TIME_UNSET : durationUs / 1000; }
        public boolean isLive() { return isLive; }
    }
    public static final class Period { public long durationUs = C.TIME_UNSET; public int windowIndex; public Object uid, id; public long getDurationMs() { return durationUs == C.TIME_UNSET ? C.TIME_UNSET : durationUs / 1000; } }
}
