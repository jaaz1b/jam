package android.media;
public final class MediaCodecInfo {
    public String getName() { return "jam.ffmpeg"; }
    public boolean isEncoder() { return false; }
    public boolean isHardwareAccelerated() { return false; }
    public boolean isSoftwareOnly() { return true; }
    public String[] getSupportedTypes() { return new String[] { "audio/raw", "audio/mpeg", "audio/mp4a-latm", "audio/opus", "audio/vorbis", "audio/flac" }; }
    public CodecCapabilities getCapabilitiesForType(String t) { return new CodecCapabilities(); }
    public static final class CodecCapabilities { public AudioCapabilities getAudioCapabilities() { return new AudioCapabilities(); } public CodecProfileLevel[] profileLevels = new CodecProfileLevel[0]; }
    public static final class AudioCapabilities { public int[] getSupportedSampleRates() { return new int[] { 44100, 48000 }; } public int getMaxInputChannelCount() { return 8; } public android.util.Range<Integer> getBitrateRange() { return new android.util.Range<>(8000, 512000); } }
    public static final class CodecProfileLevel { public static final int AACObjectLC = 2, AACObjectHE = 5; public int profile, level; }
}
