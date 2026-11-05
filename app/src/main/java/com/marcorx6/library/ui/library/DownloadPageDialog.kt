package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.network.SecureUrlPolicy
import org.json.JSONArray
import org.json.JSONTokener

/**
 * A small in-app browser for download pages that are not direct files.
 * It lets the user complete normal site steps and detects likely file links
 * without bypassing captchas, sign-in, timers, or other site protections.
 */
internal class DownloadPageDialog(
    private val activity: Activity,
    private val translate: (String) -> String
) {
    fun show(
        startUrl: String,
        gameTitle: String,
        onDownload: (String, String, String, String, String, String) -> Unit
    ) {
        val safeStartUrl = SecureUrlPolicy.toHttpsUrl(startUrl)
        if (safeStartUrl == null) {
            Toast.makeText(activity, translate("رابط صفحة التحميل غير آمن أو غير صالح"), Toast.LENGTH_LONG).show()
            return
        }

        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
        val dark = activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val background = if (dark) Color.rgb(7, 17, 31) else Color.rgb(242, 247, 253)
        val surface = if (dark) Color.rgb(15, 35, 58) else Color.WHITE
        val primary = Color.rgb(42, 145, 255)
        val heading = if (dark) Color.WHITE else Color.rgb(22, 43, 65)
        val muted = if (dark) Color.rgb(164, 184, 207) else Color.rgb(91, 112, 137)

        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val toolbar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(activity).apply {
            text = "MARCO RX6  /  DOWNLOAD PAGE"
            textSize = 9f
            letterSpacing = 0.1f
            setTextColor(primary)
        })
        titleBox.addView(TextView(activity).apply {
            text = translate("صفحة التحميل")
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(heading)
            setPadding(0, dp(3), 0, 0)
        })
        toolbar.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))

        fun toolbarAction(label: String, onClick: () -> Unit): TextView =
            TextView(activity).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(primary)
                minWidth = dp(44)
                minHeight = dp(44)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

        val webView = WebView(activity)
        toolbar.addView(toolbarAction(translate("خارجي")) {
            try {
                val currentUrl = SecureUrlPolicy.toHttpsUrl(webView.url ?: safeStartUrl)
                if (currentUrl == null) throw IllegalArgumentException("Unsafe page URL")
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
            } catch (_: Exception) {
                Toast.makeText(activity, translate("تعذر فتح المتصفح الخارجي"), Toast.LENGTH_SHORT).show()
            }
        })
        toolbar.addView(toolbarAction("×") { dialog.dismiss() })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))

        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(primary)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(primary)
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply {
            topMargin = dp(8)
            bottomMargin = dp(6)
        })

        val status = TextView(activity).apply {
            text = translate("جارٍ فتح صفحة التحميل…")
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(3), dp(3), dp(3), dp(7))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        val browserFrame = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (dark) Color.argb(120, 80, 151, 215) else Color.argb(150, 153, 195, 236))
            }
            clipToOutline = true
        }
        browserFrame.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(browserFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        val candidateTitle = TextView(activity).apply {
            text = translate("روابط ملفات محتملة")
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(heading)
            setPadding(dp(3), dp(10), dp(3), dp(5))
            visibility = View.GONE
        }
        root.addView(candidateTitle, LinearLayout.LayoutParams(-1, -2))
        val candidates = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val candidateScroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
            addView(candidates, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(candidateScroll, LinearLayout.LayoutParams(-1, dp(112)))

        fun addCandidate(label: String, url: String) {
            val safeUrl = SecureUrlPolicy.toHttpsUrl(url) ?: return
            if (candidates.childCount >= 5) return
            candidates.addView(TextView(activity).apply {
                text = "↗  " + label.take(70).ifBlank { translate("فتح رابط الملف المحتمل") }
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(24, 76, 126) else Color.rgb(35, 119, 207))
                    cornerRadius = dp(11).toFloat()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    status.text = translate("جارٍ فحص الرابط المحتمل…")
                    webView.loadUrl(safeUrl)
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
            candidateTitle.visibility = View.VISIBLE
            candidateScroll.visibility = View.VISIBLE
        }

        fun scanForFileLinks() {
            val script = """(function(){var out=[],seen={};document.querySelectorAll('a[href]').forEach(function(a){var h=a.href||'',t=(a.innerText||a.textContent||'').trim(),d=a.hasAttribute('download'),u=h.toLowerCase().split('?')[0];var ext=/\.(zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|apk|pdf|mp4|mp3)(?:$)/i.test(u);var words=/(download|تحميل|تنزيل|رابط مباشر|direct)/i.test(t);if((d||ext||words)&&h.indexOf('https://')===0&&!seen[h]){seen[h]=1;out.push({url:h,label:t||'رابط ملف محتمل',strong:d||ext});}});return JSON.stringify(out.slice(0,8));})()"""
            webView.evaluateJavascript(script) { raw ->
                try {
                    val decoded = JSONTokener(raw ?: "[]").nextValue()
                    val json = if (decoded is String) JSONArray(decoded) else decoded as? JSONArray ?: JSONArray()
                    for (index in 0 until json.length()) {
                        val item = json.optJSONObject(index) ?: continue
                        addCandidate(item.optString("label"), item.optString("url"))
                    }
                } catch (_: Exception) {
                    // A page may block script inspection; normal browsing still works.
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                progress.progress = 0
                status.text = Uri.parse(url).host ?: translate("جارٍ فتح صفحة التحميل…")
                candidates.removeAllViews()
                candidateTitle.visibility = View.GONE
                candidateScroll.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                status.text = translate("إذا ظهرت خطوة تحقق أو مؤقت، أكملها داخل هذه الصفحة. الروابط المحتملة تظهر أسفلها.")
                scanForFileLinks()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                status.text = translate("تعذر التحقق من أمان اتصال الموقع؛ تم إيقاف فتح الصفحة.")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) status.text = translate("تعذر تحميل صفحة الموقع. جرّب فتحها بالمتصفح الخارجي.")
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val safeUrl = SecureUrlPolicy.toHttpsUrl(url)
            if (safeUrl == null) {
                Toast.makeText(activity, translate("رابط الملف غير آمن؛ تم إيقاف التنزيل"), Toast.LENGTH_LONG).show()
            } else {
                onDownload(safeUrl, gameTitle, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType.orEmpty(), webView.url.orEmpty())
            }
        })

        dialog.setContentView(root)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (webView.canGoBack()) {
                    webView.goBack()
                    true
                } else {
                    false
                }
            } else false
        }
        dialog.setOnDismissListener {
            webView.stopLoading()
            webView.destroy()
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        webView.loadUrl(safeStartUrl)
    }
}
