package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.inputmethod.EditorInfo
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.text.InputType
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.network.SecureUrlPolicy
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicInteger

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
        val appPreferences = activity.getSharedPreferences("app_settings", Activity.MODE_PRIVATE)
        // The in-app appearance setting is authoritative; do not follow the device theme.
        val dark = appPreferences.getBoolean("dark_mode", true)
        val background = if (dark) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247)
        val surface = if (dark) Color.rgb(25, 28, 34) else Color.WHITE
        val primary = if (dark) Color.rgb(205, 212, 222) else Color.rgb(105, 116, 132)
        val border = if (dark) Color.rgb(70, 77, 89) else Color.rgb(205, 211, 220)
        val heading = if (dark) Color.WHITE else Color.rgb(25, 29, 36)
        val muted = if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)

        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val webView = WebView(activity)
        val toolbar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), 0, dp(2))
        }
        titleBox.addView(TextView(activity).apply {
            text = "MARCO"
            textSize = 17f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(heading)
        })
        titleBox.addView(TextView(activity).apply {
            text = translate("متصفح التحميل")
            textSize = 10f
            setTextColor(muted)
        })
        toolbar.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))

        fun toolbarAction(label: String, onClick: () -> Unit, iconRes: Int = 0): TextView =
            TextView(activity).apply {
                text = label
                if (iconRes != 0) {
                    setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
                    compoundDrawablePadding = dp(5)
                }
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(primary)
                minWidth = dp(44)
                minHeight = dp(44)
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setBackground(GradientDrawable().apply {
                    setColor(surface)
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), border)
                })
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    compoundDrawableTintList = android.content.res.ColorStateList.valueOf(primary)
                    foreground = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(
                            if (dark) Color.argb(70, 205, 212, 222) else Color.argb(45, 105, 116, 132)
                        ), null, android.graphics.drawable.ColorDrawable(Color.WHITE)
                    )
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

        toolbar.addView(toolbarAction("", { dialog.dismiss() }, com.marcorx6.library.R.drawable.ic_action_close))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))

        // Opera-inspired compact navigation row: history, editable address, reload and external open.
        val addressRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        addressRow.addView(toolbarAction("", {
            if (webView.canGoBack()) webView.goBack()
        }, com.marcorx6.library.R.drawable.ic_action_back))
        val addressInput = EditText(activity).apply {
            setSingleLine(true)
            textSize = 12f
            setTextColor(heading)
            setHintTextColor(muted)
            hint = translate("ابحث أو أدخل عنوان الموقع")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(12), 0, dp(10), 0)
            setBackground(GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(65, 71, 82) else Color.rgb(205, 211, 220))
            })
        }
        addressRow.addView(addressInput, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        addressRow.addView(toolbarAction("", {
            val currentUrl = SecureUrlPolicy.toHttpsUrl(webView.url ?: safeStartUrl)
            if (currentUrl != null) {
                webView.stopLoading()
                webView.loadUrl(currentUrl)
            }
        }, com.marcorx6.library.R.drawable.ic_action_refresh))
        addressInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                val entered = addressInput.text.toString().trim()
                val candidate = if (entered.contains("://")) entered else "https://$entered"
                val safeUrl = SecureUrlPolicy.toHttpsUrl(candidate)
                if (safeUrl != null) {
                    webView.loadUrl(safeUrl)
                    val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(addressInput.windowToken, 0)
                    true
                } else {
                    addressInput.error = translate("رابط الصفحة غير صالح أو غير آمن")
                    true
                }
            } else false
        }
        root.addView(addressRow, LinearLayout.LayoutParams(-1, -2))


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
        val blockedStats = TextView(activity).apply {
            text = translate("الإعلانات والمتتبعات المحجوبة: 0")
            textSize = 10f
            setTextColor(primary)
            setPadding(dp(3), 0, dp(3), dp(6))
        }
        root.addView(blockedStats, LinearLayout.LayoutParams(-1, -2))

        val browserFrame = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            this.background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (dark) Color.argb(120, 130, 138, 151) else Color.argb(150, 160, 168, 181))
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

        fun confirmDownload(url: String, label: String, mimeType: String = "") {
            val safeUrl = SecureUrlPolicy.toHttpsUrl(url) ?: return
            AlertDialog.Builder(activity)
                .setTitle(translate("إضافة تنزيل"))
                .setMessage(label.take(100) + "\n\n" + safeUrl)
                .setNegativeButton(translate("إلغاء"), null)
                .setPositiveButton(translate("تنزيل الآن")) { _, _ ->
                    onDownload(
                        safeUrl,
                        gameTitle,
                        webView.settings.userAgentString.orEmpty(),
                        "",
                        mimeType,
                        webView.url.orEmpty()
                    )
                }
                .show()
        }

        fun isDirectFileUrl(url: String): Boolean {
            val path = try { Uri.parse(url).path.orEmpty().lowercase() } catch (_: Exception) { "" }
            return path.matches(Regex(""".+\\.(zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|apk|pdf|mp4|m4v|mkv|webm|mov|avi|flv|3gp|mp3|m4a|aac|wav|flac|ogg)$"""))
        }

        fun addCandidate(label: String, url: String) {
            val safeUrl = SecureUrlPolicy.toHttpsUrl(url) ?: return
            if (candidates.childCount >= 5) return
            candidates.addView(TextView(activity).apply {
                text = "  " + label.take(70).ifBlank { translate("فتح رابط الملف المحتمل") }
                setCompoundDrawablesRelativeWithIntrinsicBounds(com.marcorx6.library.R.drawable.ic_action_external, 0, 0, 0)
                compoundDrawablePadding = dp(7)
                textSize = 12f
                setTextColor(heading)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                this.background = GradientDrawable().apply {
                    setColor(surface)
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), border)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    confirmDownload(safeUrl, label)
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
            candidateTitle.visibility = View.VISIBLE
            candidateScroll.visibility = View.VISIBLE
        }

        fun scanForFileLinks() {
            val script = """(function(){var out=[],seen={};var ext=/\.(zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|apk|pdf|mp4|m4v|mkv|webm|mov|avi|flv|3gp|mp3|m4a|aac|wav|flac|ogg)(?:$)/i;function add(h,t,force){if(!h||h.indexOf('https://')!==0||seen[h]||out.length>=12)return;var u=h.toLowerCase().split('?')[0];if(force||ext.test(u)){seen[h]=1;out.push({url:h,label:t||'ملف مباشر محتمل',strong:!!force});}}document.querySelectorAll('a[href]').forEach(function(a){var h=a.href||'',t=(a.innerText||a.textContent||'').trim(),d=a.hasAttribute('download'),u=h.toLowerCase().split('?')[0];var words=/(download|تحميل|تنزيل|رابط مباشر|direct)/i.test(t);if(d||ext.test(u)||words)add(h,t,d||ext.test(u));});document.querySelectorAll('video[src],video source[src],audio[src],audio source[src]').forEach(function(e){add(e.src||e.getAttribute('src'),e.tagName.toLowerCase().indexOf('audio')>=0?'ملف صوت':'ملف فيديو',true);});return JSON.stringify(out.slice(0,8));})()"""
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        val adPreferences = appPreferences
        var adBlockerEnabled = adPreferences.getBoolean("download_page_ad_blocker", true)
        val allowedHosts = adPreferences.getString("download_page_ad_allowlist", "").orEmpty()
            .split(',', ';').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        lateinit var adToggle: TextView
        adToggle = toolbarAction(translate("حجب الإعلانات"), {
            adBlockerEnabled = !adBlockerEnabled
            adPreferences.edit().putBoolean("download_page_ad_blocker", adBlockerEnabled).apply()
            adToggle.apply {
                text = translate("حجب الإعلانات")
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    if (adBlockerEnabled) com.marcorx6.library.R.drawable.ic_action_check
                    else com.marcorx6.library.R.drawable.ic_action_close, 0, 0, 0
                )
            }
            blockedStats.text = translate(if (adBlockerEnabled) "تم تفعيل حجب الإعلانات" else "تم إيقاف حجب الإعلانات")
            webView.reload()
        }, if (adBlockerEnabled) com.marcorx6.library.R.drawable.ic_action_check else com.marcorx6.library.R.drawable.ic_action_close)
        val browserActions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(7))
        }
        browserActions.addView(toolbarAction(translate("تقدم"), {
            if (webView.canGoForward()) webView.goForward()
        }, com.marcorx6.library.R.drawable.ic_action_forward),
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        browserActions.addView(toolbarAction(translate("فتح خارجياً"), {
            try {
                val currentUrl = SecureUrlPolicy.toHttpsUrl(webView.url ?: safeStartUrl)
                if (currentUrl == null) throw IllegalArgumentException("Unsafe page URL")
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
            } catch (_: Exception) {
                Toast.makeText(activity, translate("تعذر فتح المتصفح الخارجي"), Toast.LENGTH_SHORT).show()
            }
        }, com.marcorx6.library.R.drawable.ic_action_external),
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        browserActions.addView(adToggle, LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(browserActions, LinearLayout.LayoutParams(-1, -2))
        val blockedRequests = AtomicInteger(0)
        // Curated local filter rules: known ad networks plus common third-party ad endpoints.
        // This is deliberately conservative for first-party pages so download controls keep working.
        val blockedAdHosts = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "googletagservices.com", "googletagmanager.com",
            "taboola.com", "outbrain.com", "popads.net", "popcash.net",
            "propellerads.com", "exoclick.com", "trafficjunky.com", "onclickads.net",
            "juicyads.com", "adsterra.com", "adform.net", "adnxs.com",
            "adsrvr.org", "criteo.com", "casalemedia.com", "rubiconproject.com",
            "openx.net", "pubmatic.com", "sharethrough.com", "lijit.com",
            "media.net", "revcontent.com", "mgid.com", "zemanta.com",
            "smartadserver.com", "yieldmo.com", "bidswitch.net", "advertising.com",
            "3lift.com", "contextweb.com", "sovrn.com", "inmobi.com",
            "unityads.unity3d.com", "applovin.com", "vungle.com", "chartboost.com",
            "ironsrc.com", "adcolony.com", "adnxs-simple.com", "adsafeprotected.com",
            "adroll.com", "bluekai.com", "bouncex.net", "clarity.ms", "connectad.io",
            "demdex.net", "e-planning.net", "everesttech.net", "eyeota.net",
            "flashtalking.com", "ibillboard.com", "krxd.net", "mathtag.com",
            "moatads.com", "omnitagjs.com", "quantserve.com", "rlcdn.com",
            "samba.tv", "serving-sys.com", "simpli.fi", "sportradarserving.com",
            "teads.tv", "triplelift.com", "turn.com", "undertone.com", "yieldlove.com",
            "adzerk.net", "adlightning.com", "admanmedia.com", "adpushup.com",
            "adrecover.com", "adthrive.com", "carbonads.com", "ethicalads.io",
            "buysellads.com", "optad360.com", "monetizemore.com", "snigel.com",
            "shareasale.com", "impact.com", "awin1.com", "partnerize.com",
            "amazon-adsystem.com", "advertising.amazon.com", "ads.google.com",
            "ads.youtube.com", "pixel.facebook.com", "connect.facebook.net",
            "analytics.tiktok.com", "bat.bing.com", "popunder.net",
            "hilltopads.net", "hilltopads.com", "ad-maven.com", "ad-maven.net",
            "monetag.com", "monetag.net",
            "adcash.com", "yllix.com", "revenuehits.com"
        )
        val adEndpointPatterns = listOf(
            Regex("""(?:^|/)(?:pagead|adsystem|adserver|adservice|adview|adclick|popunder|popup|bannerad|vast|vpaid)(?:/|[._?=&-]|$)""", RegexOption.IGNORE_CASE),
            Regex("""[?&](?:adunit|adslot|adtag|adzone|adserver|sponsorid)=([^&]*)""", RegexOption.IGNORE_CASE)
        )
        fun hostIsAllowed(host: String): Boolean =
            allowedHosts.any { host == it || host.endsWith(".$it") }
        fun isBlockedAdRequest(url: String): Boolean {
            if (!adBlockerEnabled) return false
            val uri = try { Uri.parse(url) } catch (_: Exception) { return false }
            val host = uri.host?.lowercase() ?: return false
            if (hostIsAllowed(host)) return false
            if (blockedAdHosts.any { host == it || host.endsWith(".$it") }) return true
            val pageHost = try { Uri.parse(webView.url ?: safeStartUrl).host?.lowercase() } catch (_: Exception) { null }
            // Generic endpoint rules apply only to third-party requests, not the download site itself.
            if (pageHost != null && host != pageHost && !host.endsWith(".$pageHost")) {
                val target = (uri.encodedPath.orEmpty() + "?" + uri.encodedQuery.orEmpty())
                if (adEndpointPatterns.any { it.containsMatchIn(target) }) return true
            }
            return false
        }
        fun recordBlocked() {
            val count = blockedRequests.incrementAndGet()
            activity.runOnUiThread {
                if (blockedStats.isAttachedToWindow) {
                    blockedStats.text = translate("الإعلانات والمتتبعات المحجوبة: $count")
                }
            }
        }
        fun applyCosmeticFiltering() {
            if (!adBlockerEnabled) return
            val script = """(function(){try{
              var selectors=['iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]',
                'iframe[src*="adservice"]','iframe[src*="adnxs"]','iframe[src*="outbrain"]',
                'iframe[src*="taboola"]','iframe[src*="popads"]','iframe[src*="popcash"]',
                'iframe[src*="propellerads"]','iframe[src*="exoclick"]','iframe[src*="adsterra"]',
                '[id^="google_ads"]','[id*="ad-container"]','[id*="ad_wrapper"]',
                '[class*="ad-container"]','[class*="ad-wrapper"]','[class*="advertisement"]',
                '[class*="advert-banner"]','[class*="ad-slot"]','[class*="ad-placement"]',
                '[class*="sponsored"]','[class*="sponsor-box"]','[data-ad]','[data-ad-unit]',
                '[data-ad-slot]','[data-advertisement]','[data-google-query-id]',
                '[aria-label="advertisement" i]','ins.adsbygoogle','amp-ad',
                'amp-embed[type="taboola"]','div[id*="taboola"]','div[class*="taboola"]',
                'div[id*="outbrain"]','div[class*="outbrain"]',
                'a[href*="doubleclick.net"]','a[href*="popads.net"]','a[href*="popcash.net"]',
                'a[href*="propellerads.com"]','a[href*="exoclick.com"]'];
              function hideAds(root){var n=0;selectors.forEach(function(s){
                try{
                  if(root&&root.nodeType===1&&root.matches&&root.matches(s)){
                    root.style.setProperty('display','none','important');root.setAttribute('data-marco-ad-hidden','1');n++;
                  }
                  root.querySelectorAll(s).forEach(function(e){
                    if(n<240){e.style.setProperty('display','none','important');e.setAttribute('data-marco-ad-hidden','1');n++;}
                  });
                }catch(ignore){}
              });}
              hideAds(document);
              if(!window.__marcoAdObserver){
                window.__marcoAdObserver=new MutationObserver(function(records){
                  records.forEach(function(record){record.addedNodes.forEach(function(node){
                    if(node&&node.nodeType===1){hideAds(node);}});
                  });
                });
                window.__marcoAdObserver.observe(document.documentElement,{childList:true,subtree:true});
              }
            }catch(e){}})();"""
            webView.evaluateJavascript(script, null)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                if (!request.isForMainFrame && isBlockedAdRequest(request.url.toString())) {
                    recordBlocked()
                    return android.webkit.WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                if (scheme != "https") return true
                if (adBlockerEnabled && isBlockedAdRequest(request.url.toString())) {
                    recordBlocked()
                    return true
                }
                if (isDirectFileUrl(request.url.toString())) {
                    confirmDownload(request.url.toString(), request.url.lastPathSegment ?: translate("ملف مباشر"))
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                progress.progress = 0
                status.text = Uri.parse(url).host ?: translate("جارٍ فتح صفحة التحميل…")
                addressInput.setText(url)
                candidates.removeAllViews()
                candidateTitle.visibility = View.GONE
                candidateScroll.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                status.text = translate("إذا ظهرت خطوة تحقق أو مؤقت، أكملها داخل هذه الصفحة. الروابط المحتملة تظهر أسفلها.")
                applyCosmeticFiltering()
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
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                if (adBlockerEnabled) recordBlocked()
                return false
            }
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
                confirmDownload(safeUrl, contentDisposition.ifBlank { gameTitle }, mimeType.orEmpty())
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
        dialog.window?.apply {
            setLayout(-1, -1)
            setGravity(Gravity.FILL)
            setBackgroundDrawableResource(android.R.color.transparent)
            // Make the full-screen download workspace visually own both system bars.
            statusBarColor = background
            navigationBarColor = background
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            decorView.systemUiVisibility = if (!dark) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
        }
        addressInput.setText(safeStartUrl)
        webView.loadUrl(safeStartUrl)
    }
}
