package ph.appbuilders.saklolo.contact

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

data class DeviceKeys(val privateKey: ByteArray, val publicKey: ByteArray)

/**
 * 1:1 payload seal. X25519, HKDF-SHA256, and AES-GCM. Routing headers stay outside the box.
 * SOS is not sealed. A phone that does not hold either private key cannot read the box.
 */
object SealedBox {
    private const val INFO = "B-LINK-1"
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private val random = SecureRandom()

    fun generate(): DeviceKeys {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(random))
        val pair = generator.generateKeyPair()
        val privateKey = (pair.private as X25519PrivateKeyParameters).encoded
        val publicKey = (pair.public as X25519PublicKeyParameters).encoded
        return DeviceKeys(privateKey, publicKey)
    }

    fun publicFromPrivate(privateKey: ByteArray): ByteArray =
        X25519PrivateKeyParameters(privateKey).generatePublicKey().encoded

    fun encodeKey(key: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    fun decodeKey(text: String?): ByteArray? {
        if (text.isNullOrBlank()) return null
        return try {
            val decoded = Base64.getUrlDecoder().decode(text.trim())
            if (decoded.size == KEY_BYTES) decoded else null
        } catch (_: Exception) {
            null
        }
    }

    fun payload(senderName: String, body: String): ByteArray =
        "${senderName.replace("\u0000", " ")}\u0000$body".toByteArray(Charsets.UTF_8)

    fun readPayload(bytes: ByteArray): Pair<String, String>? {
        val text = bytes.toString(Charsets.UTF_8)
        val split = text.indexOf('\u0000')
        if (split < 0) return null
        return text.substring(0, split) to text.substring(split + 1)
    }

    fun aad(id: String, fromDeviceId: String, toDeviceId: String, kind: String, createdAtMillis: Long): ByteArray =
        "$id|$fromDeviceId|$toDeviceId|$kind|$createdAtMillis".toByteArray(Charsets.UTF_8)

    fun seal(senderPrivate: ByteArray, recipientPublic: ByteArray, plaintext: ByteArray, aad: ByteArray): String =
        encodeKey(sealBytes(senderPrivate, recipientPublic, plaintext, aad))

    fun sealBytes(senderPrivate: ByteArray, recipientPublic: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = aes(Cipher.ENCRYPT_MODE, senderPrivate, recipientPublic, nonce, aad).doFinal(plaintext)
        return nonce + cipher
    }

    fun open(recipientPrivate: ByteArray, senderPublic: ByteArray, sealed: String, aad: ByteArray): ByteArray? {
        val packed = decodePacked(sealed) ?: return null
        return openBytes(recipientPrivate, senderPublic, packed, aad)
    }

    fun openBytes(recipientPrivate: ByteArray, senderPublic: ByteArray, packed: ByteArray, aad: ByteArray): ByteArray? {
        if (packed.size <= NONCE_BYTES) return null
        val nonce = packed.copyOfRange(0, NONCE_BYTES)
        val cipher = packed.copyOfRange(NONCE_BYTES, packed.size)
        return try {
            aes(Cipher.DECRYPT_MODE, recipientPrivate, senderPublic, nonce, aad).doFinal(cipher)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodePacked(sealed: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(sealed.trim())
    } catch (_: Exception) {
        null
    }

    private fun aes(mode: Int, ourPrivate: ByteArray, theirPublic: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
        val key = SecretKeySpec(sharedKey(ourPrivate, theirPublic), "AES")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(128, nonce))
            updateAAD(aad)
        }
    }

    private fun sharedKey(ourPrivate: ByteArray, theirPublic: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(ourPrivate))
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(theirPublic), secret, 0)
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(secret, ByteArray(0), INFO.toByteArray(Charsets.UTF_8)))
        val key = ByteArray(KEY_BYTES)
        hkdf.generateBytes(key, 0, key.size)
        return key
    }
}

object Ack {
    const val KIND = "ack"

    fun text(messageId: String, hops: Int): String = "$messageId|$hops"

    fun parse(body: String): Pair<String, Int>? {
        val split = body.lastIndexOf('|')
        if (split <= 0 || split == body.lastIndex) return null
        val hops = body.substring(split + 1).toIntOrNull() ?: return null
        val id = body.substring(0, split)
        if (id.isBlank() || hops < 0) return null
        return id to hops
    }
}

object Delivery {
    const val SENDING = "sending"
    const val RELAYED = "relayed"
    const val DELIVERED = "delivered"

    fun label(state: String, hops: Int): String = when (state) {
        DELIVERED -> "Delivered"
        RELAYED -> "Relayed · $hops hops"
        else -> "Sending"
    }

    /** Delivered is only the ACK. A local send does not count. */
    fun afterAck(current: String): String = DELIVERED
}

data class Held(
    val id: String,
    val createdAtMillis: Long,
    val expireAtMillis: Long,
    val clipBytes: Int,
    val sos: Boolean,
)

object HoldPolicy {
    const val TTL_MS = 6L * 60 * 60 * 1000
    const val MAX_MESSAGES = 200
    const val MAX_CLIP_BYTES = 20 * 1024 * 1024

    fun expireAt(createdAtMillis: Long): Long = createdAtMillis + TTL_MS

    /**
     * Drop expired rows, then oldest non-SOS until the count and clip caps fit.
     * SOS is removed only when nothing else can make room.
     */
    fun evict(items: List<Held>, now: Long): List<Held> {
        val live = items.filter { it.expireAtMillis > now }.toMutableList()
        while (live.size > MAX_MESSAGES) {
            val victim = oldest(live, preferNonSos = true) ?: break
            live.remove(victim)
        }
        while (live.sumOf { it.clipBytes.coerceAtLeast(0) } > MAX_CLIP_BYTES) {
            val victim = oldest(live.filter { it.clipBytes > 0 }, preferNonSos = true) ?: break
            live.remove(victim)
        }
        return live
    }

    private fun oldest(items: List<Held>, preferNonSos: Boolean): Held? {
        if (preferNonSos) {
            items.filter { !it.sos }.minByOrNull { it.createdAtMillis }?.let { return it }
        }
        return items.minByOrNull { it.createdAtMillis }
    }
}

object SummarySync {
    const val MIN_INTERVAL_MS = 30_000L

    fun allow(lastSentAtMillis: Long, now: Long): Boolean = now - lastSentAtMillis >= MIN_INTERVAL_MS

    /** Ids we have that the other phone did not list. */
    fun theyNeed(myIds: Set<String>, theirIds: Set<String>): Set<String> = myIds - theirIds
}

object CallOffer {
    const val FALLBACK = "Not in direct range. Send a voice note instead?"

    fun fallback(directlyConnected: Boolean): String? = if (directlyConnected) null else FALLBACK
}

object RadioPolicy {
    const val FLOOR_PERCENT = 10

    /** Under 10% the radios stay off unless an SOS is going out. */
    fun advertise(batteryPercent: Int, sosInFlight: Boolean): Boolean =
        sosInFlight || batteryPercent >= FLOOR_PERCENT
}

object SendGate {
    const val NEED_KEY = "Add by QR or meet nearby once"

    fun blockReason(hasPeerKey: Boolean, sos: Boolean): String? =
        if (sos || hasPeerKey) null else NEED_KEY
}

object LinkMessage {
    fun hello(myId: String, name: String, publicKey: String, toDeviceId: String, now: Long) = DirectMessage(
        id = "hello-$myId",
        fromDeviceId = myId,
        toDeviceId = toDeviceId.ifBlank { "mesh" },
        senderName = name.ifBlank { "Phone" },
        body = publicKey,
        createdAtMillis = now,
        kind = "hello",
    )

    fun sealText(privateKey: ByteArray, peerPublic: String, message: DirectMessage): DirectMessage? {
        val peer = SealedBox.decodeKey(peerPublic) ?: return null
        val box = SealedBox.seal(privateKey, peer, SealedBox.payload(message.senderName, message.body), aad(message))
        return message.copy(box = box)
    }

    fun sealClip(privateKey: ByteArray, peerPublic: String, message: DirectMessage, wav: ByteArray): ByteArray? {
        val peer = SealedBox.decodeKey(peerPublic) ?: return null
        return SealedBox.sealBytes(privateKey, peer, wav, aad(message))
    }

    fun openText(privateKey: ByteArray, peerPublic: String, message: DirectMessage): DirectMessage? {
        if (message.box.isBlank()) return message
        val peer = SealedBox.decodeKey(peerPublic) ?: return null
        val plain = SealedBox.open(privateKey, peer, message.box, aad(message)) ?: return null
        val payload = SealedBox.readPayload(plain) ?: return null
        return message.copy(senderName = payload.first.ifBlank { message.senderName }, body = payload.second)
    }

    fun openClip(privateKey: ByteArray, peerPublic: String, message: DirectMessage, packed: ByteArray): ByteArray? {
        val peer = SealedBox.decodeKey(peerPublic) ?: return null
        return SealedBox.openBytes(privateKey, peer, packed, aad(message))
    }

    /** ACK is sealed back to the sender. The GCM tag is what proves it was opened by the recipient. */
    fun ack(
        original: DirectMessage,
        myId: String,
        myName: String,
        privateKey: ByteArray,
        peerPublic: String,
        now: Long,
    ): DirectMessage? {
        val draft = DirectMessage(
            id = "ack-${original.id}",
            fromDeviceId = myId,
            toDeviceId = original.fromDeviceId,
            senderName = myName.ifBlank { "Phone" },
            body = Ack.text(original.id, original.hops),
            createdAtMillis = now,
            kind = Ack.KIND,
        )
        return sealText(privateKey, peerPublic, draft)
    }

    private fun aad(message: DirectMessage): ByteArray =
        SealedBox.aad(message.id, message.fromDeviceId, message.toDeviceId, message.kind, message.createdAtMillis)
}

object RelayCatalog {
    fun ids(
        messages: List<DirectMessage>,
        alerts: List<ph.appbuilders.saklolo.model.Alert>,
        now: Long,
        alertClipBytes: (ph.appbuilders.saklolo.model.Alert) -> Int = { 0 },
    ): Set<String> {
        val held = messages.filter { DirectGate.holdsForRelay(it) }.map { message ->
            Held(
                id = message.id,
                createdAtMillis = message.createdAtMillis,
                expireAtMillis = if (message.expireAtMillis > 0) message.expireAtMillis else HoldPolicy.expireAt(message.createdAtMillis),
                clipBytes = message.clipBytes,
                sos = false,
            )
        } + alerts.map { alert ->
            Held(
                id = alert.id,
                createdAtMillis = alert.createdAtMillis,
                expireAtMillis = HoldPolicy.expireAt(alert.createdAtMillis),
                clipBytes = alertClipBytes(alert).coerceAtLeast(0),
                sos = true,
            )
        }
        return HoldPolicy.evict(held, now).map { it.id }.toSet()
    }
}
