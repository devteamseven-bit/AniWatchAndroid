package com.aniwatch.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            
            // [NEW] 1. Network Blocking: Intercept and block ad requests before they load
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                
                // List of known ad and pop-up domains
                val adDomains = listOf(
                    "rm358.com",
                    "propush.me",
                    "doubleclick.net",
                    "googleadservices.com",
                    "popads.net",
                    "onclickads.net",
                    "adsterra",
                    "adcash"
                )

                if (adDomains.any { url.contains(it, ignoreCase = true) }) {
                    // Return an empty response to block the request silently
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return null // Let all other requests load normally
            }

            // [NEW] 2. Navigation Blocking: Stop the page from redirecting to ads
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val adDomains = listOf("rm358.com", "propush.me", "doubleclick.net")
                
                if (adDomains.any { url.contains(it, ignoreCase = true) }) {
                    return true // Block navigation
                }
                return false
            }

            // [NEW] 3. CSS/JS Injection: Hide ad banners that are built into the website
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Inject Javascript to remove common ad elements and popups
                val javascript = """
                    javascript:(function() {
                        var style = document.createElement('style');
                        style.type = 'text/css';
                        style.innerHTML = `
                            .ad-container, .adsbygoogle, #ad-container, .popup-ad, 
                            .overlay-ad, iframe[src*="ads"], iframe[src*="doubleclick"],
                            div[id^="ad-"], div[class^="ad-"] { display: none !important; }
                        `;
                        document.getElementsByTagName('head')[0].appendChild(style);
                    })();
                """.trimIndent()
                
                view?.evaluateJavascript(javascript, null)
            }
        }

        webView.loadUrl("https://aniwatch.co.at/")
        setContentView(webView)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}