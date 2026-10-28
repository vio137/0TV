package dev.tidytv.adb

import dev.tidytv.core.ops.TvShell
import kotlinx.coroutines.runBlocking
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Just enough of adbd for the real Kadb client: CNXN, AUTH (token, signature, public key with a
 * simulated "Allow debugging?" prompt), legacy `shell:` streams and `sync:` SEND. Shell commands
 * are answered by [tv], so the full TidyTV stack runs over a real socket against a simulated TV.
 */
class FakeAdbd(private val tv: TvShell) : AutoCloseable {
    enum class User { ALLOW_ALWAYS, ALLOW_ONCE, DENY, IGNORE }

    @Volatile var user: User = User.ALLOW_ALWAYS
    @Volatile var promptDelayMs: Long = 0
    /** Close the socket after this many shell commands (simulates Wi-Fi dropping). */
    @Volatile var dropAfterCommands: Int? = null

    val trusted = CopyOnWriteArrayList<RSAPublicKey>()
    val prompts = AtomicInteger()
    val connections = AtomicInteger()
    val shellCommands = CopyOnWriteArrayList<String>()
    /** Lines written to kept-open `shell:` streams, and how many such streams were opened. */
    val sessionLines = CopyOnWriteArrayList<String>()
    val sessionsOpened = AtomicInteger()

    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    private val sockets = CopyOnWriteArrayList<Socket>()

    init {
        thread(isDaemon = true, name = "fake-adbd") {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                sockets += s
                connections.incrementAndGet()
                thread(isDaemon = true) { runCatching { serve(s) }; runCatching { s.close() } }
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
    }

    /** Drops every open connection, like the TV leaving Wi-Fi. */
    fun dropAll() = sockets.forEach { runCatching { it.close() } }

    // --- protocol ---

    private class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun serve(s: Socket) {
        val input = DataInputStream(s.getInputStream())
        val out = s.getOutputStream()
        var token = Random.nextBytes(20)
        var authed = false
        var sessionKeyAllowed = false
        val streams = mutableMapOf<Int, Stream>()
        // Interactive `shell:` streams (the remote): each written line is one command.
        val shells = mutableMapOf<Int, StringBuilder>()
        var nextId = 100

        while (true) {
            val m = read(input) ?: return
            when (m.cmd) {
                CNXN -> {
                    token = Random.nextBytes(20)
                    write(out, AUTH, TOKEN, 0, token)
                }
                AUTH -> when (m.arg0) {
                    SIGNATURE -> {
                        if (trusted.any { verify(it, token, m.data) }) {
                            authed = true
                            write(out, CNXN, 0x01000001, 256 * 1024, BANNER)
                        } else {
                            token = Random.nextBytes(20)
                            write(out, AUTH, TOKEN, 0, token)
                        }
                    }
                    RSAPUBLIC -> {
                        prompts.incrementAndGet()
                        if (promptDelayMs > 0) Thread.sleep(promptDelayMs)
                        when (user) {
                            User.ALLOW_ALWAYS -> { trusted += parseAndroidPubkey(m.data); sessionKeyAllowed = true }
                            User.ALLOW_ONCE -> sessionKeyAllowed = true
                            User.DENY -> return
                            User.IGNORE -> { Thread.sleep(Long.MAX_VALUE / 4) }
                        }
                        if (sessionKeyAllowed) {
                            authed = true
                            write(out, CNXN, 0x01000001, 256 * 1024, BANNER)
                        }
                    }
                }
                OPEN -> {
                    check(authed) { "OPEN before auth" }
                    val dest = String(m.data).trimEnd('\u0000')
                    val id = nextId++
                    val remote = m.arg0
                    write(out, OKAY, id, remote, ByteArray(0))
                    if (dest == "shell:") {
                        shells[id] = StringBuilder()
                        sessionsOpened.incrementAndGet()
                    } else if (dest.startsWith("shell:")) {
                        val cmd = dest.removePrefix("shell:")
                        shellCommands += cmd
                        val limit = dropAfterCommands
                        if (limit != null && shellCommands.size > limit) { dropAfterCommands = null; return }
                        val answer = runBlocking { tv.run(cmd) }
                        val bytes = answer.toByteArray()
                        if (bytes.isNotEmpty()) {
                            write(out, WRTE, id, remote, bytes)
                            awaitOkay(input, id)
                        }
                        write(out, CLSE, id, remote, ByteArray(0))
                    } else if (dest.startsWith("sync:")) {
                        streams[id] = Stream(remote)
                    } else {
                        write(out, CLSE, id, remote, ByteArray(0))
                    }
                }
                WRTE -> {
                    shells[m.arg1]?.let { buf ->
                        write(out, OKAY, m.arg1, m.arg0, ByteArray(0))
                        buf.append(String(m.data))
                        while (buf.contains('\n')) {
                            val line = buf.substring(0, buf.indexOf('\n')).trim()
                            buf.delete(0, buf.indexOf('\n') + 1)
                            if (line.isNotEmpty()) { sessionLines += line; runBlocking { tv.run(line) } }
                        }
                        continue
                    }
                    val st = streams[m.arg1]
                    write(out, OKAY, m.arg1, m.arg0, ByteArray(0))
                    if (st != null) {
                        st.buf.write(m.data)
                        st.process()?.let { reply ->
                            write(out, WRTE, m.arg1, st.remote, reply)
                            awaitOkay(input, m.arg1)
                        }
                    }
                }
                OKAY, CLSE -> if (m.cmd == CLSE) { streams.remove(m.arg1); shells.remove(m.arg1) }
            }
        }
    }

    /** Collects sync bytes; answers "OKAY" once a SEND ... DONE sequence is complete. */
    private inner class Stream(val remote: Int) {
        val buf = java.io.ByteArrayOutputStream()
        private var path: String? = null
        private val data = java.io.ByteArrayOutputStream()

        fun process(): ByteArray? {
            var b = ByteBuffer.wrap(buf.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
            var consumed = 0
            var reply: ByteArray? = null
            while (b.remaining() >= 8) {
                val id = ByteArray(4).also { b.get(it) }.toString(Charsets.US_ASCII)
                val len = b.int
                when (id) {
                    "SEND" -> { if (b.remaining() < len) break; val p = ByteArray(len).also { b.get(it) }; path = String(p).substringBefore(',') }
                    "DATA" -> { if (b.remaining() < len) break; val d = ByteArray(len).also { b.get(it) }; data.write(d) }
                    "DONE" -> {
                        runBlocking { tv.push(data.toByteArray(), path!!) }
                        reply = "OKAY".toByteArray() + ByteArray(4)
                        data.reset()
                    }
                    "QUIT" -> Unit
                    else -> error("unexpected sync id $id")
                }
                consumed = b.position()
            }
            val rest = buf.toByteArray().copyOfRange(consumed, buf.size())
            buf.reset(); buf.write(rest)
            return reply
        }
    }

    private fun awaitOkay(input: DataInputStream, id: Int) {
        // The client acknowledges our WRTE before we send more. Other packets cannot arrive on
        // this single-stream test flow, so the next packet must be that OKAY.
        val m = read(input) ?: throw EOFException()
        check(m.cmd == OKAY && m.arg1 == id) { "expected OKAY for $id" }
    }

    private fun read(input: InputStream): Msg? {
        val h = ByteArray(24)
        if (!readFully(input, h)) return null
        val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = bb.int; val a0 = bb.int; val a1 = bb.int; val len = bb.int
        val data = ByteArray(len)
        if (!readFully(input, data)) return null
        return Msg(cmd, a0, a1, data)
    }

    private fun readFully(input: InputStream, b: ByteArray): Boolean {
        var off = 0
        while (off < b.size) {
            val n = try { input.read(b, off, b.size - off) } catch (e: Exception) { -1 }
            if (n < 0) return false
            off += n
        }
        return true
    }

    @Synchronized
    private fun write(out: OutputStream, cmd: Int, a0: Int, a1: Int, data: ByteArray) {
        val bb = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(cmd).putInt(a0).putInt(a1).putInt(data.size)
        bb.putInt(data.sumOf { it.toInt() and 0xff }).putInt(cmd.inv()).put(data)
        out.write(bb.array()); out.flush()
    }

    companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257
        const val TOKEN = 1
        const val SIGNATURE = 2
        const val RSAPUBLIC = 3
        val BANNER = "device::ro.product.name=fake;ro.product.model=Fake;ro.product.device=fake;features=".toByteArray()

        /** adbd: RSA "decrypt" with the public key and compare the trailing 20 bytes to the token. */
        fun verify(key: RSAPublicKey, token: ByteArray, sig: ByteArray): Boolean = runCatching {
            val c = Cipher.getInstance("RSA/ECB/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key)
            val plain = c.doFinal(sig)
            plain.copyOfRange(plain.size - token.size, plain.size).contentEquals(token)
        }.getOrDefault(false)

        /** Android's RSAPublicKey struct (base64, then " user@host\0"). */
        fun parseAndroidPubkey(payload: ByteArray): RSAPublicKey {
            val b64 = String(payload).trimEnd('\u0000').substringBefore(' ')
            val raw = ByteBuffer.wrap(Base64.getDecoder().decode(b64)).order(ByteOrder.LITTLE_ENDIAN)
            val words = raw.int
            raw.int // n0inv
            val n = ByteArray(words * 4).also { raw.get(it) }.reversedArray()
            raw.position(raw.position() + words * 4) // rr
            val e = raw.int
            val spec = RSAPublicKeySpec(BigInteger(1, n), BigInteger.valueOf(e.toLong()))
            return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
        }
    }
}
