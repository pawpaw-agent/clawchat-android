package com.openclaw.clawchat.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MarkdownWebView(
    content: String,
    modifier: Modifier = Modifier
) {
    val webViewRef = remember { mutableMapOf<String, WebView?>() }
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 页面加载完成后渲染内容
                        webViewRef["ready"] = this@apply
                        renderContent(this@apply, content)
                    }
                }
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadUrl("file:///android_asset/markdown.html")
            }
        },
        update = { webView ->
            // 只有页面加载完成后才渲染
            if (webViewRef["ready"] != null) {
                renderContent(webView, content)
            }
        },
        modifier = modifier.fillMaxWidth().wrapContentHeight()
    )
}

private fun renderContent(webView: WebView, content: String) {
    val escaped = content
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    webView.evaluateJavascript("renderMarkdown(\"$escaped\");", null)
}