package io.github.maxomatic458.conduit.irohnet

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A real end-to-end round trip over the iroh network: two endpoints in one JVM, dialled by bare
 * connect ID through n0 discovery -- exactly the path a player takes.
 */
class IrohNetworkTest {

    private val settings = IrohEndpointSettings(alpn = "mc-iroh-test/1")

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun `a bare connect id round-trips a payload and propagates half-close`() {
        assumeTrue(IrohNet.isSupported(), "iroh unsupported here: ${IrohNet.unsupportedReason()}")

        val secret = IrohNet.generateSecretKey()
        val expectedId = IrohNet.endpointIdOf(secret)

        IrohHost.open(secret, settings).use { host ->
            assertEquals(expectedId, host.endpointId, "host must bind under its persisted identity")

            // Echo server
            val echo = thread(name = "test-echo", isDaemon = true) {
                val stream = host.accept() ?: return@thread
                stream.use {
                    val received = ArrayList<Byte>()
                    while (true) {
                        val chunk = it.read(16 * 1024) ?: break
                        received.addAll(chunk.toList())
                    }
                    // Reaching here proves the client's finishSend() surfaced EOF.
                    it.write(received.toByteArray())
                    it.finishSend()
                }
            }

            IrohClient.open(settings).use { client ->
                client.connect(host.endpointId, 60_000).use { peer ->
                    val payload = "hello from minecraft".repeat(500).toByteArray()

                    peer.openStream().use { stream ->
                        stream.write(payload)
                        stream.finishSend()

                        val echoed = ArrayList<Byte>()
                        while (true) {
                            val chunk = stream.read(16 * 1024) ?: break
                            echoed.addAll(chunk.toList())
                        }
                        assertArrayEquals(payload, echoed.toByteArray())
                        // A second read past EOF must stay at EOF rather than blocking or throwing
                        assertNull(stream.read(16 * 1024))
                    }
                }
            }
            echo.join(30_000)
        }
    }

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun `explicit relay urls still bind and connect`() {
        assumeTrue(IrohNet.isSupported(), "iroh unsupported here: ${IrohNet.unsupportedReason()}")

        val staging = IrohEndpointSettings(
            alpn = "mc-iroh-test/1",
            relayUrls = listOf(
                "https://euc1-1.staging-relay.n0.iroh.link./",
                "https://use1-1.staging-relay.n0.iroh.link./",
            ),
        )

        IrohHost.open(IrohNet.generateSecretKey(), staging).use { host ->
            val echo = thread(name = "test-staging-echo", isDaemon = true) {
                val stream = host.accept() ?: return@thread
                stream.use {
                    while (true) {
                        val chunk = it.read(16 * 1024) ?: break
                        it.write(chunk)
                    }
                    it.finishSend()
                }
            }

            IrohClient.open(staging).use { client ->
                client.connect(host.endpointId, 60_000).use { peer ->
                    peer.openStream().use { stream ->
                        val payload = "relayed hello".toByteArray()
                        stream.write(payload)
                        stream.finishSend()
                        val echoed = ArrayList<Byte>()
                        while (true) {
                            val chunk = stream.read(1024) ?: break
                            echoed.addAll(chunk.toList())
                        }
                        assertArrayEquals(payload, echoed.toByteArray())
                    }
                    println("staging relay paths: ${peer.info().paths}")
                }
            }
            echo.join(30_000)
        }
    }

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun `one peer carries many streams`() {
        assumeTrue(IrohNet.isSupported(), "iroh unsupported here: ${IrohNet.unsupportedReason()}")

        // A server-list ping followed by a join reuses the same connection, so multiple sequential
        // streams on one peer must work.
        lateinit var server: Thread
        IrohHost.open(IrohNet.generateSecretKey(), settings).use { host ->
            server = thread(name = "test-multi-echo", isDaemon = true) {
                while (true) {
                    val stream = host.accept() ?: break
                    thread(isDaemon = true) {
                        stream.use {
                            while (true) {
                                val chunk = it.read(16 * 1024) ?: break
                                it.write(chunk)
                            }
                            it.finishSend()
                        }
                    }
                }
            }

            IrohClient.open(settings).use { client ->
                client.connect(host.endpointId, 60_000).use { peer ->
                    repeat(3) { i ->
                        peer.openStream().use { stream ->
                            val payload = "stream-$i".toByteArray()
                            stream.write(payload)
                            stream.finishSend()
                            val echoed = ArrayList<Byte>()
                            while (true) {
                                val chunk = stream.read(1024) ?: break
                                echoed.addAll(chunk.toList())
                            }
                            assertArrayEquals(payload, echoed.toByteArray())
                        }
                    }
                }
            }
            // Closing the host makes accept() return null, which ends the loop cleanly.
        }.also { server.join(10_000) }
    }
}
