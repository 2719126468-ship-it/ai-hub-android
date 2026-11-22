package app.aihub

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class DiagnosticsActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        result = TextView(this).apply { text = "此版本仅检查手机当前网络直连 Google 的 HTTPS 可达性。\n尚未导入节点，也不会启动 VPN。" }
        val button = Button(this).apply {
            text = "检查 Google HTTPS"
            setOnClickListener { runCheck() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(button)
            addView(result)
        })
    }

    private fun runCheck() {
        result.text = "正在检查……"
        executor.execute {
            val message = try {
                val connection = (URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection)
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                val code = connection.responseCode
                connection.disconnect()
                "HTTP $code：${if (code == 204) "Google HTTPS 检查通过（当前系统路由）" else "收到响应，但未达到 204 标准"}"
            } catch (e: Exception) {
                "连接失败：${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            }
            runOnUiThread { result.text = message + "\n注意：这是直连检测，不代表某个代理节点可用。" }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
