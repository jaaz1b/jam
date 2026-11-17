package android.media;
public final class AudioDeviceInfo {
    public static final int TYPE_UNKNOWN = 0, TYPE_BUILTIN_EARPIECE = 1, TYPE_BUILTIN_SPEAKER = 2, TYPE_WIRED_HEADSET = 3, TYPE_WIRED_HEADPHONES = 4, TYPE_BLUETOOTH_SCO = 7, TYPE_BLUETOOTH_A2DP = 8, TYPE_HDMI = 9;
    public static final int TYPE_USB_DEVICE = 11, TYPE_USB_ACCESSORY = 12, TYPE_USB_HEADSET = 22, TYPE_HEARING_AID = 23, TYPE_BLE_HEADSET = 26, TYPE_BLE_SPEAKER = 27, TYPE_REMOTE_SUBMIX = 25, TYPE_BUILTIN_SPEAKER_SAFE = 24, TYPE_DOCK = 13, TYPE_LINE_ANALOG = 5, TYPE_LINE_DIGITAL = 6, TYPE_BLE_BROADCAST = 30;
    private final String name; private final int type;
    public AudioDeviceInfo(String name, int type) { this.name = name; this.type = type; }
    public int getType() { return type; }
    public CharSequence getProductName() { return name; }
    public int getId() { return 1; }
    public boolean isSink() { return true; }
    public boolean isSource() { return false; }
    public String getAddress() { return ""; }
    public int[] getSampleRates() { return new int[] { 48000 }; }
}
