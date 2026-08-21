package io.github.maxomatic458.conduit.irohnet

import computer.iroh.EndpointId
import computer.iroh.SecretKey
import java.io.IOException

/**
 * Entry point to the iroh facade.
 *
 * Everything the mod needs that does not require a live endpoint lives here: platform support
 * detection, key handling, and recognising a connect ID typed into the server address field.
 *
 * All of it is safe to call before [isSupported] has been checked -- the class itself never
 * touches a native symbol until you ask it to.
 */
object IrohNet {

    /** ALPN used by the tunnel. Bump the suffix on any wire-incompatible change. */
    const val DEFAULT_ALPN: String = "mc-iroh/1"

    /**
     * The relay servers used out of the box: n0's public production relays.
     */
    @JvmField
    val DEFAULT_RELAY_URLS: List<String> = listOf(
        "https://aps1-1.relay.n0.iroh.link./",
        "https://euc1-1.relay.n0.iroh.link./",
        "https://use1-1.relay.n0.iroh.link./",
        "https://usw1-1.relay.n0.iroh.link./",
    )

    private val HEX_ID = Regex("^[0-9a-fA-F]{64}$")
    private val BASE32_ID = Regex("^[A-Za-z2-7]{52}$")

    /**
     * Platforms for which `computer.iroh:iroh` bundles a native library.
     *
     * The artifact ships: `linux-x86-64`, `linux-aarch64`, `darwin-aarch64` and
     * `win32-x86-64`. Other platforms unsupported
     */
    private val support: Support by lazy { probe() }

    private class Support(val ok: Boolean, val reason: String?)

    @JvmStatic
    fun isSupported(): Boolean = support.ok

    /** Human-readable explanation of why [isSupported] is `false`, or `null` when it is `true`. */
    @JvmStatic
    fun unsupportedReason(): String? = support.reason

    private fun probe(): Support {
        val os = System.getProperty("os.name", "").lowercase()
        val arch = System.getProperty("os.arch", "").lowercase()
        if (os.contains("mac") || os.contains("darwin")) {
            val aarch64 = arch == "aarch64" || arch == "arm64"
            if (!aarch64) {
                return Support(
                    false,
                    "iroh ships no native library for Intel macOS (os.arch=$arch); " +
                        "Apple Silicon is required on macOS",
                )
            }
        }

        return try {
            SecretKey.generate().use { it.public().use { id -> id.toString() } }
            Support(true, null)
        } catch (t: Throwable) {
            Support(false, "failed to load the iroh native library ($os/$arch): $t")
        }
    }

    /** A random 32-byte Ed25519 secret key */
    @JvmStatic
    fun generateSecretKey(): ByteArray = irohCall("generate secret key") {
        SecretKey.generate().use { it.toBytes() }
    }

    /**
     * The connect ID (64 lowercase hex chars) derived from [secretKey].
     */
    @JvmStatic
    fun endpointIdOf(secretKey: ByteArray): String = irohCall("derive endpoint id") {
        require(secretKey.size == 32) { "secret key must be 32 bytes, got ${secretKey.size}" }
        SecretKey.fromBytes(secretKey).use { key -> key.public().use { it.toString() } }
    }

    /**
     * Checks that [urls] are usable as relay servers.
     *
     * @return `null` if they are fine, otherwise a message explaining the problem.
     */
    @JvmStatic
    fun validateRelayUrls(urls: List<String>): String? {
        if (urls.isEmpty()) {
            return null
        }
        return try {
            computer.iroh.RelayMode.customFromUrls(urls)
            null
        } catch (t: Throwable) {
            t.message ?: t.toString()
        }
    }

    @JvmStatic
    fun defaultRelayUrls(): List<String> = DEFAULT_RELAY_URLS

    @JvmStatic
    fun isEndpointId(value: String?): Boolean {
        val s = value?.trim() ?: return false
        return HEX_ID.matches(s) || BASE32_ID.matches(s)
    }

    /**
     * Canonicalise a connect ID to its 64-char lowercase hex form.
     *
     * @throws IOException if [value] is not a valid endpoint id
     */
    @JvmStatic
    fun normalizeEndpointId(value: String): String = irohCall("parse endpoint id") {
        val trimmed = value.trim()
        val candidate = if (HEX_ID.matches(trimmed)) trimmed.lowercase() else trimmed
        EndpointId.fromString(candidate).use { it.toString() }
    }
}

/**
 * Runs an iroh call, translating its failure modes into [IOException].
 *
 * `IrohException` extends `kotlin.Exception` and native-loading problems arrive as `Error`s.
 */
internal inline fun <T> irohCall(what: String, block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("interrupted while trying to $what", e)
    } catch (t: Throwable) {
        throw IOException("failed to $what: ${t.message ?: t.toString()}", t)
    }
