package io.github.maxomatic458.conduit.irohnet

/**
 * Mode of the iroh connection
 */
enum class IrohConnectionMode {
    CONNECTING,
    DIRECT,
    MIXED,
    RELAYED,
}

/**
 * One candidate network path to a peer.
 *
 * @param remoteAddress `ip:port` for a direct path, or the relay URL for a relayed one
 * @param selected whether QUIC is currently sending application data over this path
 * @param rttMillis round-trip estimate sampled from live QUIC state
 */
data class IrohPathInfo(
    val remoteAddress: String,
    val selected: Boolean,
    val relay: Boolean,
    val rttMillis: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
)

/** A point-in-time snapshot of one peer connection. */
data class IrohConnectionInfo(
    /** The peer's connect ID, 64 lowercase hex chars. */
    val remoteId: String,
    val mode: IrohConnectionMode,
    /** Round-trip time in milliseconds, or `-1` when not yet known. */
    val rttMillis: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val paths: List<IrohPathInfo>,
) {

    /** The path currently carrying data, or `null` while still connecting. */
    fun selectedPath(): IrohPathInfo? = paths.firstOrNull { it.selected }
}
