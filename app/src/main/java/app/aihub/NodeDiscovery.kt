package app.aihub

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeDiscovery(context: Context) {
    private val prefs = context.getSharedPreferences("node_sources_v1", Context.MODE_PRIVATE)

    fun sources(): List<String> = prefs.getStringSet("sources", emptySet())
        ?.toList()?.sorted().orEmpty()

    fun addSource(raw: String): Boolean {
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return false
        val next = sources().toMutableSet().apply { add(value) }
        prefs.edit().putStringSet("sources", next).apply()
        return true
    }

    fun removeSource(value: String) {
        prefs.edit().putStringSet("sources", sources().toMutableSet().apply { remove(value) }).apply()
    }

    fun discover(source: String): List<ProxyNode> {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "AI-Hub-Android/0.1")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "订阅服务器返回 HTTP $code" }
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 3 * 1024 * 1024) { "订阅内容超过 3 MB 限制" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            val body = bytes.toString(Charsets.UTF_8).trim()
            val decoded = decodeSubscription(body)
            return parseLines(decoded, source)
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeSubscription(body: String): String {
        if (body.contains("://")) return body
        val compact = body.filterNot { it.isWhitespace() }
        return runCatching {
            String(Base64.decode(compact, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(body)
    }

    private fun parseLines(text: String, source: String): List<ProxyNode> {
        val result = linkedMapOf<String, ProxyNode>()
        text.lineSequence().map { it.trim() }.filter { it.contains("://") }.forEach { line ->
            val node = runCatching { parseShareLink(line, source) }.getOrNull()
            if (node != null) result[node.id] = node
        }
        return result.values.toList()
    }

    private fun parseShareLink(link: String, source: String): ProxyNode? {
        val scheme = link.substringBefore("://").lowercase()
        if (scheme == "vmess") {
            val payload = link.substringAfter("://").substringBefore('#')
            val jsonText = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
            val json = JSONObject(jsonText)
            val host = json.optString("add")
            val port = json.optString("port").toIntOrNull() ?: return null
            return createNode(link, source, scheme, host, port, json.optString("ps", "VMess"))
        }
        if (scheme !in setOf("vless", "trojan", "ss", "hysteria2", "hy2", "tuic", "socks", "socks5", "http", "https", "anytls")) return null
        val uri = URI(link)
        var host = uri.host
        var port = uri.port
        if ((host.isNullOrBlank() || port <= 0) && scheme == "ss") {
            val authority = link.substringAfter("://").substringBefore('#').substringBefore('?')
            val endpoint = authority.substringAfterLast('@')
            val endpointUri = runCatching { URI("ss://$endpoint") }.getOrNull()
            host = endpointUri?.host
            port = endpointUri?.port ?: -1
            if (host.isNullOrBlank() || port <= 0) {
                val decoded = runCatching {
                    String(Base64.decode(authority, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                }.getOrNull()
                val decodedUri = decoded?.let { runCatching { URI("ss://$it") }.getOrNull() }
                host = decodedUri?.host
                port = decodedUri?.port ?: -1
            }
        }
        if (host.isNullOrBlank() || port !in 1..65535) return null
        val label = runCatching { URI(link).fragment }.getOrNull()?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        }?.takeIf { it.isNotBlank() } ?: scheme.uppercase()
        return createNode(link, source, scheme, host, port, label)
    }

    private fun createNode(link: String, source: String, scheme: String, host: String, port: Int, name: String): ProxyNode {
        val id = MessageDigest.getInstance("SHA-256")
            .digest(link.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        return ProxyNode(id, name, scheme, host, port, link, source)
    }

    fun probeEndpoints(nodes: List<ProxyNode>, onResult: (ProxyNode) -> Unit) {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = nodes.map { node ->
                pool.submit(Callable {
                    val started = System.nanoTime()
                    val reachable = runCatching {
                        java.net.Socket().use { socket ->
                            socket.connect(java.net.InetSocketAddress(node.host, node.port), 3500)
                        }
                        true
                    }.getOrDefault(false)
                    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                    node.copy(endpointLatencyMs = elapsed, endpointReachable = reachable)
                })
            }
            futures.forEach { future -> onResult(future.get()) }
        } finally {
            pool.shutdownNow()
        }
    }
}
