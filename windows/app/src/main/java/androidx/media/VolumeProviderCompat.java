package androidx.media;

public abstract class VolumeProviderCompat {
    public static final int VOLUME_CONTROL_FIXED = 0, VOLUME_CONTROL_RELATIVE = 1, VOLUME_CONTROL_ABSOLUTE = 2;
    private int current; private final int control, max;
    public VolumeProviderCompat(int control, int max, int current) { this.control = control; this.max = max; this.current = current; }
    public final int getCurrentVolume() { return current; } public final void setCurrentVolume(int v) { current = v; } public final int getMaxVolume() { return max; } public final int getVolumeControl() { return control; }
    public void onSetVolumeTo(int volume) {} public void onAdjustVolume(int direction) {}
}
