package app.aihub

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/**
 * Converts a single imported share link into a minimal sing-box config.
 *
 * This deliberately does not claim that a node works: native config validation
 * checks schema/semantics only. Real proxy egress is a separate runtime step.
 */
object SingBoxConfigBuilder {
    data class BuiltConfig(val json: String, val outboundType: String)

    fun build(node: ProxyNode, socksPort: Int = 20808): BuiltConfig {
        require(socksPort in 1024..65535) { "本地 SOCKS 端口无效" }
        val link = node.shareLink.trim()
        val scheme = link.substringBefore("://").lowercase()
        val outbound = when (scheme) {
            "vless" -> vless(link)
            "vmess" -> vmess(link)
            "trojan" -> trojan(link)
            "ss" -> shadowsocks(link)
            "hysteria2", "hy2" -> hysteria2(link)
            "tuic" -> tuic(link)
            "anytls" -> anytls(link)
            "socks", "socks5" -> socks(link)
            "http", "https" -> http(link)
            else -> error("暂不支持协议：$scheme")
        }.put("tag", "proxy")

        val config = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("inbounds", JSONArray().put(
                JSONObject()
                    .put("type", "socks")
                    .put("tag", "local-socks")
                    .put("listen", "127.0.0.1")
                    .put("listen_port", socksPort)
            ))
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put("route", JSONObject().put("final", "proxy"))
        return BuiltConfig(config.toString(), scheme)
    }

    private fun vless(link: String): JSONObject {
        val uri = URI(link)
        val uuid = uri.rawUserInfo?.substringBefore(':').orEmpty()
        require(uuid.isNotBlank()) { "VLESS 缺少 UUID" }
        val query = query(uri)
        val result = serverBase(uri).put("type", "vless").put("uuid", decode(uuid))
        query["flow"]?.takeIf { it.isNotBlank() }?.let { result.put("flow", it) }
        val encryption = query["encryption"]
        if (!encryption.isNullOrBlank() && encryption != "none") result.put("encryption", encryption)
        addTls(result, query)
        addTransport(result, query)
        return result
    }

    private fun vmess(link: String): JSONObject {
        val payload = link.substringAfter("://").substringBefore('#').substringBefore('?')
        val data = JSONObject(String(decodeBase64(payload), Charsets.UTF_8))
        val host = data.optString("add")
        val port = data.optString("port").toIntOrNull() ?: -1
        val uuid = data.optString("id")
        require(host.isNotBlank() && port in 1..65535 && uuid.isNotBlank()) { "VMess 节点信息不完整" }
        val result = JSONObject()
            .put("type", "vmess")
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", port)
            .put("uuid", uuid)
            .put("security", data.optString("scy").ifBlank { "auto" })
            .put("alter_id", data.optInt("aid", 0))
        val tls = data.optString("tls")
        if (tls.equals("tls", true)) {
            result.put("tls", JSONObject().put("enabled", true)
                .put("server_name", data.optString("sni").ifBlank { data.optString("host") }))
        }
        val net = data.optString("net")
        if (net.isNotBlank() && net != "tcp") {
            val transport = JSONObject().put("type", when (net) {
                "ws" -> "ws"
                "grpc" -> "grpc"
                "http" -> "http"
                "httpupgrade" -> "httpupgrade"
                else -> error("暂不支持 VMess 传输方式：$net")
            })
            if (net == "ws" || net == "httpupgrade") {
                data.optString("path").takeIf { it.isNotBlank() }?.let { transport.put("path", it) }
                data.optString("host").takeIf { it.isNotBlank() }?.let {
                    transport.put("headers", JSONObject().put("Host", it))
                }
            }
            if (net == "grpc") data.optString("path").takeIf { it.isNotBlank() }?.let { transport.put("service_name", it) }
            result.put("transport", transport)
        }
        return result
    }

    private fun trojan(link: String): JSONObject {
        val uri = URI(link)
        val password = uri.rawUserInfo?.let(::decode).orEmpty()
        require(password.isNotBlank()) { "Trojan 缺少密码" }
        val query = query(uri)
        return serverBase(uri).put("type", "trojan").put("password", password).also {
            addTls(it, query, defaultEnabled = true)
            addTransport(it, query)
        }
    }

    private fun shadowsocks(link: String): JSONObject {
        val uri = URI(link)
        val authority = link.substringAfter("://").substringBefore('#').substringBefore('?')
        val at = authority.lastIndexOf('@')
        val decodedUser = if (at >= 0) decodeUserInfo(authority.substring(0, at)) else {
            val decoded = String(decodeBase64(authority), Charsets.UTF_8)
            decoded.substringBefore('@')
        }
        val endpoint = if (at >= 0) authority.substring(at + 1) else {
            val decoded = String(decodeBase64(authority), Charsets.UTF_8)
            decoded.substringAfter('@')
        }
        val method = decodedUser.substringBefore(':')
        val password = decodedUser.substringAfter(':', "")
        val endpointUri = URI("ss://$endpoint")
        require(method.isNotBlank() && password.isNotBlank()) { "Shadowsocks 缺少加密方法或密码" }
        require(endpointUri.host != null && endpointUri.port in 1..65535) { "Shadowsocks 地址无效" }
        require(query(uri)["plugin"].isNullOrBlank()) { "暂不支持带插件的 Shadowsocks 节点" }
        return JSONObject().put("type", "shadowsocks").put("server", endpointUri.host)
            .put("server_port", endpointUri.port).put("method", method).put("password", password)
    }

    private fun hysteria2(link: String): JSONObject {
        val uri = URI(link)
        val password = uri.rawUserInfo?.let(::decode).orEmpty()
        require(password.isNotBlank()) { "Hysteria2 缺少密码" }
        val query = query(uri)
        val result = serverBase(uri).put("type", "hysteria2").put("password", password)
        val obfs = query["obfs"]
        if (!obfs.isNullOrBlank()) result.put("obfs", JSONObject().put("type", obfs).put("password", query["obfs-password"].orEmpty()))
        addTls(result, query, defaultEnabled = true)
        return result
    }

    private fun tuic(link: String): JSONObject {
        val uri = URI(link)
        val credentials = uri.rawUserInfo.orEmpty().split(':', limit = 2)
        require(credentials.size == 2 && credentials.all { it.isNotBlank() }) { "TUIC 缺少 UUID 或密码" }
        val query = query(uri)
        val result = serverBase(uri).put("type", "tuic")
            .put("uuid", decode(credentials[0])).put("password", decode(credentials[1]))
            .put("congestion_control", query["congestion_control"] ?: "cubic")
            .put("udp_relay_mode", query["udp_relay_mode"] ?: "native")
        addTls(result, query, defaultEnabled = true)
        return result
    }

    private fun anytls(link: String): JSONObject {
        val uri = URI(link)
        val password = uri.rawUserInfo?.let(::decode).orEmpty()
        require(password.isNotBlank()) { "AnyTLS 缺少密码" }
        val result = serverBase(uri).put("type", "anytls").put("password", password)
        addTls(result, query(uri), defaultEnabled = true)
        return result
    }

    private fun socks(link: String): JSONObject {
        val uri = URI(link)
        val result = serverBase(uri).put("type", "socks")
        uri.rawUserInfo?.takeIf { it.isNotBlank() }?.let {
            val credentials = it.split(':', limit = 2)
            result.put("username", decode(credentials[0]))
            if (credentials.size == 2) result.put("password", decode(credentials[1]))
        }
        return result
    }

    private fun http(link: String): JSONObject {
        val uri = URI(link)
        val result = serverBase(uri).put("type", "http")
        uri.rawUserInfo?.takeIf { it.isNotBlank() }?.let {
            val credentials = it.split(':', limit = 2)
            result.put("username", decode(credentials[0]))
            if (credentials.size == 2) result.put("password", decode(credentials[1]))
        }
        if (uri.scheme.equals("https", true)) {
            result.put("tls", JSONObject().put("enabled", true).put("server_name", uri.host))
        }
        return result
    }

    private fun serverBase(uri: URI): JSONObject {
        require(!uri.host.isNullOrBlank() && uri.port in 1..65535) { "节点服务器地址或端口无效" }
        return JSONObject().put("server", uri.host).put("server_port", uri.port)
    }

    private fun addTls(target: JSONObject, query: Map<String, String>, defaultEnabled: Boolean = false) {
        val security = query["security"].orEmpty()
        val enabled = defaultEnabled || security == "tls" || security == "reality" || query["tls"] == "1"
        if (!enabled) return
        val tls = JSONObject().put("enabled", true)
        query["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
        tls.put("insecure", query["allowInsecure"] == "1" || query["insecure"] == "1")
        query["fp"]?.takeIf { it.isNotBlank() }?.let {
            tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
        }
        if (security == "reality") {
            val publicKey = query["pbk"].orEmpty()
            require(publicKey.isNotBlank()) { "Reality 节点缺少公钥 pbk" }
            tls.put("reality", JSONObject().put("enabled", true).put("public_key", publicKey)
                .put("short_id", query["sid"].orEmpty()))
        }
        target.put("tls", tls)
    }

    private fun addTransport(target: JSONObject, query: Map<String, String>) {
        val type = query["type"] ?: return
        if (type == "tcp" || type.isBlank()) return
        val transport = JSONObject().put("type", when (type) {
            "ws" -> "ws"
            "grpc" -> "grpc"
            "http" -> "http"
            "httpupgrade" -> "httpupgrade"
            else -> error("暂不支持传输方式：$type")
        })
        query["path"]?.takeIf { it.isNotBlank() }?.let {
            if (type == "grpc") transport.put("service_name", it) else transport.put("path", it)
        }
        query["host"]?.takeIf { it.isNotBlank() }?.let {
            if (type == "ws" || type == "httpupgrade") transport.put("headers", JSONObject().put("Host", it))
        }
        target.put("transport", transport)
    }

    private fun query(uri: URI): Map<String, String> =
        uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.associate {
            val key = decode(it.substringBefore('='))
            val value = decode(it.substringAfter('=', ""))
            key to value
        }

    private fun decodeUserInfo(value: String): String {
        val decoded = runCatching { String(decodeBase64(value), Charsets.UTF_8) }.getOrNull()
        return decoded?.takeIf { it.contains(':') } ?: decode(value)
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(value)

    private fun decodeBase64(value: String): ByteArray {
        val normalized = value.replace('-', '+').replace('_', '/')
        return Base64.decode(normalized, Base64.DEFAULT)
    }
}
