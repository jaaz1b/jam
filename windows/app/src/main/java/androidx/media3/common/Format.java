package androidx.media3.common;

public final class Format {
    public final String id, label, sampleMimeType, containerMimeType, codecs, language;
    public final int bitrate, averageBitrate, peakBitrate, sampleRate, channelCount, pcmEncoding, width, height; public final float frameRate, pixelWidthHeightRatio;
    private Format(Builder b) { id = b.id; label = b.label; sampleMimeType = b.sampleMimeType; containerMimeType = b.containerMimeType; codecs = b.codecs; language = b.language; bitrate = b.averageBitrate > 0 ? b.averageBitrate : b.peakBitrate; averageBitrate = b.averageBitrate; peakBitrate = b.peakBitrate;
        sampleRate = b.sampleRate; channelCount = b.channelCount; pcmEncoding = b.pcmEncoding; width = b.width; height = b.height; frameRate = b.frameRate; pixelWidthHeightRatio = 1f; }
    public Builder buildUpon() { Builder b = new Builder(); b.id = id; b.sampleMimeType = sampleMimeType; b.containerMimeType = containerMimeType; b.codecs = codecs; b.averageBitrate = averageBitrate; b.peakBitrate = peakBitrate; b.sampleRate = sampleRate; b.channelCount = channelCount; b.pcmEncoding = pcmEncoding; return b; }
    public static final int NO_VALUE = -1;
    public static final class Builder {
        String id, label, sampleMimeType, containerMimeType, codecs, language; int averageBitrate = -1, peakBitrate = -1, sampleRate = -1, channelCount = -1, pcmEncoding = -1, width = -1, height = -1; float frameRate = -1;
        public Builder setId(String v) { id = v; return this; } public Builder setLabel(String v) { label = v; return this; }
        public Builder setSampleMimeType(String v) { sampleMimeType = v; return this; } public Builder setContainerMimeType(String v) { containerMimeType = v; return this; }
        public Builder setCodecs(String v) { codecs = v; return this; } public Builder setLanguage(String v) { language = v; return this; }
        public Builder setAverageBitrate(int v) { averageBitrate = v; return this; } public Builder setPeakBitrate(int v) { peakBitrate = v; return this; }
        public Builder setSampleRate(int v) { sampleRate = v; return this; } public Builder setChannelCount(int v) { channelCount = v; return this; } public Builder setPcmEncoding(int v) { pcmEncoding = v; return this; }
        public Builder setWidth(int v) { width = v; return this; } public Builder setHeight(int v) { height = v; return this; } public Builder setFrameRate(float v) { frameRate = v; return this; }
        public Format build() { return new Format(this); }
    }
}
