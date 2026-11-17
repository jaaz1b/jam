package android.media;

/** The PC's audio, as AudioManager tells it: one output (the default device), Jam's own media volume. */
public class AudioManager {
    public static final int STREAM_MUSIC = 3, STREAM_RING = 2, STREAM_NOTIFICATION = 5, STREAM_ALARM = 4, STREAM_VOICE_CALL = 0;
    public static final int GET_DEVICES_OUTPUTS = 2, GET_DEVICES_INPUTS = 1, GET_DEVICES_ALL = 3;
    public static final int MODE_NORMAL = 0, MODE_RINGTONE = 1, MODE_IN_CALL = 2, MODE_IN_COMMUNICATION = 3, MODE_CALL_SCREENING = 4, MODE_CALL_REDIRECT = 5, MODE_COMMUNICATION_REDIRECT = 6;
    public static final int FLAG_SHOW_UI = 1, FLAG_PLAY_SOUND = 4, ADJUST_RAISE = 1, ADJUST_LOWER = -1, ADJUST_SAME = 0, ADJUST_MUTE = -100, ADJUST_UNMUTE = 100, ADJUST_TOGGLE_MUTE = 101;
    public static final int AUDIOFOCUS_GAIN = 1, AUDIOFOCUS_LOSS = -1, AUDIOFOCUS_LOSS_TRANSIENT = -2, AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3, AUDIOFOCUS_REQUEST_GRANTED = 1, AUDIOFOCUS_REQUEST_FAILED = 0;
    public static final String ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY";
    public static final String PROPERTY_OUTPUT_SAMPLE_RATE = "android.media.property.OUTPUT_SAMPLE_RATE", PROPERTY_OUTPUT_FRAMES_PER_BUFFER = "android.media.property.OUTPUT_FRAMES_PER_BUFFER";
    public static final int MAX = 15;
    public int getStreamVolume(int stream) { return Math.round(app.jam.desktop.media.Volume.INSTANCE.getLevel() * MAX); }
    public int getStreamMaxVolume(int stream) { return MAX; }
    public int getStreamMinVolume(int stream) { return 0; }
    public void setStreamVolume(int stream, int index, int flags) { app.jam.desktop.media.Volume.INSTANCE.setLevel(Math.max(0, Math.min(MAX, index)) / (float) MAX); }
    public void adjustStreamVolume(int stream, int dir, int flags) { setStreamVolume(stream, getStreamVolume(stream) + (dir > 0 ? 1 : dir < 0 && dir > -2 ? -1 : 0), flags); }
    public boolean isStreamMute(int stream) { return getStreamVolume(stream) == 0; }
    public int getMode() { return MODE_NORMAL; }
    public boolean isMusicActive() { return false; }
    public boolean isBluetoothA2dpOn() { return false; }
    public boolean isWiredHeadsetOn() { return false; }
    public boolean isSpeakerphoneOn() { return true; }
    public String getProperty(String key) { return PROPERTY_OUTPUT_SAMPLE_RATE.equals(key) ? "48000" : PROPERTY_OUTPUT_FRAMES_PER_BUFFER.equals(key) ? "480" : null; }
    public AudioDeviceInfo[] getDevices(int flags) { return new AudioDeviceInfo[] { new AudioDeviceInfo(app.jam.desktop.media.Volume.INSTANCE.outputName(), AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) }; }
    public void registerAudioDeviceCallback(AudioDeviceCallback cb, android.os.Handler h) {}
    public void unregisterAudioDeviceCallback(AudioDeviceCallback cb) {}
    public int requestAudioFocus(Object req) { return AUDIOFOCUS_REQUEST_GRANTED; }
    public int requestAudioFocus(Object l, int stream, int hint) { return AUDIOFOCUS_REQUEST_GRANTED; }
    public int abandonAudioFocusRequest(Object req) { return AUDIOFOCUS_REQUEST_GRANTED; }
    public int abandonAudioFocus(Object l) { return AUDIOFOCUS_REQUEST_GRANTED; }
    public int generateAudioSessionId() { return (int) (System.nanoTime() & 0x7fff) + 1; }
    public void dispatchMediaKeyEvent(android.view.KeyEvent e) {}
    public void playSoundEffect(int t) {}
    public interface OnModeChangedListener { void onModeChanged(int mode); }
    public void addOnModeChangedListener(java.util.concurrent.Executor e, OnModeChangedListener l) {}
    public void removeOnModeChangedListener(OnModeChangedListener l) {}
}
