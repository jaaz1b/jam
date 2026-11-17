package app.jam.desktop.security

import android.security.keystore.KeyGenParameterSpec
import app.jam.desktop.shim.Desk
import org.json.JSONObject
import java.io.File
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGeneratorSpi
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * "AndroidKeyStore" on the PC: keys made by alias (KeyGenerator AES, KeyPairGenerator EC) kept in Jam's
 * data folder, protected by Windows for this user (DPAPI) so another account of the PC cannot read them.
 */
object AndroidKeyStore {
    const val NAME = "AndroidKeyStore"
    fun install() { if (java.security.Security.getProvider(NAME) == null) java.security.Security.addProvider(KeyStoreProvider()) }

    private val file get() = File(Desk.dataDir, "keystore.json")
    @Synchronized fun load(): JSONObject = runCatching { JSONObject(String(unprotect(file.readBytes()))) }.getOrElse { JSONObject() }
    @Synchronized fun save(o: JSONObject) { val tmp = File(file.path + ".tmp"); tmp.writeBytes(protect(o.toString().toByteArray())); tmp.renameTo(file).let { if (!it) { file.delete(); tmp.renameTo(file) } } }
    private fun protect(b: ByteArray): ByteArray = if (Desk.isWindows) com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(b) else b
    private fun unprotect(b: ByteArray): ByteArray = if (Desk.isWindows) com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(b) else b

    fun put(alias: String, entry: JSONObject) { val o = load(); o.put(alias, entry); save(o) }
    fun get(alias: String): JSONObject? = load().optJSONObject(alias)
    fun remove(alias: String) { val o = load(); o.remove(alias); save(o) }

    fun secret(alias: String): SecretKey? = get(alias)?.takeIf { it.optString("t") == "aes" }?.let { SecretKeySpec(Base64.getDecoder().decode(it.getString("k")), "AES") }
    fun pair(alias: String): KeyPair? = get(alias)?.takeIf { it.optString("t") == "ec" }?.let {
        val f = KeyFactory.getInstance("EC")
        KeyPair(f.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(it.getString("pub")))), f.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(it.getString("priv")))))
    }
}

class KeyStoreProvider : Provider(AndroidKeyStore.NAME, "1.0", "Jam for Windows: app keys protected by Windows") {
    init {
        put("KeyStore.AndroidKeyStore", Store::class.java.name)
        put("KeyGenerator.AES", AesGen::class.java.name)
        put("KeyPairGenerator.EC", EcGen::class.java.name)
    }
}

class Store : KeyStoreSpi() {
    override fun engineGetKey(alias: String, password: CharArray?): Key? = AndroidKeyStore.secret(alias) ?: AndroidKeyStore.pair(alias)?.private
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = AndroidKeyStore.pair(alias)?.let { arrayOf(PublicKeyCertificate(it.public)) }
    override fun engineGetCertificate(alias: String): Certificate? = AndroidKeyStore.pair(alias)?.let { PublicKeyCertificate(it.public) }
    override fun engineGetCreationDate(alias: String): Date? = AndroidKeyStore.get(alias)?.let { Date(it.optLong("at")) }
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) { throw UnsupportedOperationException() }
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) { throw UnsupportedOperationException() }
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) { throw UnsupportedOperationException() }
    override fun engineDeleteEntry(alias: String) = AndroidKeyStore.remove(alias)
    override fun engineAliases(): Enumeration<String> = java.util.Collections.enumeration(AndroidKeyStore.load().keySet().toList())
    override fun engineContainsAlias(alias: String): Boolean = AndroidKeyStore.get(alias) != null
    override fun engineSize(): Int = AndroidKeyStore.load().length()
    override fun engineIsKeyEntry(alias: String): Boolean = engineContainsAlias(alias)
    override fun engineIsCertificateEntry(alias: String): Boolean = false
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineStore(stream: java.io.OutputStream?, password: CharArray?) {}
    override fun engineLoad(stream: java.io.InputStream?, password: CharArray?) {}
}

/** The public half of a key pair, as the certificate Android's keystore would give for it. */
class PublicKeyCertificate(private val key: java.security.PublicKey) : Certificate("X.509") {
    override fun getEncoded(): ByteArray = key.encoded
    override fun verify(key: java.security.PublicKey?) {}
    override fun verify(key: java.security.PublicKey?, sigProvider: String?) {}
    override fun toString(): String = "PublicKeyCertificate(${key.algorithm})"
    override fun getPublicKey(): java.security.PublicKey = key
}

class AesGen : KeyGeneratorSpi() {
    private var spec: KeyGenParameterSpec? = null; private var size = 256
    override fun engineInit(random: SecureRandom?) {}
    override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) { spec = params as? KeyGenParameterSpec; spec?.keySize?.takeIf { it > 0 }?.let { size = it } }
    override fun engineInit(keysize: Int, random: SecureRandom?) { size = keysize }
    override fun engineGenerateKey(): SecretKey {
        val raw = ByteArray(size / 8).also { SecureRandom().nextBytes(it) }
        spec?.let { AndroidKeyStore.put(it.keystoreAlias, JSONObject().put("t", "aes").put("k", Base64.getEncoder().encodeToString(raw)).put("at", System.currentTimeMillis())) }
        return SecretKeySpec(raw, "AES")
    }
}

class EcGen : KeyPairGeneratorSpi() {
    private var spec: KeyGenParameterSpec? = null
    override fun initialize(keysize: Int, random: SecureRandom?) {}
    override fun initialize(params: AlgorithmParameterSpec?, random: SecureRandom?) { spec = params as? KeyGenParameterSpec }
    override fun generateKeyPair(): KeyPair {
        val g = java.security.KeyPairGenerator.getInstance("EC", "SunEC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val kp = g.generateKeyPair()
        spec?.let { AndroidKeyStore.put(it.keystoreAlias, JSONObject().put("t", "ec").put("pub", Base64.getEncoder().encodeToString(kp.public.encoded))
            .put("priv", Base64.getEncoder().encodeToString(kp.private.encoded)).put("at", System.currentTimeMillis())) }
        return kp
    }
}
