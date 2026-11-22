package app.aihub

data class ProxyNode(
    val id: String,
    val name: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val shareLink: String,
    val source: String,
    val endpointLatencyMs: Long? = null,
    val endpointReachable: Boolean? = null
) {
    fun summary(): String = "$scheme  ·  $host:$port"
}
