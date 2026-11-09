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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.marcorx6.library.network.SecureUrlPolicy
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
        onDownload: (String, String, String, String, String, String) -> Unit,
        onOpenDownloads: () -> Unit = {}
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
        val primary = if (dark) Color.rgb(205, 212, 222) else Color.rgb(105, 113, 126)
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
        val browserHistory = mutableListOf<Pair<String, String>>()
        val browserBookmarks = mutableListOf<Pair<String, String>>()
        val browserTabs = mutableListOf<Pair<String, String>>()
        val browserTabStates = mutableListOf<android.os.Bundle?>()
        var activeTabIndex = 0
        fun saveActiveTabState() {
            if (activeTabIndex !in browserTabStates.indices) return
            val state = android.os.Bundle()
            if (runCatching { webView.saveState(state) }.isSuccess) {
                browserTabStates[activeTabIndex] = state
            }
        }
        fun restoreBrowserTab(index: Int) {
            if (index !in browserTabs.indices) return
            activeTabIndex = index
            val savedState = browserTabStates.getOrNull(index)
            val restored = savedState != null && runCatching {
                webView.restoreState(savedState) != null
            }.getOrDefault(false)
            if (!restored) webView.loadUrl(browserTabs[index].second)
        }
        runCatching {
            val saved = JSONArray(appPreferences.getString("download_browser_history", "[]"))
            for (i in 0 until saved.length()) {
                val item = saved.optJSONObject(i) ?: continue
                val url = item.optString("url")
                if (url.startsWith("https://")) browserHistory.add(item.optString("title") to url)
            }
        }
        runCatching {
            val saved = JSONArray(appPreferences.getString("download_browser_bookmarks", "[]"))
            for (i in 0 until saved.length()) {
                val item = saved.optJSONObject(i) ?: continue
                val url = SecureUrlPolicy.toHttpsUrl(item.optString("url")) ?: continue
                browserBookmarks.add(item.optString("title").ifBlank { Uri.parse(url).host.orEmpty() } to url)
            }
        }
        browserTabs.add(translate("التبويب 1") to safeStartUrl)
        browserTabStates.add(null)

        fun persistBrowserHistory() {
            val json = JSONArray()
            browserHistory.take(60).forEach { (title, url) ->
                json.put(org.json.JSONObject().put("title", title).put("url", url))
            }
            appPreferences.edit().putString("download_browser_history", json.toString()).apply()
        }

        fun persistBookmarks() {
            val json = JSONArray()
            browserBookmarks.take(100).forEach { (title, url) ->
                json.put(org.json.JSONObject().put("title", title).put("url", url))
            }
            appPreferences.edit().putString("download_browser_bookmarks", json.toString()).apply()
        }

        fun toggleCurrentBookmark() {
            val url = SecureUrlPolicy.toHttpsUrl(webView.url ?: safeStartUrl) ?: return
            val existing = browserBookmarks.indexOfFirst { it.second == url }
            if (existing >= 0) {
                browserBookmarks.removeAt(existing)
                Toast.makeText(activity, translate("تمت إزالة الموقع من المفضلة"), Toast.LENGTH_SHORT).show()
            } else {
                val title = webView.title.orEmpty().ifBlank { Uri.parse(url).host.orEmpty() }
                browserBookmarks.add(0, title to url)
                Toast.makeText(activity, translate("تم حفظ الموقع في المفضلة"), Toast.LENGTH_SHORT).show()
            }
            persistBookmarks()
        }

        fun openBrowserBookmarks() {
            if (browserBookmarks.isEmpty()) {
                Toast.makeText(activity, translate("لا توجد مواقع محفوظة في المفضلة"), Toast.LENGTH_SHORT).show()
                return
            }
            val labels = browserBookmarks.map { (title, url) ->
                (title.ifBlank { Uri.parse(url).host.orEmpty() }) + "\n" + url
            }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle(translate("المفضلة"))
                .setItems(labels) { _, which ->
                    browserBookmarks.getOrNull(which)?.second?.let { webView.loadUrl(it) }
                }
                .setNegativeButton(translate("إغلاق"), null)
                .setNeutralButton(translate("مسح المفضلة")) { _, _ ->
                    browserBookmarks.clear()
                    persistBookmarks()
                    Toast.makeText(activity, translate("تم مسح المواقع المحفوظة"), Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        fun clearBrowsingData() {
            AlertDialog.Builder(activity)
                .setTitle(translate("الخصوصية"))
                .setMessage(translate("سيتم مسح سجل التصفح وملفات تعريف الارتباط وبيانات المواقع المخزنة. لن تُحذف الملفات التي نزّلتها."))
                .setNegativeButton(translate("إلغاء"), null)
                .setPositiveButton(translate("مسح البيانات")) { _, _ ->
                    browserHistory.clear()
                    persistBrowserHistory()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    webView.clearCache(true)
                    webView.clearFormData()
                    Toast.makeText(activity, translate("تم مسح بيانات التصفح"), Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        fun rememberPage(url: String, title: String) {
            if (!url.startsWith("https://")) return
            browserHistory.removeAll { it.second == url }
            browserHistory.add(0, title.ifBlank { Uri.parse(url).host.orEmpty() } to url)
            while (browserHistory.size > 60) browserHistory.removeAt(browserHistory.lastIndex)
            persistBrowserHistory()
            if (activeTabIndex in browserTabs.indices) {
                browserTabs[activeTabIndex] = (title.ifBlank { browserTabs[activeTabIndex].first }) to url
            }
        }

        fun openBrowserHistory() {
            if (browserHistory.isEmpty()) {
                Toast.makeText(activity, translate("لا يوجد سجل تصفح بعد"), Toast.LENGTH_SHORT).show()
                return
            }
            val labels = browserHistory.map { (title, url) ->
                (title.ifBlank { Uri.parse(url).host.orEmpty() }) + "\n" + url
            }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle(translate("سجل التصفح"))
                .setItems(labels) { _, which ->
                    browserHistory.getOrNull(which)?.second?.let { webView.loadUrl(it) }
                }
                .setNegativeButton(translate("إغلاق"), null)
                .setNeutralButton(translate("مسح السجل")) { _, _ ->
                    browserHistory.clear()
                    persistBrowserHistory()
                    Toast.makeText(activity, translate("تم مسح سجل التصفح"), Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        fun openBrowserTabs() {
            val labels = browserTabs.mapIndexed { index, tab ->
                (if (index == activeTabIndex) "● " else "") +
                    (tab.first.ifBlank { translate("تبويب") + " " + (index + 1) }) +
                    "\n" + tab.second
            }.toMutableList()
            labels.add(translate("+ تبويب جديد"))
            AlertDialog.Builder(activity)
                .setTitle(translate("التبويبات المفتوحة") + " (" + browserTabs.size + "/8)")
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which == browserTabs.size) {
                        if (browserTabs.size >= 8) {
                            Toast.makeText(activity, translate("الحد الأقصى 8 تبويبات"), Toast.LENGTH_SHORT).show()
                        } else {
                            val input = EditText(activity).apply {
                                hint = translate("أدخل رابط الموقع")
                                setSingleLine(true)
                                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                            }
                            AlertDialog.Builder(activity)
                                .setTitle(translate("فتح تبويب جديد"))
                                .setView(input)
                                .setNegativeButton(translate("إلغاء"), null)
                                .setPositiveButton(translate("فتح")) { _, _ ->
                                    val raw = input.text.toString().trim()
                                    val candidate = if (raw.contains("://")) raw else "https://$raw"
                                    val safe = SecureUrlPolicy.toHttpsUrl(candidate)
                                    if (safe == null) {
                                        Toast.makeText(activity, translate("الرابط غير صالح أو غير آمن"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        saveActiveTabState()
                                        browserTabs.add(translate("تبويب") + " " + (browserTabs.size + 1) to safe)
                                        browserTabStates.add(null)
                                        restoreBrowserTab(browserTabs.lastIndex)
                                    }
                                }
                                .show()
                        }
                    } else if (which in browserTabs.indices && which != activeTabIndex) {
                        saveActiveTabState()
                        restoreBrowserTab(which)
                    }
                }
                .setNegativeButton(translate("إغلاق"), null)
                .setNeutralButton(translate("إغلاق التبويب الحالي")) { _, _ ->
                    if (browserTabs.size > 1) {
                        webView.stopLoading()
                        browserTabs.removeAt(activeTabIndex)
                        browserTabStates.removeAt(activeTabIndex)
                        restoreBrowserTab((activeTabIndex - 1).coerceAtLeast(0))
                    } else {
                        Toast.makeText(activity, translate("يجب إبقاء تبويب واحد على الأقل"), Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
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

        // Minimal browser header: address/search field and one ad-settings icon.
        val addressRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val addressInput = EditText(activity).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(heading)
            setHintTextColor(muted)
            hint = translate("ابحث أو أدخل عنوان الموقع")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(14), 0, dp(12), 0)
            setBackground(GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), border)
            })
        }
        addressRow.addView(addressInput, LinearLayout.LayoutParams(0, dp(46), 1f))
        addressInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                val entered = addressInput.text.toString().trim()
                if (entered.isBlank()) {
                    addressInput.error = translate("أدخل رابطاً أو عبارة للبحث")
                    return@setOnEditorActionListener true
                }
                val isUrl = entered.contains("://") || android.util.Patterns.WEB_URL.matcher(entered).matches()
                val destination = if (isUrl) {
                    SecureUrlPolicy.toHttpsUrl(if (entered.contains("://")) entered else "https://$entered")
                } else {
                    Uri.Builder().scheme("https").authority("www.google.com")
                        .appendPath("search").appendQueryParameter("q", entered).build().toString()
                }
                if (destination != null) {
                    webView.loadUrl(destination)
                    (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                        ?.hideSoftInputFromWindow(addressInput.windowToken, 0)
                } else addressInput.error = translate("رابط الصفحة غير صالح أو غير آمن")
                true
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
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(activity).apply {
            setColorSchemeColors(primary)
            setProgressBackgroundColorSchemeColor(surface)
            setOnRefreshListener { webView.reload() }
        }
        swipeRefresh.addView(browserFrame, android.view.ViewGroup.LayoutParams(-1, -1))
        root.addView(swipeRefresh, LinearLayout.LayoutParams(-1, 0, 1f))

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

        fun confirmDownload(
            url: String,
            label: String,
            mimeType: String = "",
            contentDisposition: String = ""
        ) {
            val safeUrl = SecureUrlPolicy.toHttpsUrl(url) ?: return
            DownloadConfirmationPanel(activity, translate, dark).show(
                url = safeUrl,
                suggestedName = android.webkit.URLUtil.guessFileName(safeUrl, contentDisposition, mimeType)
                    .ifBlank { label.ifBlank { gameTitle } },
                mimeType = mimeType,
                onConfirm = { fileName ->
                    val safeName = fileName.trim()
                        .replace(Regex("""[\\\\/:*?"<>|]"""), "_")
                        .take(180)
                        .ifBlank { android.webkit.URLUtil.guessFileName(safeUrl, null, mimeType) }
                    val disposition = "attachment; filename=\"$safeName\""
                    onDownload(
                        safeUrl,
                        safeName.ifBlank { gameTitle },
                        webView.settings.userAgentString.orEmpty(),
                        disposition,
                        mimeType,
                        webView.url.orEmpty()
                    )
                }
            )
        }

        fun isDirectFileUrl(url: String): Boolean {
            val uri = try { Uri.parse(url) } catch (_: Exception) { return false }
            val path = uri.path.orEmpty().lowercase()
            val knownExtension = Regex("""\.(zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|apk|pdf|mp4|m4v|mkv|webm|mov|avi|flv|3gp|mp3|m4a|aac|wav|flac|ogg)$""", RegexOption.IGNORE_CASE).containsMatchIn(path)
            val downloadFlag = listOf("download", "attachment", "file", "export").any { key ->
                uri.getQueryParameter(key)?.let { value ->
                    value.equals("1", true) || value.equals("true", true) ||
                        value.equals("yes", true) || value.equals("download", true)
                } == true
            }
            val downloadPath = Regex("""(?:^|/)(?:download|attachment|export)(?:/|$)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(path)
            return knownExtension || downloadFlag || downloadPath
        }

        val candidateUrls = mutableSetOf<String>()
        fun addCandidate(label: String, url: String) {
            val safeUrl = SecureUrlPolicy.toHttpsUrl(url) ?: return
            if (!candidateUrls.add(safeUrl) || candidates.childCount >= 8) return
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
            val script = """(function(){var out=[],seen={};var ext=/\.(zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|apk|pdf|mp4|m4v|mkv|webm|mov|avi|flv|3gp|mp3|m4a|aac|wav|flac|ogg)$/i;var words=/(download|تحميل|تنزيل|رابط مباشر|direct|download file)/i;function add(raw,label,force){try{var u=new URL(raw,location.href);if(u.protocol!=='https:'||seen[u.href]||out.length>=12)return;var p=u.pathname.toLowerCase(),q=u.search.toLowerCase(),endpoint=/(?:^|\/)(download|attachment|file|export)(?:\/|$)/i.test(p)||/[?&](download|attachment|file|export)=(1|true|yes|download)(?:&|$)/i.test(q);if(force||ext.test(p)||endpoint){seen[u.href]=1;out.push({url:u.href,label:(label||'ملف مباشر محتمل').slice(0,90)});}}catch(e){}}document.querySelectorAll('a[href],a[data-href],a[data-url],[data-download-url],[data-file-url],form[action],video[src],video source[src],audio[src],audio source[src]').forEach(function(e){var h=e.href||e.getAttribute('data-href')||e.getAttribute('data-url')||e.getAttribute('data-download-url')||e.getAttribute('data-file-url')||e.action||e.src||e.getAttribute('src')||'',t=(e.innerText||e.textContent||e.getAttribute('aria-label')||'').trim(),d=e.hasAttribute('download'),p=h.toLowerCase().split('?')[0],media=e.tagName==='VIDEO'||e.tagName==='AUDIO'||e.tagName==='SOURCE';if(media||d||words.test(t)||ext.test(p)||/(?:download|attachment|file|export)/i.test(h))add(h,t,d||media);});return JSON.stringify(out);})()"""
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
            .split(',', ';').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toMutableSet()
        lateinit var adToggle: android.widget.ImageButton
        fun showAdSettings() {
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(4))
            }
            val enabled = android.widget.CheckBox(activity).apply {
                text = translate("تفعيل حجب الإعلانات والمتتبعات")
                isChecked = adBlockerEnabled
                setTextColor(heading)
                buttonTintList = android.content.res.ColorStateList.valueOf(primary)
            }
            val allowlistInput = EditText(activity).apply {
                setText(allowedHosts.joinToString(", "))
                hint = translate("مواقع مسموح بها، افصل بينها بفاصلة")
                textSize = 13f
                setTextColor(heading)
                setHintTextColor(muted)
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackground(GradientDrawable().apply {
                    setColor(surface); cornerRadius = dp(12).toFloat(); setStroke(dp(1), border)
                })
            }
            content.addView(enabled)
            content.addView(TextView(activity).apply {
                text = translate("استثناء نطاقات المواقع من الحجب")
                textSize = 11f; setTextColor(muted)
                setPadding(0, dp(10), 0, dp(5))
            })
            content.addView(allowlistInput, LinearLayout.LayoutParams(-1, dp(48)))
            AlertDialog.Builder(activity)
                .setTitle(translate("إعدادات حجب الإعلانات"))
                .setView(content)
                .setNegativeButton(translate("إلغاء"), null)
                .setPositiveButton(translate("حفظ")) { _, _ ->
                    adBlockerEnabled = enabled.isChecked
                    val cleaned = allowlistInput.text.toString().split(',', ';')
                        .map { it.trim().lowercase() }
                        .filter { it.matches(Regex("[a-z0-9.-]+")) && it.contains('.') }
                        .distinct().take(30)
                    appPreferences.edit()
                        .putBoolean("download_page_ad_blocker", adBlockerEnabled)
                        .putString("download_page_ad_allowlist", cleaned.joinToString(","))
                        .apply()
                    allowedHosts.clear()
                    allowedHosts.addAll(cleaned)
                    adToggle.imageTintList = android.content.res.ColorStateList.valueOf(
                        if (adBlockerEnabled) heading else muted
                    )
                    webView.reload()
                }
                .show()
        }
        adToggle = android.widget.ImageButton(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_shield)
            imageTintList = android.content.res.ColorStateList.valueOf(if (adBlockerEnabled) heading else muted)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = translate("إعدادات حجب الإعلانات")
            isClickable = true
            isFocusable = true
            setBackground(GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), border)
            })
            setOnClickListener { showAdSettings() }
        }
        val blockedRequests = AtomicInteger(0)
        val blockedBadge = TextView(activity).apply {
            text = "0"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minWidth = dp(17)
            minHeight = dp(17)
            setPadding(dp(3), 0, dp(3), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(75, 82, 94))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), surface)
            }
        }
        val shieldContainer = android.widget.FrameLayout(activity).apply {
            addView(adToggle, android.widget.FrameLayout.LayoutParams(dp(46), dp(46)))
            addView(blockedBadge, android.widget.FrameLayout.LayoutParams(-2, dp(18), Gravity.END or Gravity.TOP))
        }
        addressRow.addView(shieldContainer, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(7) })
        val activePageUrl = AtomicReference(safeStartUrl)
        fun isBlockedRedirectDestination(url: String): Boolean =
            LocalBrowserAdBlocker.shouldBlockNavigation(url, adBlockerEnabled, allowedHosts)

        fun isBlockedAdRequest(url: String): Boolean =
            LocalBrowserAdBlocker.shouldBlockRequest(
                url = url,
                pageUrl = activePageUrl.get(),
                enabled = adBlockerEnabled,
                allowedHosts = allowedHosts,
                isDirectFile = ::isDirectFileUrl
            )

        fun recordBlocked(amount: Int = 1) {
            if (amount <= 0) return
            val count = blockedRequests.addAndGet(amount)
            activity.runOnUiThread {
                blockedBadge.text = if (count > 999) "999+" else count.toString()
                blockedBadge.contentDescription = translate("عدد الإعلانات المحجوبة: $count")
                blockedStats.text = translate("الإعلانات والمتتبعات المحجوبة: $count")
            }
        }
        fun applyCosmeticFiltering() {
            if (!adBlockerEnabled) return
            val script = """(function(){try{
              var selectors=['iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]',
                'iframe[src*="adservice"]','iframe[src*="adnxs"]','iframe[src*="outbrain"]',
                'iframe[src*="taboola"]','iframe[src*="popads"]','iframe[src*="popcash"]',
                'iframe[src*="propellerads"]','iframe[src*="exoclick"]','iframe[src*="adsterra"]',
                'iframe[src*="ad-delivery"]','iframe[src*="clickadu"]','iframe[src*="trafficstars"]',
                'iframe[src*="adnow"]','iframe[src*="adskeeper"]','iframe[src*="monetag"]',
                '[id^="google_ads"]','[id*="ad-container"]','[id*="ad_wrapper"]','[id*="ad_banner"]',
                '[id*="ad-popup"]','[id*="ad-overlay"]',
                '[class*="ad-container"]','[class*="ad-wrapper"]','[class*="advertisement"]',
                '[class*="advert-banner"]','[class*="ad-slot"]','[class*="ad-placement"]',
                '[class*="sponsored"]','[class*="sponsor-box"]','[class*="ad-banner"]',
                '[class*="ad-popup"]','[class*="ad-overlay"]','[class*="popunder"]',
                '[data-ad]','[data-ad-unit]',
                '[data-ad-slot]','[data-advertisement]','[data-google-query-id]',
                '[aria-label="advertisement" i]','ins.adsbygoogle','amp-ad',
                'amp-embed[type="taboola"]','div[id*="taboola"]','div[class*="taboola"]',
                'div[id*="outbrain"]','div[class*="outbrain"]',
                'a[href*="doubleclick.net"]','a[href*="popads.net"]','a[href*="popcash.net"]',
                'a[href*="propellerads.com"]','a[href*="exoclick.com"]'];
              function hideAds(root){var n=0;selectors.forEach(function(s){
                try{
                  if(root&&root.nodeType===1&&root.matches&&root.matches(s)&&root.getAttribute('data-marco-ad-hidden')!=='1'){
                    root.style.setProperty('display','none','important');root.setAttribute('data-marco-ad-hidden','1');n++;
                  }
                  root.querySelectorAll(s).forEach(function(e){
                    if(n<240&&e.getAttribute('data-marco-ad-hidden')!=='1'){e.style.setProperty('display','none','important');e.setAttribute('data-marco-ad-hidden','1');n++;}
                  });
                }catch(ignore){}
              });}
              var newlyHidden=hideAds(document);
              if(!window.__marcoAdObserver){
                var pending=false;
                window.__marcoAdObserver=new MutationObserver(function(records){
                  if(pending)return;pending=true;
                  requestAnimationFrame(function(){
                    pending=false;
                    records.forEach(function(record){record.addedNodes.forEach(function(node){
                      if(node&&node.nodeType===1){hideAds(node);}});
                    });
                  });
                });
                window.__marcoAdObserver.observe(document.documentElement,{childList:true,subtree:true});
              }
              return newlyHidden;
            }catch(e){return 0;}})();"""
            webView.evaluateJavascript(script) { raw ->
                val newlyHidden = runCatching {
                    (JSONTokener(raw ?: "0").nextValue() as? Number)?.toInt() ?: 0
                }.getOrDefault(0)
                recordBlocked(newlyHidden)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                // Retain narrow local fallbacks for known forced-redirect destinations.
                if (isBlockedAdRequest(request.url.toString())) {
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
                val destination = request.url.toString()
                if (adBlockerEnabled && isBlockedAdRequest(destination)) {
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
                activePageUrl.set(url)
                if (activeTabIndex in browserTabs.indices && url.startsWith("https://")) {
                    browserTabs[activeTabIndex] = browserTabs[activeTabIndex].first to url
                }
                swipeRefresh.isRefreshing = false
                progress.visibility = View.VISIBLE
                progress.progress = 0
                status.text = Uri.parse(url).host ?: translate("جارٍ فتح صفحة التحميل…")
                addressInput.setText(url)
                candidates.removeAllViews()
                candidateUrls.clear()
                candidateTitle.visibility = View.GONE
                candidateScroll.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                rememberPage(url, view.title.orEmpty())
                progress.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                status.text = translate("إذا ظهرت خطوة تحقق أو مؤقت، أكملها داخل هذه الصفحة. الروابط المحتملة تظهر أسفلها.")
                applyCosmeticFiltering()
                scanForFileLinks()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                swipeRefresh.isRefreshing = false
                status.text = translate("تعذر التحقق من أمان اتصال الموقع؛ تم إيقاف فتح الصفحة.")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    swipeRefresh.isRefreshing = false
                    status.text = translate("تعذر تحميل صفحة الموقع. جرّب فتحها بالمتصفح الخارجي.")
                }
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
                confirmDownload(safeUrl, contentDisposition.ifBlank { gameTitle }, mimeType.orEmpty(), contentDisposition)
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
        var lifecycleObserver: DefaultLifecycleObserver? = null
        val lifecycleOwner = activity as? LifecycleOwner
        dialog.setOnDismissListener {
            webView.stopLoading()
            webView.destroy()
            lifecycleObserver?.let { lifecycleOwner?.lifecycle?.removeObserver(it) }
        }
        dialog.show()
        dialog.window?.apply {
            setLayout(-1, -1)
            setGravity(Gravity.FILL)
            setBackgroundDrawableResource(android.R.color.transparent)
            // Keep the same immersive top-bar policy as the rest of the app.
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = background
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            androidx.core.view.WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightNavigationBars = !dark
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
        addressInput.setText(safeStartUrl)
        webView.loadUrl(safeStartUrl)
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (dialog.isShowing) dialog.dismiss()
                owner.lifecycle.removeObserver(this)
            }
        }
        lifecycleObserver = observer
        lifecycleOwner?.lifecycle?.addObserver(observer)
    }
}
