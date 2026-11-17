package android.net

/** The PC is always on an unmetered, validated network (Wi-Fi-like) for the app's purposes. */
class ConnectivityManager {
    val activeNetwork: Network? get() = Network.DEFAULT
    val activeNetworkInfo: NetworkInfo? get() = NetworkInfo()
    val allNetworks: Array<Network> get() = arrayOf(Network.DEFAULT)
    val isActiveNetworkMetered: Boolean get() = false
    val restrictBackgroundStatus: Int get() = RESTRICT_BACKGROUND_STATUS_DISABLED
    val boundNetworkForProcess: Network? get() = null
    fun getNetworkCapabilities(n: Network?): NetworkCapabilities? = if (online()) NetworkCapabilities() else null
    fun getLinkProperties(n: Network?): LinkProperties? = LinkProperties()
    fun getNetworkInfo(n: Network?): NetworkInfo? = NetworkInfo()
    fun registerDefaultNetworkCallback(cb: NetworkCallback) {}
    fun registerDefaultNetworkCallback(cb: NetworkCallback, h: android.os.Handler?) {}
    fun registerNetworkCallback(req: NetworkRequest, cb: NetworkCallback) {}
    fun registerNetworkCallback(req: NetworkRequest, cb: NetworkCallback, h: android.os.Handler?) {}
    fun requestNetwork(req: NetworkRequest, cb: NetworkCallback) {}
    fun unregisterNetworkCallback(cb: NetworkCallback) {}
    fun bindProcessToNetwork(n: Network?): Boolean = true
    open class NetworkCallback {
        open fun onAvailable(network: Network) {}
        open fun onLost(network: Network) {}
        open fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {}
        open fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {}
        open fun onUnavailable() {}
        open fun onBlockedStatusChanged(network: Network, blocked: Boolean) {}
    }
    companion object {
        const val TYPE_WIFI = 1; const val TYPE_MOBILE = 0; const val TYPE_ETHERNET = 9
        const val RESTRICT_BACKGROUND_STATUS_DISABLED = 1; const val RESTRICT_BACKGROUND_STATUS_WHITELISTED = 2; const val RESTRICT_BACKGROUND_STATUS_ENABLED = 3
        const val CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE"
        fun online(): Boolean = runCatching { java.net.NetworkInterface.getNetworkInterfaces().toList().any { it.isUp && !it.isLoopback } }.getOrDefault(true)
    }
}
class Network private constructor(val id: Int) { companion object { val DEFAULT = Network(100) }
    val socketFactory: javax.net.SocketFactory get() = javax.net.SocketFactory.getDefault()
    fun bindSocket(s: java.net.Socket) {}
    fun openConnection(url: java.net.URL): java.net.URLConnection = url.openConnection()
    fun getAllByName(host: String): Array<java.net.InetAddress> = java.net.InetAddress.getAllByName(host) }
class NetworkInfo { val isConnected: Boolean get() = ConnectivityManager.online(); val isConnectedOrConnecting: Boolean get() = isConnected; val type: Int get() = ConnectivityManager.TYPE_WIFI; val typeName = "WIFI" }
class LinkProperties { val dnsServers: List<java.net.InetAddress> = emptyList(); val interfaceName: String? = null; val linkAddresses: List<Any> = emptyList(); val isPrivateDnsActive = false; val privateDnsServerName: String? = null }
class NetworkCapabilities {
    fun hasTransport(t: Int): Boolean = t == TRANSPORT_WIFI || t == TRANSPORT_ETHERNET
    fun hasCapability(c: Int): Boolean = c != NET_CAPABILITY_NOT_ROAMING || true
    val linkDownstreamBandwidthKbps: Int get() = 100_000
    val linkUpstreamBandwidthKbps: Int get() = 20_000
    val signalStrength: Int get() = -50
    companion object {
        const val TRANSPORT_CELLULAR = 0; const val TRANSPORT_WIFI = 1; const val TRANSPORT_BLUETOOTH = 2; const val TRANSPORT_ETHERNET = 3; const val TRANSPORT_VPN = 4
        const val NET_CAPABILITY_INTERNET = 12; const val NET_CAPABILITY_VALIDATED = 16; const val NET_CAPABILITY_NOT_METERED = 11; const val NET_CAPABILITY_NOT_ROAMING = 18
        const val NET_CAPABILITY_NOT_VPN = 15; const val NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25; const val NET_CAPABILITY_CAPTIVE_PORTAL = 17; const val NET_CAPABILITY_NOT_SUSPENDED = 21
    }
}
class NetworkRequest private constructor() { class Builder { fun addCapability(c: Int) = this; fun addTransportType(t: Int) = this; fun removeCapability(c: Int) = this; fun build() = NetworkRequest() } }
object TrafficStats { @JvmStatic fun getUidRxBytes(uid: Int): Long = 0; @JvmStatic fun getUidTxBytes(uid: Int): Long = 0; @JvmStatic fun getTotalRxBytes(): Long = 0
    @JvmStatic fun setThreadStatsTag(t: Int) {}; @JvmStatic fun clearThreadStatsTag() {}; @JvmStatic fun tagSocket(s: java.net.Socket) {} ; @JvmStatic fun getThreadStatsTag() = 0 }
object Proxy { const val PROXY_CHANGE_ACTION = "android.intent.action.PROXY_CHANGE" }
