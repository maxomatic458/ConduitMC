package io.github.maxomatic458.conduit.irohnet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class IrohNetTest {

    @Test
    fun `real server addresses are never mistaken for connect ids`() {
        listOf(
            "127.0.0.1",
            "localhost",
            "192.168.1.10",
            "mc.hypixel.net",
            "play.example.com:25566",
            "::1",
            "2001:db8::1",
            "",
            "   ",
            "0".repeat(52),
            "a".repeat(63),
            "a".repeat(65),
        ).forEach { assertFalse(IrohNet.isEndpointId(it), "expected '$it' to be treated as a host") }
    }

    @Test
    fun `both encodings of a connect id are recognised`() {
        assertTrue(IrohNet.isEndpointId("a".repeat(64)))
        assertTrue(IrohNet.isEndpointId("A".repeat(64)))
        assertTrue(IrohNet.isEndpointId("  " + "f".repeat(64) + "  "), "must tolerate pasted whitespace")
        // 52-char base32, the alternative form `EndpointId.fromString` accepts.
        assertTrue(IrohNet.isEndpointId("b".repeat(52)))
    }


    @Test
    fun `a connect id is too long to survive Minecraft's own address validation`() {
        // Documents *why* MixinServerAddress has to exist. Vanilla's ServerAddress.isValidAddress
        // runs the text through IDN.toASCII, which enforces the 63-char DNS label limit, so the
        // canonical 64-char id is rejected and the Join / Done buttons stay greyed out.
        // If this ever stops throwing, the mixin is dead weight and can go.
        assertThrows(IllegalArgumentException::class.java) { java.net.IDN.toASCII("a".repeat(64)) }

        // The 52-char base32 form slips under the limit, which is why the failure looked
        // inconsistent depending on which encoding was pasted.
        java.net.IDN.toASCII("b".repeat(52))
    }

    @Test
    fun `the hard-coded default relays still match iroh's own`() {
        assumeTrue(IrohNet.isSupported(), "iroh unsupported here: ${IrohNet.unsupportedReason()}")

        // The config ships these URLs literally, so they must stay in step with what iroh would
        // have used implicitly. If n0 adds, removes or renames a relay, this fails and the list
        // needs updating -- far better than silently pinning players to a stale set.
        assertEquals(
            computer.iroh.RelayMode.defaultMode().relayMap().urls().sorted(),
            IrohNet.DEFAULT_RELAY_URLS.sorted(),
        )

        // Listing them explicitly must be equivalent to leaving it implicit, per-relay settings
        // included -- the built-in map enables QUIC address discovery, and a bare URL list has to
        // reproduce that or we would quietly downgrade everyone's connectivity.
        val builtin = computer.iroh.RelayMode.defaultMode().relayMap()
        val explicit = computer.iroh.RelayMode.customFromUrls(IrohNet.DEFAULT_RELAY_URLS).relayMap()
        IrohNet.DEFAULT_RELAY_URLS.forEach { url ->
            assertEquals(builtin.get(url), explicit.get(url), "relay config differs for $url")
        }
    }

    @Test
    fun `a persisted secret key yields a stable connect id`() {
        assumeTrue(IrohNet.isSupported(), "iroh unsupported here: ${IrohNet.unsupportedReason()}")

        val secret = IrohNet.generateSecretKey()
        assertEquals(32, secret.size)

        val id = IrohNet.endpointIdOf(secret)

        assertEquals(id, IrohNet.endpointIdOf(secret), "same key must always give the same id")
        assertTrue(id.matches(Regex("^[0-9a-f]{64}$")), "expected 64 lowercase hex chars, got '$id'")
        assertTrue(IrohNet.isEndpointId(id), "a generated id must be recognised as one")
        assertEquals(id, IrohNet.normalizeEndpointId(id.uppercase()))

        assertFalse(id == IrohNet.endpointIdOf(IrohNet.generateSecretKey()))
    }
}
