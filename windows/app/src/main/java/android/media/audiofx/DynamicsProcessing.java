package android.media.audiofx;
/** The platform's per-session effect: none on the PC (the app's own processors do the sound). */
public final class DynamicsProcessing {
    public static final int VARIANT_FAVOR_FREQUENCY_RESOLUTION = 0, VARIANT_FAVOR_TIME_RESOLUTION = 1;
    public DynamicsProcessing(int priority, int session, Config cfg) { throw new UnsupportedOperationException("no audio effects on the PC"); }
    public void setPreEqBandAllChannelsTo(int band, EqBand b) {}
    public void setPreEqAllChannelsTo(Eq eq) {}
    public int setEnabled(boolean on) { return 0; }
    public boolean getEnabled() { return false; }
    public void release() {}
    public static class EqBand { public EqBand(boolean enabled, float cutoff, float gain) {} public void setGain(float g) {} public float getGain() { return 0; } public void setCutoffFrequency(float f) {} }
    public static class Eq { public Eq(boolean inUse, boolean enabled, int bands) {} public void setBand(int i, EqBand b) {} public EqBand getBand(int i) { return new EqBand(true, 0, 0); } }
    public static final class Config { public void setPreEqAllChannelsTo(Eq e) {} public static final class Builder { public Builder(int variant, int channels, boolean pre, int preBands, boolean mbc, int mbcBands, boolean post, int postBands, boolean limiter) {}
        public Builder setPreEqAllChannelsTo(Eq e) { return this; } public Builder setPreferredFrameDuration(float d) { return this; } public Config build() { return new Config(); } } }
}
