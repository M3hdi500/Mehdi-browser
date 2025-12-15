package com.mehdi.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen()
                }
            }
        }
    }
}

private fun isProbablyUrl(input: String): Boolean {
    val s = input.trim()
    return s.startsWith("http://") || s.startsWith("https://") || s.contains(".")
}

private fun toUrlOrSearch(input: String): String {
    val s = input.trim()
    return if (isProbablyUrl(s)) {
        if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
    } else {
        "https://www.google.com/search?q=" + URLEncoder.encode(s, "UTF-8")
    }
}

/**
 * Starter ad/tracker blocker (simple).
 * Next step: plug an EasyList-compatible engine.
 */
private class SimpleAdBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adsystem.com",
        "ads.twitter.com",
        "facebook.com/tr",
        "connect.facebook.net",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com"
    )

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        return blockedHosts.any { lower.contains(it) } ||
            lower.contains("/ads?") ||
            lower.contains("adserver") ||
            lower.contains("analytics")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val context = LocalContext.current
    var address by remember { mutableStateOf(TextFieldValue("https://www.google.com")) }
    var progress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val adBlocker = remember { SimpleAdBlocker() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search or type URL") }
                )
            },
            actions = {
                TextButton(onClick = {
                    webViewRef?.loadUrl(toUrlOrSearch(address.text))
                }) { Text("Go") }
            }
        )

        if (progress in 1..99) {
            LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = {
                WebView(context).apply {
                    webViewRef = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.mediaPlaybackRequiresUserGesture = true

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            canGoBack = this@apply.canGoBack()
                            canGoForward = this@apply.canGoForward()
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            request?.url?.toString()?.let { address = TextFieldValue(it) }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { address = TextFieldValue(it) }
                            canGoBack = this@apply.canGoBack()
                            canGoForward = this@apply.canGoForward()
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            return if (adBlocker.shouldBlock(url)) {
                                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            } else null
                        }
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    loadUrl(address.text)
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { webViewRef?.goBack() }, enabled = canGoBack) { Text("Back") }
            Button(onClick = { webViewRef?.reload() }) { Text("Refresh") }
            Button(onClick = { webViewRef?.goForward() }, enabled = canGoForward) { Text("Next") }
        }
    }
}
