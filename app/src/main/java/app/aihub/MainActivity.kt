package app.aihub

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 28, 22, 24) }
        root.addView(TextView(this).apply { text = "AI Hub"; textSize = 30f; setPadding(0, 0, 0, 4) })
        root.addView(TextView(this).apply { text = "AI 服务 · 节点中心 · 媒体"; textSize = 14f; setPadding(0, 0, 0, 18) })
        root.addView(sectionTitle("AI 服务"))
        Providers.all.chunked(2).forEach { providers ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            providers.forEach { provider ->
                row.addView(Button(this).apply {
                    text = provider.name; isAllCaps = false; gravity = Gravity.CENTER
                    setOnClickListener { startActivity(Intent(this@MainActivity, WebActivity::class.java).putExtra(WebActivity.EXTRA_PROVIDER_ID, provider.id)) }
                }, LinearLayout.LayoutParams(0, 54, 1f).apply { marginEnd = 6; bottomMargin = 6 })
            }
            if (providers.size == 1) row.addView(TextView(this), LinearLayout.LayoutParams(0, 54, 1f))
            root.addView(row)
        }
        root.addView(sectionTitle("工具与媒体"))
        root.addView(action("节点发现与筛选") { NodeActivity::class.java })
        root.addView(action("Bilibili 媒体中心") { BilibiliActivity::class.java })
        root.addView(action("网络诊断") { DiagnosticsActivity::class.java })
        root.addView(TextView(this).apply {
            text = "当前节点测试仅检查服务器 TCP 端口，不代表代理握手或 Google 出口可用。"
            textSize = 12f; setPadding(0, 14, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun sectionTitle(value: String) = TextView(this).apply { text = value; textSize = 18f; setPadding(0, 18, 0, 8) }
    private fun action(label: String, target: () -> Class<*>) = Button(this).apply {
        text = label; isAllCaps = false
        setOnClickListener { startActivity(Intent(this@MainActivity, target())) }
    }
}
