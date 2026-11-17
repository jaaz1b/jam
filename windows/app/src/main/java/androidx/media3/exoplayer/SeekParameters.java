package androidx.media3.exoplayer;

public final class SeekParameters {
    public static final SeekParameters EXACT = new SeekParameters(0, 0), DEFAULT = EXACT, CLOSEST_SYNC = new SeekParameters(Long.MAX_VALUE, Long.MAX_VALUE), PREVIOUS_SYNC = new SeekParameters(Long.MAX_VALUE, 0), NEXT_SYNC = new SeekParameters(0, Long.MAX_VALUE);
    public final long toleranceBeforeUs, toleranceAfterUs;
    public SeekParameters(long before, long after) { toleranceBeforeUs = before; toleranceAfterUs = after; }
}
