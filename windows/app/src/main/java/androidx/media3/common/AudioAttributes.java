package androidx.media3.common;

public final class AudioAttributes {
    public static final AudioAttributes DEFAULT = new Builder().build();
    public final int contentType, usage, flags, allowedCapturePolicy, spatializationBehavior;
    private AudioAttributes(int ct, int u) { contentType = ct; usage = u; flags = 0; allowedCapturePolicy = 1; spatializationBehavior = 0; }
    public static final class Builder {
        private int ct, u = 1;
        public Builder setContentType(int c) { ct = c; return this; }
        public Builder setUsage(int x) { u = x; return this; }
        public Builder setFlags(int f) { return this; }
        public Builder setAllowedCapturePolicy(int p) { return this; }
        public Builder setSpatializationBehavior(int b) { return this; }
        public AudioAttributes build() { return new AudioAttributes(ct, u); }
    }
}
