package android.telephony
/** No phone on a PC: no calls, no cellular network. */
class TelephonyManager {
    @Suppress("DEPRECATION") fun listen(l: PhoneStateListener?, events: Int) {}
    fun registerTelephonyCallback(e: java.util.concurrent.Executor, cb: TelephonyCallback) {}
    fun unregisterTelephonyCallback(cb: TelephonyCallback) {}
    val callState: Int get() = CALL_STATE_IDLE
    val networkOperatorName: String get() = ""
    val simCountryIso: String get() = ""
    val networkCountryIso: String get() = ""
    val dataNetworkType: Int get() = 0
    companion object { const val CALL_STATE_IDLE = 0; const val CALL_STATE_RINGING = 1; const val CALL_STATE_OFFHOOK = 2 }
}
open class PhoneStateListener { open fun onCallStateChanged(state: Int, number: String?) {}; companion object { const val LISTEN_CALL_STATE = 32; const val LISTEN_NONE = 0 } }
open class TelephonyCallback { interface CallStateListener { fun onCallStateChanged(state: Int) } }
