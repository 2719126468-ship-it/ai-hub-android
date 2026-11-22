package app.aihub

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class NodeActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var discovery: NodeDiscovery
    private lateinit var sourceInput: EditText
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private val nodes = mutableListOf<ProxyNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discovery = NodeDiscovery(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        root.addView(TextView(this).apply { text = "节点发现与筛选"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "添加你信任的 HTTPS 订阅地址。支持解析 VLESS、VMess、Trojan、Shadowsocks、Hysteria2 等分享链接。"
            textSize = 14f; setPadding(0, 8, 0, 12)
        })
        sourceInput = EditText(this).apply { hint = "https://example.com/subscription"; maxLines = 1 }
        root.addView(sourceInput)
        root.addView(Button(this).apply { text = "保存并扫描订阅"; setOnClickListener { addAndScan() } })
        root.addView(Button(this).apply { text = "自动搜索公开订阅源"; setOnClickListener { searchPublicSources() } })
        root.addView(Button(this).apply { text = "重新扫描全部来源"; setOnClickListener { scanAll() } })
        root.addView(Button(this).apply { text = "测试节点服务器端口"; setOnClickListener { probe() } })
        status = TextView(this).apply { text = "来源与节点尚未扫描。" }
        root.addView(status)
        root.addView(TextView(this).apply { text = "订阅来源"; textSize = 18f; setPadding(0, 16, 0, 8) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(ScrollView(this).apply { addView(root) })
        renderSources()
    }

    private fun addAndScan() {
        val source = sourceInput.text.toString().trim()
        if (!discovery.addSource(source)) {
            status.text = "地址无效：仅接受有效 HTTPS 订阅地址。"
            return
        }
        sourceInput.text.clear()
        renderSources()
        scan(source)
    }

    private fun searchPublicSources() {
        status.text = "正在 GitHub 搜索公开订阅源……"
        io.execute {
            val result = runCatching { discovery.searchPublicSources() }
            runOnUiThread {
                result.onSuccess { candidates ->
                    if (candidates.isEmpty()) {
                        status.text = "没有找到可识别的公开订阅链接。可以手动添加可信来源。"
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("发现 ${candidates.size} 个候选来源")
                            .setMessage("公开来源未经安全审计，可能失效或包含不可信节点。只添加你愿意承担风险的来源。")
                            .setItems(candidates.toTypedArray()) { _, which ->
                                val selected = candidates[which]
                                if (discovery.addSource(selected)) {
                                    renderSources()
                                    scan(selected)
                                } else status.text = "来源地址无效。"
                            }
                            .setNegativeButton("取消", null)
                            .show()
                        status.text = "搜索完成。选择来源后才会下载订阅内容。"
                    }
                }.onFailure { status.text = "公开源搜索失败：${it.message}" }
            }
        }
    }

    private fun scanAll() {
        val all = discovery.sources()
        if (all.isEmpty()) { status.text = "请先添加一个 HTTPS 订阅来源。"; return }
        nodes.clear()
        renderNodes()
        all.forEach { scan(it) }
    }

    private fun scan(source: String) {
        status.text = "正在扫描订阅来源……"
        io.execute {
            val result = runCatching { discovery.discover(source) }
            runOnUiThread {
                result.onSuccess { found ->
                    nodes.removeAll { it.source == source }
                    nodes.addAll(found)
                    status.text = "本来源发现 ${found.size} 个可解析节点；当前共 ${nodes.size} 个。"
                    renderNodes()
                }.onFailure { status.text = "扫描失败：${it.message}" }
            }
        }
    }

    private fun probe() {
        if (nodes.isEmpty()) { status.text = "没有节点，请先扫描订阅。"; return }
        status.text = "正在并发测试节点服务器端口……"
        io.execute {
            val results = mutableListOf<ProxyNode>()
            runCatching { discovery.probeEndpoints(nodes) { results.add(it) } }
            runOnUiThread {
                nodes.clear()
                nodes.addAll(results.sortedWith(compareByDescending<ProxyNode> { it.endpointReachable == true }
                    .thenBy { it.endpointLatencyMs ?: Long.MAX_VALUE }))
                status.text = "端口探测完成：${nodes.count { it.endpointReachable == true }}/${nodes.size} 个端口可连接。"
                renderNodes()
            }
        }
    }

    private fun renderSources() {
        list.removeAllViews()
        discovery.sources().forEach { source ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = sourceLabel(source); textSize = 12f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "移除"
                setOnClickListener {
                    discovery.removeSource(source)
                    nodes.removeAll { it.source == source }
                    renderNodes()
                }
            })
            list.addView(row)
        }
        list.addView(TextView(this).apply { text = "节点列表"; textSize = 18f; setPadding(0, 16, 0, 8) })
        nodes.forEach { addNodeRow(it) }
    }

    private fun renderNodes() {
        renderSources()
    }

    private fun addNodeRow(node: ProxyNode) {
        val result = when (node.endpointReachable) {
            true -> "TCP 端口可连接 · ${node.endpointLatencyMs} ms"
            false -> "TCP 端口不可连接"
            null -> "尚未测试"
        }
        list.addView(TextView(this).apply {
            text = "${node.name}\n${node.summary()}\n$result\n来源：${sourceLabel(node.source)}"
            textSize = 14f; setPadding(12, 12, 12, 12)
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun sourceLabel(value: String): String = runCatching {
        val uri = java.net.URI(value)
        uri.host + (uri.path?.takeIf { it.isNotBlank() } ?: "")
    }.getOrDefault("已保存的 HTTPS 来源")


    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
