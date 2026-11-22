package app.aihub

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.HttpURLConnection

/**
 * Performs an HTTPS reachability check through the local SOCKS listener.
 *
 * The connection is always created with an explicit SOCKS Proxy instance.
 * There is intentionally no direct-connection retry or fallback.
 *
 * This verifies an HTTPS request through a running local proxy; it does not
 * independently prove that a remote node is trustworthy or stable over time.
 */
object ProxyEgressTester {
    data class Result(
        val success: Boolean,
        val latencyMs: Long,
        val httpStatus: Int? = null,
        val detail: String
    )

    fun checkHttps(
        socksPort: Int,
        endpoint: String = "https://www.google.com/generate_204",
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 10_000
    ): Result {
        require(socksPort in 1..65535) { "本地 SOCKS 端口无效" }
        require(connectTimeoutMs > 0 && readTimeoutMs > 0) { "网络超时设置无效" }
        val url = URL(endpoint)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "出口检测地址必须使用 HTTPS"
        }

        val startedAt = System.nanoTime()
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", socksPort)
        )
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection(proxy) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Connection", "close")
            }
            val status = connection.responseCode
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
            if (status == 204) {
                Result(true, elapsed, status, "HTTPS 请求经本地 SOCKS 代理成功")
            } else {
                Result(false, elapsed, status, "代理请求返回 HTTP $status（预期 204）")
            }
        } catch (error: Exception) {
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
            Result(
                false,
                elapsed,
                null,
                error.message?.take(240) ?: error.javaClass.simpleName
            )
        } finally {
            connection?.disconnect()
        }
    }
}
