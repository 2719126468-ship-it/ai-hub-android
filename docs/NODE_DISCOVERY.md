# Node discovery and screening

## Flow

1. Search public GitHub repository READMEs for candidate raw subscription URLs.
2. Show candidates to the user; never silently activate a source.
3. Store selected HTTPS source URLs encrypted with an Android Keystore AES-GCM key.
4. Fetch at most 3 MiB per subscription and parse supported share-link schemes.
5. Run concurrent TCP port checks and rank endpoints by reachability and observed connect time.

## Security boundaries

- Public subscriptions are untrusted input and may contain malicious or privacy-invasive servers.
- The app only accepts HTTPS source URLs and does not automatically follow redirects.
- GitHub's unauthenticated API is rate-limited; search may return no candidates.
- TCP reachability is not a proxy protocol test. It does not authenticate a node or prove that traffic can exit through it.
- Real proxy egress checks, automatic selection and failover must be implemented only after the sing-box VPN runtime is integrated and tested.
- Source URLs are encrypted at rest using an Android Keystore AES-GCM key. Subscription node share links are currently kept in memory for the active screen and are not persisted by this module.

## Supported parser families

VLESS, VMess (JSON share links), Trojan, Shadowsocks, Hysteria2/Hy2, TUIC, SOCKS, HTTP(S), and AnyTLS. Unsupported or malformed entries are skipped.
