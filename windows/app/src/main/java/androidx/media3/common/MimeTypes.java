package androidx.media3.common;

public final class MimeTypes {
    public static final String AUDIO_MPEG = "audio/mpeg", AUDIO_AAC = "audio/mp4a-latm", AUDIO_MP4 = "audio/mp4", AUDIO_OPUS = "audio/opus", AUDIO_VORBIS = "audio/vorbis", AUDIO_FLAC = "audio/flac", AUDIO_WAV = "audio/wav", AUDIO_OGG = "audio/ogg", AUDIO_WEBM = "audio/webm", AUDIO_RAW = "audio/raw";
    public static final String VIDEO_MP4 = "video/mp4", VIDEO_WEBM = "video/webm", VIDEO_H264 = "video/avc", VIDEO_VP9 = "video/x-vnd.on2.vp9", VIDEO_AV1 = "video/av01";
    public static final String APPLICATION_M3U8 = "application/x-mpegURL", APPLICATION_MPD = "application/dash+xml", APPLICATION_SS = "application/vnd.ms-sstr+xml", TEXT_VTT = "text/vtt";
    public static boolean isAudio(String m) { return m != null && m.startsWith("audio/"); }
    public static boolean isVideo(String m) { return m != null && m.startsWith("video/"); }
}
