package io.github.maxomatic458.conduit.irohnet

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * End-to-end probe against a *running* Minecraft server that is hosting over iroh.
 *
 * Speaks the real server-list ping handshake through the tunnel.
 * player path: iroh stream -> the host's loopback socket -> vanilla `ServerConnectionListener` ->
 * status response back through the tunnel.
 */
class MinecraftPingProbeTest {

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun `server list ping succeeds through the tunnel`() {
        val connectId = System.getenv("MC_PROBE_ID")
        assumeTrue(!connectId.isNullOrBlank(), "set MC_PROBE_ID to a running host's connect ID")
        assumeTrue(IrohNet.isSupported(), "iroh unsupported here: ${IrohNet.unsupportedReason()}")

        IrohClient.open().use { client ->
            client.connect(connectId!!.trim(), 60_000).use { peer ->
                peer.openStream().use { stream ->
                    // Handshake, next state 1 (status). The host name and port are what a vanilla
                    // client would have resolved to: the tunnels loopback endpoint.
                    stream.write(
                        packet(0x00) {
                            writeVarInt(-1) // protocol version is ignored for status queries
                            writeString("127.0.0.1")
                            writeUnsignedShort(25565)
                            writeVarInt(1)
                        },
                    )
                    stream.write(packet(0x00) {}) // status request
                    stream.finishSend()

                    val response = readAll(stream)
                    val json = parseStatusResponse(response)
                    assertTrue(
                        json.contains("\"version\"") && json.contains("\"players\""),
                        "expected a Minecraft status response, got: ${json.take(400)}",
                    )
                    println("Status response through the iroh tunnel: $json")
                }
            }
        }
    }

    private fun readAll(stream: IrohStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val chunk = stream.read(16 * 1024) ?: break
            buffer.write(chunk)
            runCatching { parseStatusResponse(buffer.toByteArray()) }.onSuccess { return buffer.toByteArray() }
        }
        return buffer.toByteArray()
    }

    /** Unwraps `[length][packet id 0x00][json string]`, throwing if the frame is incomplete. */
    private fun parseStatusResponse(bytes: ByteArray): String {
        val reader = VarIntReader(bytes)
        val length = reader.readVarInt()
        require(reader.remaining() >= length) { "frame incomplete" }
        require(reader.readVarInt() == 0x00) { "unexpected packet id" }
        return reader.readString()
    }

    private fun packet(id: Int, body: ByteArrayOutputStream.() -> Unit): ByteArray {
        val payload = ByteArrayOutputStream().apply { writeVarInt(id); body() }.toByteArray()
        return ByteArrayOutputStream().apply { writeVarInt(payload.size); write(payload) }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var remaining = value
        while (true) {
            if (remaining and 0x7F.inv() == 0) {
                write(remaining)
                return
            }
            write((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
    }

    private fun ByteArrayOutputStream.writeString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        writeVarInt(encoded.size)
        write(encoded)
    }

    private fun ByteArrayOutputStream.writeUnsignedShort(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private class VarIntReader(private val bytes: ByteArray) {
        private var index = 0

        fun remaining(): Int = bytes.size - index

        fun readVarInt(): Int {
            var result = 0
            var shift = 0
            while (true) {
                require(index < bytes.size) { "frame incomplete" }
                val byte = bytes[index++].toInt()
                result = result or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                require(shift < 35) { "VarInt too long" }
            }
        }

        fun readString(): String {
            val length = readVarInt()
            require(remaining() >= length) { "frame incomplete" }
            return String(bytes, index, length, Charsets.UTF_8).also { index += length }
        }
    }
}
