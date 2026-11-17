package android.media;

import java.util.HashMap;

public final class MediaFormat {
    public static final String KEY_MIME = "mime", KEY_SAMPLE_RATE = "sample-rate", KEY_CHANNEL_COUNT = "channel-count", KEY_DURATION = "durationUs", KEY_PCM_ENCODING = "pcm-encoding";
    public static final String KEY_BIT_RATE = "bitrate", KEY_MAX_INPUT_SIZE = "max-input-size", KEY_AAC_PROFILE = "aac-profile", KEY_CHANNEL_MASK = "channel-mask", KEY_LANGUAGE = "language", KEY_IS_ADTS = "is-adts";
    public static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm", MIMETYPE_AUDIO_RAW = "audio/raw", MIMETYPE_AUDIO_OPUS = "audio/opus", MIMETYPE_AUDIO_MPEG = "audio/mpeg", MIMETYPE_AUDIO_VORBIS = "audio/vorbis", MIMETYPE_AUDIO_FLAC = "audio/flac";
    private final HashMap<String, Object> map = new HashMap<>();
    public MediaFormat() {}
    public static MediaFormat createAudioFormat(String mime, int rate, int channels) { MediaFormat f = new MediaFormat(); f.setString(KEY_MIME, mime); f.setInteger(KEY_SAMPLE_RATE, rate); f.setInteger(KEY_CHANNEL_COUNT, channels); return f; }
    public boolean containsKey(String k) { return map.containsKey(k); }
    public int getInteger(String k) { Object v = map.get(k); if (v == null) throw new NullPointerException(k); return ((Number) v).intValue(); }
    public int getInteger(String k, int d) { Object v = map.get(k); return v == null ? d : ((Number) v).intValue(); }
    public long getLong(String k) { Object v = map.get(k); if (v == null) throw new NullPointerException(k); return ((Number) v).longValue(); }
    public long getLong(String k, long d) { Object v = map.get(k); return v == null ? d : ((Number) v).longValue(); }
    public float getFloat(String k) { return ((Number) map.get(k)).floatValue(); }
    public String getString(String k) { Object v = map.get(k); return v == null ? null : v.toString(); }
    public String getString(String k, String d) { Object v = map.get(k); return v == null ? d : v.toString(); }
    public java.nio.ByteBuffer getByteBuffer(String k) { return (java.nio.ByteBuffer) map.get(k); }
    public void setInteger(String k, int v) { map.put(k, v); }
    public void setLong(String k, long v) { map.put(k, v); }
    public void setFloat(String k, float v) { map.put(k, v); }
    public void setString(String k, String v) { map.put(k, v); }
    public void setByteBuffer(String k, java.nio.ByteBuffer v) { map.put(k, v); }
    public void removeKey(String k) { map.remove(k); }
    @Override public String toString() { return map.toString(); }
}
