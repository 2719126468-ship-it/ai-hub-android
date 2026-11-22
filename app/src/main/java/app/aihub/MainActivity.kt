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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        content.addView(TextView(this).apply {
            text = "AI Hub"
            textSize = 28f
            setPadding(0, 0, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = "全球 AI 服务入口 · Android 8.1+"
            textSize = 14f
            setPadding(0, 0, 0, 20)
        })

        Providers.all.forEach { provider ->
            content.addView(Button(this).apply {
                text = "${provider.name}  ·  ${provider.region}"
                isAllCaps = false
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, WebActivity::class.java).apply {
                        putExtra(WebActivity.EXTRA_PROVIDER_ID, provider.id)
                    })
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 })
        }

        content.addView(Button(this).apply {
            text = "网络诊断（直连检查，代理检测尚未实现）"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
            }
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
