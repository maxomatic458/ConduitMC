package io.github.maxomatic458.conduit.irohnet

import computer.iroh.BiStream
import kotlinx.coroutines.runBlocking

/**
 * One bidirectional QUIC stream, exposed as a blocking byte pipe.
 *
 * A stream carries exactly one Minecraft TCP connection

 * Not thread-safe for concurrent use of the same direction. The intended usage is one reader
 * thread and one writer thread.
 */
class IrohStream internal constructor(private val bi: BiStream) : AutoCloseable {

    // Each send()/recv() call clones the underlying Arc into a FFI handle, so these must
    // be obtained once and reused
    private val send = bi.send()
    private val recv = bi.recv()

    @Volatile private var sendFinished = false
    @Volatile private var closed = false

    /**
     * Reads the next chunk, blocking until at least one byte is available.
     *
     * @param maxBytes upper bound on the returned chunk.
     * @return the bytes read, or `null` once the peer has finished its send half (EOF).
     */
    fun read(maxBytes: Int): ByteArray? {
        require(maxBytes > 0) { "maxBytes must be positive, got $maxBytes" }
        val chunk = irohCall("read from iroh stream") {
            runBlocking { recv.read(maxBytes.toUInt()) }
        }
        return if (chunk.isEmpty()) null else chunk
    }

    /** Writes [len] bytes from [src] starting at [off], blocking until all of them are accepted. */
    @JvmOverloads
    fun write(src: ByteArray, off: Int = 0, len: Int = src.size) {
        if (len == 0) return
        val payload = if (off == 0 && len == src.size) src else src.copyOfRange(off, off + len)
        irohCall("write to iroh stream") {
            runBlocking { send.writeAll(payload) }
        }
    }

    fun finishSend() {
        if (sendFinished || closed) return
        sendFinished = true
        irohCall("finish iroh stream") {
            runBlocking { send.finish() }
        }
    }

    /**
     * Releases the stream's native handles.
     */
    override fun close() {
        if (closed) return
        closed = true
        // Independent handles -- one failing must not strand the others.
        runCatching { send.close() }
        runCatching { recv.close() }
        runCatching { bi.close() }
    }
}
