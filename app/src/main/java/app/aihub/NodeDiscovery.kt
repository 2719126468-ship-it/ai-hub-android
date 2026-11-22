package app.aihub

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeDiscovery(context: Context) {
    private val secrets = SecretStore(context)

    fun sources(): List<String> = runCatching {
        val array = org.json.JSONArray(secrets.read("sources_json") ?: "[]")
        (0 until array.length()).map { array.getString(it) }.sorted()
    }.getOrDefault(emptyList())

    fun addSource(raw: String): Boolean {
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return false
        secrets.write("sources_json", org.json.JSONArray(sources().toMutableSet().apply { add(value) }.toList()).toString())
        return true
    }

    fun removeSource(value: String) {
        secrets.write("sources_json", org.json.JSONArray(sources().filterNot { it == value }).toString())
    }

    fun searchPublicSources(): List<String> {
        val api = URL("https://api.github.com/search/repositories?q=free+v2ray+subscription+in%3Aname%2Cdescription%2Creadme&sort=updated&per_page=15")
        val connection = (api.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AI-Hub-Android/0.1")
        }
        try {
            require(connection.responseCode in 200..299) { "GitHub 搜索失败：HTTP ${connection.responseCode}" }
            val repositories = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("items") ?: return emptyList()
            val candidates = linkedSetOf<String>()
            val rawPattern = Regex("""https://raw\.githubusercontent\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/[^\s)"<>]+""")
            for (index in 0 until minOf(repositories.length(), 8)) {
                val repo = repositories.getJSONObject(index)
                val fullName = repo.optString("full_name")
                val readmeUrl = URL("https://api.github.com/repos/$fullName/readme")
                val readmeConnection = (readmeUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AI-Hub-Android/0.1")
                }
                try {
                    if (readmeConnection.responseCode !in 200..299) continue
                    val readme = org.json.JSONObject(readmeConnection.inputStream.bufferedReader().use { it.readText() })
                    val encoded = readme.optString("content")
                    if (encoded.isBlank()) continue
                    val markdown = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                    rawPattern.findAll(markdown).map { it.value.trimEnd('.', ',', ';') }
                        .filter { url -> url.contains("sub", true) || url.contains("v2ray", true) || url.contains("clash", true) || url.contains("node", true) }
                        .forEach { candidates.add(it) }
                } finally { readmeConnection.disconnect() }
                if (candidates.size >= 30) break
            }
            return candidates.toList()
        } finally { connection.disconnect() }
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
            return parseLines(decodeSubscription(body), source)
        } finally { connection.disconnect() }
    }

    private fun decodeSubscription(body: String): String {
        if (body.contains("://")) return body
        return runCatching {
            String(Base64.decode(body.filterNot { it.isWhitespace() }, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8)
        }.getOrDefault(body)
    }

    private fun parseLines(text: String, source: String): List<ProxyNode> {
        val result = linkedMapOf<String, ProxyNode>()
        text.lineSequence().map { it.trim() }.filter { it.contains("://") }.forEach { line ->
            runCatching { parseShareLink(line, source) }.getOrNull()?.let { result[it.id] = it }
        }
        return result.values.toList()
    }

    private fun parseShareLink(link: String, source: String): ProxyNode? {
        val scheme = link.substringBefore("://").lowercase()
        if (scheme == "vmess") {
            val payload = link.substringAfter("://").substringBefore('#')
            val json = JSONObject(String(Base64.decode(payload, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8))
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
            val parsed = runCatching { URI("ss://$endpoint") }.getOrNull()
            host = parsed?.host
            port = parsed?.port ?: -1
            if (host.isNullOrBlank() || port <= 0) {
                val decoded = runCatching { String(Base64.decode(authority, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8) }.getOrNull()
                val decodedUri = decoded?.let { runCatching { URI("ss://$it") }.getOrNull() }
                host = decodedUri?.host
                port = decodedUri?.port ?: -1
            }
        }
        if (host.isNullOrBlank() || port !in 1..65535) return null
        val label = uri.fragment?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() } ?: scheme.uppercase()
        return createNode(link, source, scheme, host, port, label)
    }

    private fun createNode(link: String, source: String, scheme: String, host: String, port: Int, name: String): ProxyNode {
        val id = MessageDigest.getInstance("SHA-256").digest(link.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        return ProxyNode(id, name, scheme, host, port, link, source)
    }

    fun probeEndpoints(nodes: List<ProxyNode>, onResult: (ProxyNode) -> Unit) {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = nodes.map { node -> pool.submit(Callable {
                val started = System.nanoTime()
                val reachable = runCatching {
                    java.net.Socket().use { it.connect(java.net.InetSocketAddress(node.host, node.port), 3500) }
                    true
                }.getOrDefault(false)
                node.copy(endpointLatencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), endpointReachable = reachable)
            }) }
            futures.forEach { onResult(it.get()) }
        } finally { pool.shutdownNow() }
    }
}
