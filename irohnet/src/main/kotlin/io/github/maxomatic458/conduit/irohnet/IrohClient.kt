package io.github.maxomatic458.conduit.irohnet

import computer.iroh.Connection
import computer.iroh.Endpoint
import computer.iroh.EndpointAddr
import computer.iroh.EndpointId
import computer.iroh.EndpointOptions
import computer.iroh.presetN0
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * The joining half of the tunnel: outbound-only iroh endpoint.
 */
class IrohClient private constructor(
    private val endpoint: Endpoint,
    private val alpn: ByteArray,
) : AutoCloseable {

    @Volatile private var closed = false

    /** This client's own connect ID */
    @get:JvmName("endpointId")
    val endpointId: String by lazy {
        irohCall("read iroh client endpoint id") { endpoint.id().use { it.toString() } }
    }

    companion object {
        /** Binds an outbound endpoint. Blocks until ready to dial. */
        @JvmStatic
        @JvmOverloads
        fun open(settings: IrohEndpointSettings = IrohEndpointSettings()): IrohClient {
            val alpnBytes = settings.alpn.toByteArray(Charsets.UTF_8)
            val endpoint = irohCall("bind iroh client endpoint") {
                runBlocking {
                    Endpoint.bind(settings.toEndpointOptions(null, emptyList()))
                }
            }
            return IrohClient(endpoint, alpnBytes)
        }
    }

    /**
     * Dials [endpointId] and connects

     * @param endpointId 64-char hex or 52-char base32 connect ID
     * @throws IOException if the ID is malformed, the host is unreachable, or [timeoutMillis] elapses
     */
    @Throws(IOException::class)
    fun connect(endpointId: String, timeoutMillis: Long): IrohPeer {
        check(!closed) { "iroh client endpoint is closed" }
        // Validate ID
        val canonical = IrohNet.normalizeEndpointId(endpointId)
        val connection = try {
            runBlocking {
                withTimeout(timeoutMillis) {
                    EndpointId.fromString(canonical).use { id ->
                        EndpointAddr(id, null, emptyList()).use { addr ->
                            endpoint.connect(addr, alpn)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException(
                "timed out after ${timeoutMillis}ms connecting to $endpointId -- " +
                    "the host may be offline or not running the mod",
                e,
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted while connecting to $endpointId", e)
        } catch (t: Throwable) {
            throw IOException("failed to connect to $endpointId: ${t.message ?: t.toString()}", t)
        }
        return IrohPeer(connection)
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { runBlocking { endpoint.shutdown() } }
        runCatching { endpoint.close() }
    }
}

/**
 * An established connection to one host.
 *
 * Each Minecraft TCP connection to the tunnel becomes its own [IrohStream] via [openStream]
 */
class IrohPeer internal constructor(private val connection: Connection) : AutoCloseable {

    @Volatile private var closed = false

    /** opens bidirectional stream to the host. */
    @Throws(IOException::class)
    fun openStream(): IrohStream {
        check(!closed) { "iroh peer connection is closed" }
        val bi = irohCall("open iroh stream") { runBlocking { connection.openBi() } }
        return IrohStream(bi)
    }

    /**
     * Snapshot of how this connection is currently routed.
     */
    @Throws(IOException::class)
    fun info(): IrohConnectionInfo = irohCall("read iroh connection info") {
        val paths = connection.paths().map {
            IrohPathInfo(
                remoteAddress = it.remoteAddr,
                selected = it.isSelected,
                relay = it.isRelay,
                rttMillis = it.rttMs.toLong(),
                bytesSent = it.stats.udpTxBytes.toLong(),
                bytesReceived = it.stats.udpRxBytes.toLong(),
            )
        }
        val stats = connection.stats()
        IrohConnectionInfo(
            remoteId = connection.remoteId().use { it.toString() },
            mode = when {
                paths.isEmpty() -> IrohConnectionMode.CONNECTING
                paths.all { it.relay } -> IrohConnectionMode.RELAYED
                paths.none { it.relay } -> IrohConnectionMode.DIRECT
                else -> IrohConnectionMode.MIXED
            },
            rttMillis = connection.rtt()?.toLong() ?: -1L,
            bytesSent = stats.udpTxBytes,
            bytesReceived = stats.udpRxBytes,
            paths = paths,
        )
    }

    fun isAlive(): Boolean =
        !closed && runCatching { connection.closeReason() == null }.getOrDefault(false)

    override fun close() {
        if (closed) return
        closed = true
        runCatching { connection.close(0L, ByteArray(0)) }
        runCatching { connection.close() }
    }
}
