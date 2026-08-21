package io.github.maxomatic458.conduit.irohnet

import computer.iroh.EndpointOptions
import computer.iroh.RelayMode
import computer.iroh.presetN0

/**
 * Transport settings shared by [IrohHost] and [IrohClient].
 *
 * @param relayUrls relay servers to use, or empty for n0's defaults.
 */
data class IrohEndpointSettings(
    val alpn: String = IrohNet.DEFAULT_ALPN,
    val relayUrls: List<String> = emptyList(),
) {

    internal fun relayMode(): RelayMode =
        if (relayUrls.isEmpty()) RelayMode.defaultMode() else RelayMode.customFromUrls(relayUrls)

    /**
     * @param secretKey the endpoint identity, or `null` for a temp one
     * @param accept ALPNs to accept
     */
    internal fun toEndpointOptions(secretKey: ByteArray?, accept: List<ByteArray>): EndpointOptions =
        EndpointOptions(
            preset = presetN0(),
            secretKey = secretKey,
            alpns = accept.ifEmpty { null },
            relayMode = relayMode(),
        )
}
