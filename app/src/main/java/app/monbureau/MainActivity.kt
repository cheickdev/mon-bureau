package app.monbureau

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** The whole interface is the bundled web page; this activity only hosts it. */
class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(this), "Native")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (_: Exception) {
                }
                return true
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
