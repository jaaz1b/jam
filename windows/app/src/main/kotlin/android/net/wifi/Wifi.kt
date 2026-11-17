package android.net.wifi

class WifiManager {
    fun createMulticastLock(tag: String) = MulticastLock()
    fun createWifiLock(mode: Int, tag: String) = WifiLock()
    val isWifiEnabled: Boolean get() = true
    val connectionInfo: WifiInfo get() = WifiInfo()
    class MulticastLock { private var held = false; fun acquire() { held = true }; fun release() { held = false }; val isHeld: Boolean get() = held; fun setReferenceCounted(b: Boolean) {} }
    class WifiLock { private var held = false; fun acquire() { held = true }; fun release() { held = false }; val isHeld: Boolean get() = held; fun setReferenceCounted(b: Boolean) {} }
    companion object { const val WIFI_MODE_FULL_HIGH_PERF = 3; const val WIFI_MODE_FULL_LOW_LATENCY = 4; const val WIFI_MODE_FULL = 1 }
}
class WifiInfo { val ssid: String = "<unknown ssid>"; val rssi: Int = -50; val linkSpeed: Int = 1000; val ipAddress: Int = 0; val frequency: Int = 5000 }
