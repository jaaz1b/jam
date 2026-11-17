package app.myspoti.pass

import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The other way round from [PassServer] (Jam): the device that is signed in shows a code, and the
 * device to sign in scans it and takes the setup and the session. For a phone whose own sign-in
 * page Spotify refuses, signed in through the computer, whose page it takes. The code carries the
 * computer's address on the Wi-Fi and a one-time secret; the taker proves it read the code, the
 * payload goes back sealed for the taker's own key (as [PassClient] seals it), and only once.
 */
data class PassOffer(val host: String, val port: Int, val secret: ByteArray, val name: String) {
    fun toText(): String = "${PassInvite.LINK}?v=1&take=1&a=$host:$port&s=${b64(secret)}&n=${URLEncoder.encode(name.take(40), "UTF-8")}"

    companion object {
        private fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

        /** The offer in a scanned text or an opened link, or null when it is not one. */
        fun parse(text: String): PassOffer? = runCatching {
            val t = text.trim()
            if (!t.regionMatches(0, PassInvite.LINK, 0, PassInvite.LINK.length, ignoreCase = true)) return null
            val rest = t.substring(PassInvite.LINK.length).removePrefix("/")
            if (!rest.startsWith("?")) return null
            val q = rest.substring(1).substringBefore('#').split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
            if (q["v"] != "1" || q["take"] != "1") return null
            val a = q["a"]!!
            val host = a.substringBeforeLast(':')
            require(PassInvite.isPrivate(host)) { "not a local address" }
            val port = a.substringAfterLast(':').toInt()
            require(port in 1..65535)
            val secret = Base64.getUrlDecoder().decode(q["s"]!!)
            require(secret.size == 32)
            val name = q["n"].orEmpty().filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }.trim().take(40)
            PassOffer(host, port, secret, name)
        }.getOrNull()

        internal fun proof(secret: ByteArray, key: ByteArray, token: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }.doFinal(key + token)
    }
}

/**
 * The signed-in side: listens for one taker for [lifetimeMs] at most, [offer] shown as a QR code
 * meanwhile. [collect] reads the setup when a taker proves it has the code; [onDone] is told once,
 * with the taker's address or null (stopped, timed out).
 */
class PassOfferServer(
    deviceName: String,
    private val collect: () -> PassPayload,
    private val lifetimeMs: Long = 10 * 60_000L,
    private val onDone: (String?) -> Unit = {},
    host: String? = PassInvite.localAddress(),
) {
    private val secret = PassCrypto.randomBytes(32)
    private val address = host ?: throw IllegalStateException("no local network")
    private val server = ServerSocket().apply { reuseAddress = true; soTimeout = 1_000; bind(InetSocketAddress(InetAddress.getByName(address), 0)) }
    private val over = AtomicBoolean(false)

    val offer = PassOffer(address, server.localPort, secret, deviceName)

    fun start(): PassOfferServer {
        Thread({ serve() }, "pass-offer").apply { isDaemon = true; start() }
        return this
    }

    fun stop() { if (over.compareAndSet(false, true)) { runCatching { server.close() }; onDone(null) } }

    private fun serve() {
        val until = System.currentTimeMillis() + lifetimeMs
        while (!over.get() && System.currentTimeMillis() < until) {
            val s = runCatching { server.accept() }.getOrNull() ?: continue
            s.use { handle(it) }
        }
        stop()
    }

    private fun handle(s: Socket) {
        s.soTimeout = 30_000
        val input = BufferedInputStream(s.getInputStream())
        val out = s.getOutputStream()
        fun answer(code: Int, text: String) {
            val body = text.encodeToByteArray()
            out.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
            out.write(body); out.flush()
        }
        val request = readLine(input) ?: return
        val headers = generateSequence { readLine(input)?.takeIf { it.isNotEmpty() } }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        if (!request.startsWith("POST /take ")) return answer(404, "not here")
        val length = headers["content-length"]?.toIntOrNull()?.takeIf { it in 1..8_192 } ?: return answer(413, "too large")
        val body = ByteArray(length).also { var read = 0; while (read < length) { val n = input.read(it, read, length - read); if (n < 0) return; read += n } }
        val d = Base64.getUrlDecoder()
        val parts = runCatching { body.decodeToString().split('.').map { d.decode(it) } }.getOrNull()?.takeIf { it.size == 3 } ?: return answer(400, "malformed")
        val (key, token, proof) = parts
        if (token.size != 16 || runCatching { PassCrypto.publicKey(key) }.isFailure) return answer(400, "malformed")
        if (!MessageDigest.isEqual(proof, PassOffer.proof(secret, key, token))) return answer(403, "refused")
        if (!over.compareAndSet(false, true)) return answer(410, "already taken")
        val sealed = runCatching { PassCrypto.seal(key, token, collect().toBytes()) }.getOrElse { answer(500, "not read"); onDone(null); return }
        val e = Base64.getUrlEncoder().withoutPadding()
        answer(200, "${e.encodeToString(sealed.senderKey)}.${e.encodeToString(sealed.nonce)}.${e.encodeToString(sealed.box)}")
        runCatching { server.close() }
        onDone(s.inetAddress?.hostAddress ?: "?")
    }

    private fun readLine(input: InputStream): String? {
        val b = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.isEmpty()) null else b.toString()
            if (c == '\n'.code) return b.toString().trimEnd('\r')
            if (b.length > 8_192) return null
            b.append(c.toChar())
        }
    }
}

/** The side to sign in: takes the setup [offer] holds. Throws with the reason it failed. */
object PassTaker {
    fun take(offer: PassOffer): PassPayload {
        val keys = PassCrypto.keyPair()
        val token = PassCrypto.randomBytes(16)
        val key = keys.public.encoded
        val e = Base64.getUrlEncoder().withoutPadding()
        val body = "${e.encodeToString(key)}.${e.encodeToString(token)}.${e.encodeToString(PassOffer.proof(offer.secret, key, token))}".encodeToByteArray()
        val (code, text) = Socket().use { s ->
            s.connect(InetSocketAddress(offer.host, offer.port), 8_000)
            s.soTimeout = 60_000
            val out = s.getOutputStream()
            out.write("POST /take HTTP/1.1\r\nHost: ${offer.host}:${offer.port}\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
            out.write(body); out.flush()
            val all = s.getInputStream().readBytes().decodeToString()
            val status = all.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            status to all.substringAfter("\r\n\r\n", "")
        }
        if (code != 200) throw java.io.IOException(when (code) { 403 -> "the code was not accepted (scan the one on screen now)"; 410 -> "this code was already used: show a new one"; else -> "HTTP $code" })
        val d = Base64.getUrlDecoder()
        val p = text.trim().split('.')
        return PassPayload.of(PassCrypto.open(keys, token, PassCrypto.Sealed(d.decode(p[0]), d.decode(p[1]), d.decode(p[2]))))
    }
}
