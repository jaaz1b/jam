package android.security.keystore

import java.security.spec.AlgorithmParameterSpec

object KeyProperties {
    const val KEY_ALGORITHM_AES = "AES"; const val KEY_ALGORITHM_EC = "EC"; const val KEY_ALGORITHM_RSA = "RSA"; const val KEY_ALGORITHM_HMAC_SHA256 = "HmacSHA256"
    const val PURPOSE_ENCRYPT = 1; const val PURPOSE_DECRYPT = 2; const val PURPOSE_SIGN = 4; const val PURPOSE_VERIFY = 8
    const val BLOCK_MODE_GCM = "GCM"; const val BLOCK_MODE_CBC = "CBC"; const val BLOCK_MODE_CTR = "CTR"
    const val ENCRYPTION_PADDING_NONE = "NoPadding"; const val ENCRYPTION_PADDING_PKCS7 = "PKCS7Padding"
    const val DIGEST_SHA256 = "SHA-256"; const val DIGEST_NONE = "NONE"; const val DIGEST_SHA512 = "SHA-512"
}
class StrongBoxUnavailableException(m: String? = null) : java.security.ProviderException(m)
class KeyPermanentlyInvalidatedException(m: String? = null) : java.security.InvalidKeyException(m)
class KeyGenParameterSpec private constructor(val keystoreAlias: String, val purposes: Int, val keySize: Int) : AlgorithmParameterSpec {
    class Builder(private val alias: String, private val purposes: Int) {
        private var size = -1
        fun setBlockModes(vararg m: String) = this
        fun setEncryptionPaddings(vararg p: String) = this
        fun setDigests(vararg d: String) = this
        fun setKeySize(s: Int) = apply { size = s }
        fun setRandomizedEncryptionRequired(b: Boolean) = this
        fun setUserAuthenticationRequired(b: Boolean) = this
        fun setIsStrongBoxBacked(b: Boolean) = apply { if (b) throw StrongBoxUnavailableException("no StrongBox on the PC") }
        fun setAttestationChallenge(c: ByteArray?) = this
        fun setAlgorithmParameterSpec(s: AlgorithmParameterSpec) = this
        fun setUnlockedDeviceRequired(b: Boolean) = this
        fun setInvalidatedByBiometricEnrollment(b: Boolean) = this
        fun build() = KeyGenParameterSpec(alias, purposes, size)
    }
}
