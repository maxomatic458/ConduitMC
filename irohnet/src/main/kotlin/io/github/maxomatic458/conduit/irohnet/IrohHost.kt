package io.github.maxomatic458.conduit.irohnet

import computer.iroh.Connection
import computer.iroh.Endpoint
import computer.iroh.EndpointOptions
import computer.iroh.presetN0
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

/**
 * The hosting half of the tunnel: a listening iroh endpoint whose address is permanent.
 *
 * The endpoint's identity is derived from the 32-byte secret key handed to [open].
 */
class IrohHost private constructor(
    private val endpoint: Endpoint,
    /** The permanent connect ID: 64 lowercase hex chars */
    @get:JvmName("endpointId") val endpointId: String,
) : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("iroh-host"))
    private val inbound = LinkedBlockingQueue<Any>()
    private val connections = java.util.Collections.synchronizedList(mutableListOf<Connection>())

    @Volatile private var closed = false

    companion object {
        private val SHUTDOWN = Any()

        /**
         * Binds a listening endpoint and starts accepting.
         *
         * Blocks until the endpoint is bound and [Endpoint.online].

         * @param secretKey 32 bytes; see [IrohNet.generateSecretKey]
         * @param settings ALPN and relay configuration; both ends must agree on the ALPN
         */
        @JvmStatic
        @JvmOverloads
        fun open(secretKey: ByteArray, settings: IrohEndpointSettings = IrohEndpointSettings()): IrohHost {
            require(secretKey.size == 32) { "secret key must be 32 bytes, got ${secretKey.size}" }
            val alpnBytes = settings.alpn.toByteArray(Charsets.UTF_8)
            val endpoint = irohCall("bind iroh host endpoint") {
                runBlocking {
                    val ep = Endpoint.bind(settings.toEndpointOptions(secretKey, listOf(alpnBytes)))
                    // Wait for a usable home relay before reporting the ID as publishable
                    ep.online()
                    ep
                }
            }
            val id = irohCall("read iroh host endpoint id") { endpoint.id().use { it.toString() } }
            return IrohHost(endpoint, id).also { it.startAccepting() }
        }
    }

    private fun startAccepting() {
        scope.launch {
            try {
                while (isActive) {
                    val incoming = endpoint.acceptNext() ?: break
                    launch { serve(incoming) }
                }
            } catch (t: Throwable) {
                if (!closed) inbound.put(Failure(t))
            } finally {
                inbound.put(SHUTDOWN)
            }
        }
    }

    private suspend fun CoroutineScope.serve(incoming: computer.iroh.Incoming) {
        // `incoming` owns a native handle and must be released on the success path too, not just
        // when the handshake fails -- otherwise every accepted peer leaks one until the Cleaner
        // happens to run.
        val connection = try {
            incoming.use { pending -> pending.accept().use { it.connect() } }
        } catch (_: Throwable) {
            return
        }
        connections.add(connection)
        try {
            while (isActive) {
                // Throws once the peer goes away; that is the normal end of a peer's lifetime.
                val bi = connection.acceptBi()
                inbound.put(IrohStream(bi))
            }
        } catch (_: Throwable) {
            // fall through to cleanup
        } finally {
            connections.remove(connection)
            runCatching { connection.close() }
        }
    }

    /**
     * Blocks until the next inbound stream arrives.
     *
     * @return the next stream, or `null` once this host has been closed.
     * @throws IOException if the accept loop itself failed
     */
    @Throws(IOException::class)
    fun accept(): IrohStream? {
        while (true) {
            when (val next = inbound.take()) {
                is IrohStream -> return next
                is Failure -> throw IOException("iroh host accept loop failed", next.cause)
                else -> {
                    // Keep the sentinel in place so every later caller also sees the shutdown.
                    inbound.put(SHUTDOWN)
                    return null
                }
            }
        }
    }

    /** Whether this host is still listening. */
    fun isRunning(): Boolean = !closed

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        synchronized(connections) {
            connections.forEach { runCatching { it.close() } }
            connections.clear()
        }
        // Drain anything already queued so the native handles are not just dropped on the floor.
        while (true) {
            val pending = inbound.poll() ?: break
            if (pending is IrohStream) runCatching { pending.close() }
        }
        runCatching { runBlocking { endpoint.shutdown() } }
        runCatching { endpoint.close() }
        inbound.put(SHUTDOWN)
    }

    private class Failure(val cause: Throwable)
}
