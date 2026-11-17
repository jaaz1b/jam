package app.jam.desktop.dev

import app.myspoti.pass.PassCookie
import app.myspoti.pass.PassOffer
import app.myspoti.pass.PassOfferServer
import app.myspoti.pass.PassPayload
import app.myspoti.pass.PassTaker

/** Development check of the sign-in code: one device offers, another takes, a third try is refused. */
fun main() {
    var done: String? = "pending"
    val server = PassOfferServer("PC", collect = { PassPayload(from = "PC", prefs = emptyMap(), extensions = emptyList(), extensionFiles = emptyMap(), cookies = listOf(PassCookie("sp_dc", "secret-session"))) },
        onDone = { done = it }, host = "127.0.0.1").start()
    val text = server.offer.toText()
    println("code: ${text.take(60)}…")
    println("parse of a LAN code: " + (PassOffer.parse(text.replace("127.0.0.1", "192.168.1.20")) != null) + ", of an invite: " + PassOffer.parse("orpheon://pass?v=1&a=192.168.1.2:1&k=x&t=y"))
    val o = server.offer
    val forged = o.copy(secret = ByteArray(32))
    println("forged: " + runCatching { PassTaker.take(forged) }.exceptionOrNull()?.message)
    val p = PassTaker.take(o)
    println("taken: from=${p.from} cookies=${p.cookies}")
    println("again: " + runCatching { PassTaker.take(o) }.exceptionOrNull()?.message)
    Thread.sleep(300); println("done=$done")
}
