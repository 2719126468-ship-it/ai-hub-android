# sing-box 配置生成与校验

AI Hub 使用官方 `com.github.singbox-android:libbox:1.14.4` 绑定。应用启动时初始化 libbox，并在节点页提供“生成并校验 sing-box 配置”。

## 当前支持的分享链接

- VLESS：UUID、flow、TLS / Reality、WebSocket / gRPC / HTTP / HTTPUpgrade
- VMess：UUID、cipher、alterId、TLS、WebSocket / gRPC / HTTP / HTTPUpgrade
- Trojan：密码、TLS、上述常见传输方式
- Shadowsocks：method / password（不含插件）
- Hysteria2：密码、TLS、salamander obfs
- TUIC：UUID / password、拥塞控制、UDP relay mode、TLS
- AnyTLS：密码、TLS
- SOCKS / SOCKS5、HTTP / HTTPS：基础认证

## 本地测试配置

生成的配置包含一个只绑定 `127.0.0.1` 的 SOCKS inbound（默认端口 20808）、目标 outbound 和 direct outbound。不会创建 VPN inbound，也不会接管设备的其他应用流量。

## 结果边界

“配置校验通过”只说明 libbox 能接受生成的配置。它不代表：
- 节点认证或协议握手成功；
- 本地 SOCKS 监听已启动；
- HTTPS 请求确实通过代理；
- 节点出口 IP、延迟或稳定性合格。

TCP 端口探测仍只是快速预筛。真正的节点可用性必须在后续 runtime 阶段启动节点，再使用**明确指定 localhost SOCKS 代理且禁止 direct fallback**的 HTTPS 请求验证。未实现前，UI 不会把配置校验结果标记为“可用节点”。

## 当前已知限制

分享链接格式存在大量扩展和供应商变体。未知传输方式、缺少 Reality 公钥、Shadowsocks 插件等会明确报错，不会静默丢弃参数并生成看似可用的错误配置。导入来源仍应视为不可信输入。
