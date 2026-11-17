package com.music.spotui.pass

import android.widget.Toast
import app.myspoti.engine.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.myspoti.pass.PassClient
import app.myspoti.pass.PassInvite
import app.myspoti.pass.PassServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A QR code drawn module by module, black on white with its quiet zone. */
@Composable
fun PassQr(text: String, modifier: Modifier = Modifier) {
    val m = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp)) {
        Canvas(Modifier.size(240.dp)) {
            val cell = size.width / m.width
            for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y])
                drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/**
 * This device waits for the setup of another one: a QR code that the other phone scans
 * (Settings > Send to another device), on the same Wi-Fi. What comes is applied, then the app
 * starts again. Closed, it stops listening.
 */
@Composable
fun PassReceiveDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var invite by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var received by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val name = android.os.Build.MODEL.orEmpty().ifBlank { "Android" }
        val server = runCatching {
            PassServer(name, onPayload = { PassAndroid.apply(context.applicationContext, it) }, onDone = { p ->
                if (p != null) { received = p.from.ifBlank { "?" }; PassAndroid.restart(context.applicationContext) }
            }).start()
        }.getOrNull()
        if (server == null) failed = true else invite = server.invite.toText()
        onDispose { server?.stop() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1E)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.pass_receive_title), color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
            when {
                received != null -> Text(stringResource(R.string.pass_received, received!!), color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                failed -> Text(stringResource(R.string.pass_no_network), color = Color(0xFFFF8A80), fontSize = 15.sp, textAlign = TextAlign.Center)
                else -> {
                    invite?.let { PassQr(it) }
                    Text(stringResource(R.string.pass_receive_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    }
}

/**
 * This phone sends its setup to another device: the scanner opens, the code read must be one of
 * this app's, the user confirms the device's name, then everything goes over the local network.
 */
@Composable
fun PassSendFlow(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var invite by remember { mutableStateOf<PassInvite?>(null) }
    LaunchedEffect(Unit) {
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { code ->
                val i = PassInvite.parse(code.rawValue.orEmpty())
                if (i == null) { Toast.makeText(context, R.string.pass_not_invite, Toast.LENGTH_LONG).show(); onDismiss() } else invite = i
            }
            .addOnCanceledListener { onDismiss() }
            .addOnFailureListener { Toast.makeText(context, R.string.pass_scan_unavailable, Toast.LENGTH_LONG).show(); onDismiss() }
    }
    invite?.let { PassSendConfirm(it, fromLink = false, onDismiss = onDismiss) }
}

/**
 * The yes before sending to [i]: its name and address on the Wi-Fi, and nothing goes without the
 * Send button. [fromLink]: the code came in a link (a camera opened it, or anything else that can
 * open a link: a web page, a message), so the confirmation also says to send only to a code just
 * scanned on one's own device.
 */
@Composable
fun PassSendConfirm(i: PassInvite, fromLink: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember(i) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.pass_confirm_title, i.name.ifBlank { i.host })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pass_confirm_text))
                Text("${i.host}:${i.port}", fontSize = 13.sp, color = Color.Gray)
                if (fromLink) Text(stringResource(R.string.pass_link_warning), color = Color(0xFFFFB74D))
            }
        },
        confirmButton = {
            TextButton(enabled = !sending, onClick = {
                sending = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { PassClient.send(i, PassAndroid.collect(context.applicationContext)) } }
                    Toast.makeText(context, r.fold({ context.getString(R.string.pass_sent, i.name) }, { context.getString(R.string.pass_failed, it.message ?: it.javaClass.simpleName) }), Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }) { Text(stringResource(R.string.pass_send_action)) }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/**
 * The pass QR codes opened as links (`orpheon://pass?…`, see [PassInvite]): a phone's camera
 * scans the code of a TV, a computer or another phone and the app opens on the confirmation,
 * without going through the settings. A link only ever asks: nothing is sent before the user's
 * yes, a link that is not a whole invitation for the local network is refused, and a phone not
 * signed in is told to sign in first.
 */
object PassLinks {
    /** The invitation waiting for the user's answer, shown by [PassLinkHost]. */
    val pending = kotlinx.coroutines.flow.MutableStateFlow<PassInvite?>(null)

    /** A link of the pass's schemes (well formed or not): this app's to answer, never another handler's. */
    fun isPassLink(link: String): Boolean {
        val l = link.trim()
        return l.startsWith("${PassInvite.LINK_SCHEME}:", ignoreCase = true) || l.startsWith("${PassInvite.SCHEME}:", ignoreCase = true)
    }

    /** Takes [link] when it is a pass link (true), and puts it up for confirmation when it can be. */
    fun offer(context: android.content.Context, link: String, signedIn: Boolean): Boolean {
        if (!isPassLink(link)) return false
        // Jam: a computer's code to sign this device in (signed in or not: that is what it is for).
        app.myspoti.pass.PassOffer.parse(link)?.let { PassTakeLinks.pending.value = it; return true }
        val i = PassInvite.parse(link)
        when {
            i == null -> Toast.makeText(context, R.string.pass_not_invite, Toast.LENGTH_LONG).show()
            !signedIn -> Toast.makeText(context, R.string.pass_need_sign_in, Toast.LENGTH_LONG).show()
            else -> pending.value = i
        }
        return true
    }
}

/** Above every screen of a signed-in app: the confirmation of a pass link that was opened. */
@Composable
fun PassLinkHost() {
    val i by PassLinks.pending.collectAsState()
    // A new link while one is up replaces it (keyed on the invitation: its own Send button).
    i?.let { invite -> androidx.compose.runtime.key(invite) { PassSendConfirm(invite, fromLink = true, onDismiss = { PassLinks.pending.compareAndSet(invite, null) }) } }
}

/**
 * Jam: this device (signed in) shows a code for another to take its sign-in, settings and
 * extensions ([app.myspoti.pass.PassOffer]): a computer whose sign-in Spotify takes, for a phone
 * whose own sign-in page Spotify refuses. Closed, it stops listening.
 */
@Composable
fun PassOfferDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val name = android.os.Build.MODEL.orEmpty().ifBlank { "Jam" }
        val server = runCatching {
            app.myspoti.pass.PassOfferServer(name, collect = { kotlinx.coroutines.runBlocking { PassAndroid.collect(context.applicationContext) } },
                onDone = { who -> if (who != null) done = who }).start()
        }.getOrNull()
        if (server == null) failed = true else code = server.offer.toText()
        onDispose { server?.stop() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 440.dp).padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1E)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.pass_offer_title), color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
            when {
                done != null -> Text(stringResource(R.string.pass_offer_done, done!!), color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                failed -> Text(stringResource(R.string.pass_no_network), color = Color(0xFFFF8A80), fontSize = 15.sp, textAlign = TextAlign.Center)
                else -> {
                    code?.let { PassQr(it) }
                    Text(stringResource(R.string.pass_offer_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(if (done != null) android.R.string.ok else android.R.string.cancel)) }
        }
    }
}

/** Jam: the scanner opens on the code another device shows ([PassOfferDialog]), then [PassTakeConfirm]. */
@Composable
fun PassTakeFlow(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var offer by remember { mutableStateOf<app.myspoti.pass.PassOffer?>(null) }
    LaunchedEffect(Unit) {
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { code ->
                val text = code.rawValue.orEmpty()
                val o = app.myspoti.pass.PassOffer.parse(text)
                when {
                    o != null -> offer = o
                    PassInvite.parse(text) != null -> { Toast.makeText(context, R.string.pass_take_wrong_code, Toast.LENGTH_LONG).show(); onDismiss() }
                    else -> { Toast.makeText(context, R.string.pass_not_invite, Toast.LENGTH_LONG).show(); onDismiss() }
                }
            }
            .addOnCanceledListener { onDismiss() }
            .addOnFailureListener { Toast.makeText(context, R.string.pass_scan_unavailable, Toast.LENGTH_LONG).show(); onDismiss() }
    }
    offer?.let { PassTakeConfirm(it, onDismiss) }
}

/** The yes before taking [o]'s sign-in; then taken, applied, and the app starts again. */
@Composable
fun PassTakeConfirm(o: app.myspoti.pass.PassOffer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var taking by remember(o) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!taking) onDismiss() },
        title = { Text(stringResource(R.string.pass_take_confirm, o.name.ifBlank { o.host })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pass_take_confirm_text))
                Text("${o.host}:${o.port}", fontSize = 13.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(enabled = !taking, onClick = {
                taking = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { app.myspoti.pass.PassTaker.take(o) }.mapCatching { p ->
                            if (!PassAndroid.apply(context.applicationContext, p)) throw java.io.IOException("could not apply it")
                            p
                        }
                    }
                    r.fold(
                        { p ->
                            Toast.makeText(context, context.getString(R.string.pass_received, p.from.ifBlank { o.name }), Toast.LENGTH_LONG).show()
                            PassAndroid.restart(context.applicationContext)
                        },
                        { Toast.makeText(context, context.getString(R.string.pass_take_failed, it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show() },
                    )
                    onDismiss()
                }
            }) { Text(stringResource(R.string.pass_take)) }
        },
        dismissButton = { TextButton(enabled = !taking, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Jam: a sign-in code opened as a link (the camera scanned the computer's code). */
object PassTakeLinks {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<app.myspoti.pass.PassOffer?>(null)
}

/** Above every screen, signed in or not: the yes before taking a sign-in that was opened as a link. */
@Composable
fun PassTakeHost() {
    val o by PassTakeLinks.pending.collectAsState()
    o?.let { offer -> androidx.compose.runtime.key(offer) { PassTakeConfirm(offer, onDismiss = { PassTakeLinks.pending.compareAndSet(offer, null) }) } }
}
