package com.freehtml.app.ui.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Creates a preview WebView that survives tab changes inside the editor. The caller owns its
 * lifetime and should keep the same instance until the editor is closed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun rememberPreviewWebView(): WebView {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            setBackgroundColor(Color.WHITE)
            webViewClient = PreviewWebViewClient(onFinished = {})
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    return webView
}

@Composable
fun WebViewPreview(
    webView: WebView,
    html: String,
    javaScriptEnabled: Boolean,
    modifier: Modifier = Modifier,
    forceReloadKey: Int = 0,
    assetResolver: ((String) -> PreviewAsset?)? = null,
) {
    var isLoading by remember { mutableStateOf(false) }

    // Pause JS timers while the preview is not visible to save CPU/memory.
    DisposableEffect(webView) {
        webView.onResume()
        onDispose {
            webView.onPause()
        }
    }

    LaunchedEffect(webView, html, javaScriptEnabled, forceReloadKey) {
        val requested = LoadedPreview(
            html = html,
            javaScriptEnabled = javaScriptEnabled,
            forceReloadKey = forceReloadKey,
        )
        if (webView.tag == requested) return@LaunchedEffect

        isLoading = true
        val client = PreviewWebViewClient(onFinished = { isLoading = false })
        client.assetResolver = assetResolver
        webView.webViewClient = client
        webView.webChromeClient = PreviewWebChromeClient()
        webView.settings.javaScriptEnabled = javaScriptEnabled
        webView.settings.domStorageEnabled = true
        // A stable https origin is required so pages can use localStorage/DOM storage
        // (an opaque about:blank origin makes localStorage throw and lose its data).
        webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null)
        webView.tag = requested
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

private data class LoadedPreview(
    val html: String,
    val javaScriptEnabled: Boolean,
    val forceReloadKey: Int,
)

/** Logs page console messages and JS errors to logcat (tag FREEHTML-WEB) for diagnostics. */
private class PreviewWebChromeClient : android.webkit.WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
        android.util.Log.d(
            "FREEHTML-WEB",
            "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} " +
                consoleMessage.message(),
        )
        return true
    }
}

private const val BASE_URL = "https://freehtml.local/"

@SuppressLint("MissingOnRenderProcessGone")
private class PreviewWebViewClient(
    private val onFinished: () -> Unit,
) : WebViewClient() {

    var assetResolver: ((String) -> PreviewAsset?)? = null

    /**
     * Serves files from the user-granted site folder for requests under the preview origin,
     * so ES modules, CSS, JS and images referenced by relative path work in the preview.
     */
    override fun shouldInterceptRequest(
        view: WebView?,
        request: android.webkit.WebResourceRequest,
    ): android.webkit.WebResourceResponse? {
        val resolver = assetResolver ?: return null
        val url = request.url ?: return null
        if (url.host != "freehtml.local") return null
        val path = url.path?.trimStart('/')?.takeIf { it.isNotBlank() } ?: return null
        val decoded = runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrNull()
            ?: return null
        val asset = resolver(decoded) ?: return null
        val isText = asset.mimeType.startsWith("text/") ||
            asset.mimeType == "application/json" ||
            asset.mimeType == "image/svg+xml"
        return android.webkit.WebResourceResponse(
            asset.mimeType,
            if (isText) "UTF-8" else null,
            java.io.ByteArrayInputStream(asset.bytes),
        )
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Some pages paint their background only on <body> with background-attachment: fixed,
        // which Android WebView renders poorly, leaving the default white canvas visible.
        // Copy the computed body background onto <html> so the whole viewport is covered.
        view?.evaluateJavascript(
            """
            (function(){
              try {
                var body = document.body;
                if (!body) return;
                var computed = getComputedStyle(body);
                var root = document.documentElement;
                if (computed.backgroundImage && computed.backgroundImage !== 'none') {
                  root.style.backgroundImage = computed.backgroundImage;
                  root.style.backgroundRepeat = 'no-repeat';
                  root.style.backgroundSize = 'cover';
                  root.style.backgroundAttachment = 'scroll';
                }
                if (computed.backgroundColor && computed.backgroundColor !== 'rgba(0, 0, 0, 0)') {
                  root.style.backgroundColor = computed.backgroundColor;
                }
                root.style.minHeight = '100vh';
              } catch (e) {}
            })();
            """.trimIndent(),
            null,
        )
        onFinished()
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        // Keep the app alive if the renderer is killed; the user can reopen the preview.
        onFinished()
        return true
    }
}
