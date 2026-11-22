# AI Hub Android

Android 8.1+ multi-provider AI web hub and proxy diagnostics app.

## Current scaffold
- Native Kotlin Android shell with an embedded WebView.
- Nine configurable AI web entry points.
- Provider navigation with per-provider first-party origin isolation planned.
- ABI-selectable builds (`armeabi-v7a`, `arm64-v8a`).
- GitHub Actions build matrix.

## Important scope notes
This is the first runnable foundation, not a finished proxy client. It does **not** yet import or execute V2RayNG subscriptions, provide VPN routing, or implement chained proxies. Those require a separately audited sing-box integration and device-level VPNService tests. The current network check is a direct HTTPS reachability check and must not be presented as a proxy-node test.

AI providers and authentication methods change over time. The app opens official sites and delegates unsupported OAuth flows to the system browser; it does not automate registration, bypass CAPTCHA, or promise email-only signup where the provider does not offer it.

## Build
Open with Android Studio (JDK 17, Android SDK 35), or run:
```bash
./gradlew assembleDebug -Pabi=arm64-v8a
./gradlew assembleDebug -Pabi=armeabi-v7a
```
The Gradle wrapper JAR is not included in this scaffold; Android Studio can generate it, or add the official Gradle wrapper before command-line builds.

Minimum SDK: 27 (Android 8.1). Compile/target SDK: 35.
