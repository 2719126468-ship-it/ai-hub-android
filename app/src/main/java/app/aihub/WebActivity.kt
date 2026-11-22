package app.aihub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebActivity : AppCompatActivity() {
    companion object { const val EXTRA_PROVIDER_ID = "provider_id" }

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = Providers.all.firstOrNull {
            it.id == intent.getStringExtra(EXTRA_PROVIDER_ID)
        } ?: run { finish(); return }

        val allowedHost = Uri.parse(provider.url).host ?: run { finish(); return }
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host.orEmpty().lowercase()
                val scheme = uri.scheme.orEmpty().lowercase()
                if (scheme !in setOf("https", "http")) return true
                if (host == allowedHost || host.endsWith(".$allowedHost")) return false

                // OAuth and identity providers are delegated to the system browser.
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) { true }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        web.loadUrl(provider.url)
        setContentView(web)
    }

    override fun onBackPressed() {
        val web = (window.decorView.findViewById(android.R.id.content) as? android.view.ViewGroup)
            ?.getChildAt(0) as? WebView
        if (web?.canGoBack() == true) web.goBack() else super.onBackPressed()
    }
}
