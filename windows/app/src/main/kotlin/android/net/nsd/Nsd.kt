package android.net.nsd

/** Network service discovery (cast devices, Jam devices on the LAN): no results on the PC yet. */
class NsdManager {
    fun discoverServices(type: String, protocol: Int, listener: DiscoveryListener) { listener.onDiscoveryStarted(type) }
    fun stopServiceDiscovery(listener: DiscoveryListener) { runCatching { listener.onDiscoveryStopped("") } }
    fun resolveService(info: NsdServiceInfo, listener: ResolveListener) { listener.onResolveFailed(info, FAILURE_INTERNAL_ERROR) }
    fun registerService(info: NsdServiceInfo, protocol: Int, listener: RegistrationListener) { listener.onRegistrationFailed(info, FAILURE_INTERNAL_ERROR) }
    fun unregisterService(listener: RegistrationListener) {}
    fun registerServiceInfoCallback(info: NsdServiceInfo, exec: java.util.concurrent.Executor, cb: ServiceInfoCallback) {}
    fun unregisterServiceInfoCallback(cb: ServiceInfoCallback) {}
    interface DiscoveryListener {
        fun onStartDiscoveryFailed(serviceType: String, errorCode: Int)
        fun onStopDiscoveryFailed(serviceType: String, errorCode: Int)
        fun onDiscoveryStarted(serviceType: String)
        fun onDiscoveryStopped(serviceType: String)
        fun onServiceFound(serviceInfo: NsdServiceInfo)
        fun onServiceLost(serviceInfo: NsdServiceInfo)
    }
    interface ResolveListener { fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int); fun onServiceResolved(serviceInfo: NsdServiceInfo); fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}; fun onResolutionStopped(serviceInfo: NsdServiceInfo) {} }
    interface RegistrationListener { fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int); fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int); fun onServiceRegistered(serviceInfo: NsdServiceInfo); fun onServiceUnregistered(serviceInfo: NsdServiceInfo) }
    interface ServiceInfoCallback { fun onServiceInfoCallbackRegistrationFailed(errorCode: Int); fun onServiceUpdated(serviceInfo: NsdServiceInfo); fun onServiceLost(); fun onServiceInfoCallbackUnregistered() }
    companion object { const val PROTOCOL_DNS_SD = 1; const val FAILURE_INTERNAL_ERROR = 0; const val FAILURE_ALREADY_ACTIVE = 3; const val FAILURE_MAX_LIMIT = 4; const val FAILURE_OPERATION_NOT_RUNNING = 5 }
}
class NsdServiceInfo() {
    var serviceName: String = ""; var serviceType: String = ""; var host: java.net.InetAddress? = null; var port: Int = 0
    val hostAddresses: List<java.net.InetAddress> get() = listOfNotNull(host)
    private val attrs = HashMap<String, ByteArray?>()
    val attributes: Map<String, ByteArray?> get() = attrs
    fun setAttribute(k: String, v: String?) { attrs[k] = v?.toByteArray() }
    fun removeAttribute(k: String) { attrs.remove(k) }
}
