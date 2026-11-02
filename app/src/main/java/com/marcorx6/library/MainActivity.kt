
package com.marcorx6.library

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlertDialog
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

data class Game(
    val name: String,
    val platform: String,
    val description: String,
    val url: String,
    val coverUrl: String = "",
    val coverLocalPath: String = "",
    val sourcePostUrl: String = "",
    val downloadLinks: String = "",
    val blogImported: Boolean = false,
    val platformManuallyCorrected: Boolean = false,
    val isFavorite: Boolean = false,
    val bloggerPostId: String = "",
    val bloggerHtml: String = ""
)

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 11, 17)
    private val panel = Color.rgb(27, 29, 39)
    private val red = Color.rgb(255, 55, 75)
    private val white = Color.WHITE
    private val gray = Color.LTGRAY
    private val platforms = listOf("الكل", "BLOG", "FAVORITES", "PSP", "PS1", "PS2", "PS3", "PS4")
    private val games = mutableListOf<Game>()

    private lateinit var gameList: LinearLayout
    private lateinit var search: EditText
    private lateinit var filters: LinearLayout
    private lateinit var countText: TextView

    private var selected = "ALL"
    private var sortMode = "الأحدث"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val PICK_IMAGE_REQUEST = 1001
    private var pendingCoverLocalPath = ""
    private var coverPreview: ImageView? = null
    private var coverUrlField: EditText? = null
    private var isSyncing = false
    private var isAdminSession = false
    private var bloggerBlogId = ""
    private var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient? = null
    private val adminEmail = "xpangwan@gmail.com"
    private val SIGN_IN_REQUEST = 1002

    private val bloggerFeedUrl =
        "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        createNotificationChannel()
        configureGoogleSignIn()
        loadGames()
        restoreAdminSession()
        createInterface()
        if (notificationPreferences().getBoolean("enabled", true)) {
            scheduleBlogNotifications()
            requestNotificationPermissionIfNeeded()
        }
        syncFromBlog(showResult = false)
    }


    private fun configureGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestScopes(Scope("https://www.googleapis.com/auth/blogger")).build()
        googleSignInClient = GoogleSignIn.getClient(this, options)
    }
    private fun restoreAdminSession() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        isAdminSession = account?.email.equals(adminEmail, ignoreCase = true)
    }
    private fun startGoogleSignIn() { googleSignInClient?.let { startActivityForResult(it.signInIntent, SIGN_IN_REQUEST) } }
    private fun signOutAdmin() {
        googleSignInClient?.signOut()?.addOnCompleteListener { isAdminSession = false; createInterface() }
    }
    private fun adminToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب مسؤول المدونة")
        if (!account.email.equals(adminEmail, ignoreCase = true)) throw SecurityException("حساب Google غير مخوّل")
        val androidAccount = account.account ?: throw IllegalStateException("بيانات حساب Google غير متاحة")
        return GoogleAuthUtil.getToken(applicationContext, androidAccount, "oauth2:https://www.googleapis.com/auth/blogger")
    }
    private fun bloggerRequest(method: String, path: String, payload: JSONObject? = null): JSONObject {
        val connection = URL("https://www.googleapis.com/blogger/v3/$path").openConnection() as HttpURLConnection
        connection.requestMethod = method; connection.connectTimeout = 20000; connection.readTimeout = 25000
        connection.setRequestProperty("Authorization", "Bearer ${adminToken()}"); connection.setRequestProperty("Accept", "application/json")
        if (payload != null) { connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) } }
        val code = connection.responseCode; val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(); connection.disconnect()
        if (code !in 200..299) { val message = try { JSONObject(body).optJSONObject("error")?.optString("message") ?: body } catch (_: Exception) { body }; throw IllegalStateException("خطأ في Blogger API ($code): $message") }
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }
    private fun bloggerHtml(game: Game): String {
        val text = game.description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
        val image = if (game.coverUrl.isNotBlank()) "<p><img src=\"${game.coverUrl}\" /></p>" else ""
        return "<div><p>$text</p>$image<p><a href=\"${game.url}\">تحميل اللعبة</a></p></div>"
    }

    private fun notificationPreferences() =
        getSharedPreferences("notification_settings", MODE_PRIVATE)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BlogNotificationWorker.CHANNEL_ID,
                "New MARCO RX6 posts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new post is published on the MARCO RX6 blog"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun scheduleBlogNotifications() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BlogNotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BlogNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2002)
        }
    }

    private fun showNotificationSettings() {
        val enabled = notificationPreferences().getBoolean("enabled", true)
        AlertDialog.Builder(this)
            .setTitle("إشعارات المقالات الجديدة")
            .setMessage(if (enabled)
                "الإشعارات مفعّلة. يتحقق تطبيق MARCO RX6 من مدونتك دورياً وينبهك عند نشر مقال جديد."
            else
                "الإشعارات متوقفة. فعّلها ليصلك تنبيه عند نشر مقال جديد.")
            .setPositiveButton(if (enabled) "إبقاء مفعّلة" else "تفعيل") { _, _ ->
                notificationPreferences().edit().putBoolean("enabled", true).apply()
                scheduleBlogNotifications()
                requestNotificationPermissionIfNeeded()
                Toast.makeText(this, "تم تفعيل إشعارات المقالات الجديدة", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إيقاف") { _, _ ->
                notificationPreferences().edit().putBoolean("enabled", false).apply()
                WorkManager.getInstance(this).cancelUniqueWork(BlogNotificationWorker.WORK_NAME)
                Toast.makeText(this, "تم إيقاف الإشعارات", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("إغلاق", null)
            .show()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun makeInput(hintText: String): EditText =
        EditText(this).apply {
            hint = hintText
            textSize = 14f
            setTextColor(white)
            setHintTextColor(Color.GRAY)
            background = rounded(panel, 12)
            setPadding(dp(13), dp(10), dp(13), dp(10))
        }

    private fun createInterface() {
        // Fresh MARCO RX6 dashboard: editorial hero, compact controls and a clean image gallery.
        val root = LinearLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
            setBackgroundColor(Color.rgb(8, 10, 17))
        }
        @Suppress("DEPRECATION")
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(18), dp(14) + insets.systemWindowInsetTop,
                dp(18), dp(10) + insets.systemWindowInsetBottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val logo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        logo.addView(makeText("MARCO", 12f, Color.rgb(255, 83, 104), true))
        logo.addView(makeText("RX6", 29f, white, true))
        header.addView(logo, LinearLayout.LayoutParams(0, -2, 1f))
        val adminButton = TextView(this).apply {
            text = if (isAdminSession) "● مسؤول" else "دخول المسؤول  ↗"
            textSize = 10f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(31, 35, 49), 18)
            setPadding(dp(15), dp(11), dp(15), dp(11))
            setOnClickListener { if (isAdminSession) signOutAdmin() else startGoogleSignIn() }
        }
        header.addView(TextView(this).apply {
            text = "حسابي"; textSize = 10f; setTextColor(white); gravity = Gravity.CENTER
            background = rounded(Color.rgb(31, 35, 49), 18)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            setOnClickListener { showProfile() }
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        header.addView(adminButton)
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(91, 26, 48), Color.rgb(35, 27, 52), Color.rgb(19, 28, 45))
            ).apply { cornerRadius = dp(23).toFloat() }
            setPadding(dp(20), dp(19), dp(20), dp(19))
            elevation = dp(3).toFloat()
        }
        hero.addView(makeText("عالمك للألعاب", 10f, Color.rgb(255, 156, 169), true))
        hero.addView(makeText("اكتشف. العب.\nاستمتع أكثر.", 27f, white, true).apply {
            setPadding(0, dp(7), 0, dp(5))
        })
        hero.addView(makeText("ألعاب وشروحات وآخر الإصدارات، كلّها في مكان واحد.", 12f,
            Color.rgb(220, 220, 231)))
        val heroStats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        heroStats.addView(makeText("✦  ${games.size} مقال", 10f, white, true))
        heroStats.addView(makeText("     •     المدونة محدّثة", 10f, Color.rgb(255, 178, 190), true))
        hero.addView(heroStats)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(24, 27, 39), 15)
            setPadding(dp(13), 0, dp(13), 0)
        }
        searchWrap.addView(makeText("⌕", 25f, Color.rgb(255, 83, 104), true))
        search = EditText(this).apply {
            hint = "ابحث عن الألعاب والمقالات..."
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(Color.rgb(137, 143, 158))
            background = null
            setPadding(dp(10), 0, dp(6), 0)
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(searchWrap, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(15)
        })

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        (listOf("ALL", "BLOG", "FAVORITES", "الأخيرة", "الأكثر مشاهدة") + platforms.drop(3)).forEach { category ->
            val chip = TextView(this).apply {
                text = when (category) { "ALL" -> "الكل"; "BLOG" -> "المدونة"; "FAVORITES" -> "المفضلة"; "RECENT" -> "شوهدت مؤخراً"; "POPULAR" -> "الأكثر مشاهدة"; else -> category }
                tag = category
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(16), dp(11), dp(16), dp(11))
                setOnClickListener { selected = category; renderGames() }
            }
            filters.addView(chip, LinearLayout.LayoutParams(-2, -2).apply {
                rightMargin = dp(8)
            })
        }
        filterScroll.addView(filters)
        root.addView(filterScroll, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(13)
            bottomMargin = dp(15)
        })

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sectionTitles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sectionTitles.addView(makeText("استكشف", 10f, Color.rgb(255, 83, 104), true))
        sectionTitles.addView(makeText("أحدث المقالات", 20f, white, true))
        section.addView(sectionTitles, LinearLayout.LayoutParams(0, -2, 1f))
        countText = makeText("0 مقال", 10f, Color.rgb(255, 112, 130), true)
        section.addView(countText)
        val sortButton = TextView(this).apply {
            text = "↕"
            textSize = 19f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(31, 35, 49), 12)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            contentDescription = "ترتيب المقالات"
            setOnClickListener {
                sortMode = when (sortMode) { "LATEST" -> "POPULAR"; "POPULAR" -> "A-Z"; "A-Z" -> "FAVORITES"; else -> "LATEST" }
                renderGames()
                Toast.makeText(this@MainActivity, "الترتيب: " + when (sortMode) { "LATEST" -> "الأحدث"; "POPULAR" -> "الأكثر مشاهدة"; "A-Z" -> "أبجدياً"; else -> "المفضلة" }, Toast.LENGTH_SHORT).show()
            }
        }
        section.addView(sortButton, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(9) })
        root.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        gameList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(9), 0, dp(18))
        }
        scroll.addView(gameList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(20, 23, 34), 17)
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        fun navAction(label: String, accent: Boolean = false, action: () -> Unit) =
            TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(white)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = rounded(if (accent) Color.rgb(205, 48, 72) else Color.TRANSPARENT, 12)
                setPadding(dp(10), dp(12), dp(10), dp(12))
                setOnClickListener { action() }
            }
        actions.addView(navAction("⟳  مزامنة") { syncFromBlog(showResult = true) },
            LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(navAction("♧  التنبيهات") { showNotificationSettings() },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (isAdminSession) {
            actions.addView(navAction("＋  نشر", true) { showGameDialog() },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        setContentView(root)
        root.requestApplyInsets()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderGames() }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderGames()
    }

    private fun syncFromBlog(showResult: Boolean) {

        if (isSyncing) {
            if (showResult) {
                Toast.makeText(
                    this,
                    "المزامنة جارية بالفعل",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        isSyncing = true

        if (showResult) {
            Toast.makeText(
                this,
                "جارٍ الاتصال بمدونة MARCO RX6...",
                Toast.LENGTH_SHORT
            ).show()
        }

        Thread {
            try {
                val connection =
                    URL(bloggerFeedUrl).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty(
                    "User-Agent",
                    "MarcoRX6-Android/3.0"
                )
                connection.setRequestProperty("Accept", "application/json")

                val body = try {
                    connection.inputStream.bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                } finally {
                    connection.disconnect()
                }

                val feed = JSONObject(body).optJSONObject("feed")
                    ?: JSONObject()
                val feedId = feed.optJSONObject("id")?.optString(36.toChar().toString() + "t", "").orEmpty()
                Regex("blog-(\\d+)").find(feedId)?.groupValues?.getOrNull(1)?.let { bloggerBlogId = it }

                val entries = feed.optJSONArray("entry")
                    ?: JSONArray()

                val imported = mutableListOf<Game>()

                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val game = parseBlogEntry(entry) ?: continue
                    imported.add(game)
                }

                mainHandler.post {
                    val result = mergeImportedGames(imported)
                    isSyncing = false
                    renderGames()

                    if (showResult) {
                        Toast.makeText(
                            this,
                            "اكتملت المزامنة: تمت إضافة ${result.first} وتحديث ${result.second}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    showPlatformWarnings(result.third)
                }

            } catch (e: Exception) {
                mainHandler.post {
                    isSyncing = false

                    if (showResult) {
                        Toast.makeText(
                            this,
                            "تعذرت المزامنة. تحقق من اتصال الإنترنت وحاول مجدداً.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }

    private fun mergeImportedGames(
        imported: List<Game>
    ): Triple<Int, Int, List<Pair<Game, String>>> {

        var added = 0
        var updated = 0
        val warnings = mutableListOf<Pair<Game, String>>()

        imported.forEach { incoming ->

            val byPost = games.indexOfFirst {
                it.sourcePostUrl.isNotBlank() &&
                    it.sourcePostUrl == incoming.sourcePostUrl
            }

            if (byPost >= 0) {

                val old = games[byPost]

                games[byPost] = incoming.copy(
                    coverLocalPath = old.coverLocalPath,
                    coverUrl = if (old.coverLocalPath.isNotBlank())
                        old.coverUrl else incoming.coverUrl,
                    platform = if (old.platformManuallyCorrected)
                        old.platform else incoming.platform,
                    platformManuallyCorrected = old.platformManuallyCorrected,
                    isFavorite = old.isFavorite,
                    bloggerPostId = incoming.bloggerPostId,
                    bloggerHtml = incoming.bloggerHtml
                )

                updated++

            } else {

                val manualDuplicate = games.any {
                    !it.blogImported &&
                        it.name.equals(incoming.name, ignoreCase = true)
                }

                if (!manualDuplicate) {
                    games.add(incoming)
                    added++

                    val mismatch = findPlatformMismatch(incoming)
                    if (mismatch != null) {
                        warnings.add(incoming to mismatch)
                    }
                }
            }
        }

        saveGames()
        return Triple(added, updated, warnings)
    }

    private fun showPlatformWarnings(
        warnings: List<Pair<Game, String>>
    ) {
        if (warnings.isEmpty() || isFinishing) return

        val (game, detected) = warnings.first()

        AlertDialog.Builder(this)
            .setTitle("تعارض في معلومات المنصة")
            .setMessage(
                "${game.name}\n\nBlogger category: ${game.platform}" +
                    "\nOperating system field: $detected" +
                    "\n\nThe app has used the Blogger category. " +
                    "هل تريد تصحيح المنصة يدوياً؟"
            )
            .setNegativeButton("الإبقاء على التصنيف", null)
            .setPositiveButton("تصحيح") { _, _ ->
                showPlatformCorrection(game)
            }
            .show()
    }

    private fun showPlatformCorrection(game: Game) {

        val choices = platforms.drop(1).toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("اختر المنصة")
            .setItems(choices) { _, which ->

                val index = games.indexOfFirst {
                    it.sourcePostUrl == game.sourcePostUrl
                }

                if (index >= 0) {
                    games[index] = games[index].copy(
                        platform = choices[which],
                        platformManuallyCorrected = true
                    )

                    saveGames()
                    renderGames()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun findPlatformMismatch(game: Game): String? {

        val description = game.description

        val regex = Regex(
            "(?i)(?:نظام\\s*التشغيل|operating\\s*system)" +
                "\\s*[:：\\-]?\\s*(PlayStation\\s*[1-4]|PS[1-4]|PSP)"
        )

        val found = regex.find(description)
            ?.groupValues?.getOrNull(1) ?: return null

        val fieldPlatform = normalizePlatform(found) ?: return null

        return if (fieldPlatform != game.platform) fieldPlatform else null
    }

    private fun parseBlogEntry(entry: JSONObject): Game? {

        val title = entry.optJSONObject("title")
            ?.optString("\$t", "")?.trim().orEmpty()

        if (title.isBlank()) return null

        val content = entry.optJSONObject("content")
            ?.optString("\$t", "")
            ?: entry.optJSONObject("summary")
                ?.optString("\$t", "").orEmpty()

        val postUrl = findPostUrl(entry)
        if (postUrl.isBlank()) return null

        val platform = platformFromCategories(entry) ?: "BLOG"
        val image = extractFirstImage(content)
        val cleanText = htmlToText(content)
        val links = extractDownloadLinks(content)

        return Game(
            name = title,
            platform = platform,
            description = cleanText.take(1800),
            url = postUrl,
            coverUrl = image,
            sourcePostUrl = postUrl,
            downloadLinks = links,
            blogImported = true,
            bloggerPostId = entry.optJSONObject("id")?.optString(36.toChar().toString() + "t", "")?.let { Regex("post-(\\d+)").find(it)?.groupValues?.getOrNull(1).orEmpty() } ?: "",
            bloggerHtml = content
        )
    }

    private fun findPostUrl(entry: JSONObject): String {

        val links = entry.optJSONArray("link") ?: return ""

        for (i in 0 until links.length()) {

            val item = links.optJSONObject(i) ?: continue

            if (item.optString("rel") == "alternate") {
                val href = item.optString("href", "")

                if (href.startsWith("https://")) {
                    return href.substringBefore("?")
                }
            }
        }

        return ""
    }

    private fun platformFromCategories(entry: JSONObject): String? {

        val categories = entry.optJSONArray("category")
            ?: return null

        for (i in 0 until categories.length()) {

            val term = categories.optJSONObject(i)
                ?.optString("term", "") ?: continue

            val platform = normalizePlatform(term)

            if (platform != null) return platform
        }

        return null
    }

    private fun normalizePlatform(value: String): String? {

        val s = value.lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("-", "")

        return when {
            s == "psp" || s.contains("playstationportable") -> "PSP"
            s == "ps1" || s.contains("playstation1") ||
                s.contains("playstationone") -> "PS1"
            s == "ps2" || s.contains("playstation2") -> "PS2"
            s == "ps3" || s.contains("playstation3") -> "PS3"
            s == "ps4" || s.contains("playstation4") -> "PS4"
            else -> null
        }
    }

    private fun extractFirstImage(html: String): String {

        val pattern = Pattern.compile(
            "(?i)<img[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']"
        )

        val matcher = pattern.matcher(html)

        if (matcher.find()) {
            val src = matcher.group(1) ?: ""

            if (src.startsWith("https://") || src.startsWith("http://")) {
                return src
            }
        }

        return ""
    }

    private fun extractDownloadLinks(html: String): String {

        val result = JSONArray()

        val pattern = Pattern.compile(
            "(?is)<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>"
        )

        val matcher = pattern.matcher(html)

        while (matcher.find()) {

            val href = matcher.group(1)?.trim().orEmpty()
            val labelHtml = matcher.group(2).orEmpty()

            @Suppress("DEPRECATION")
            val label = Html.fromHtml(labelHtml).toString().trim()

            if (href.startsWith("https://") &&
                label.isNotBlank() &&
                (
                    label.contains("اللعبة") ||
                    label.contains("التحديث") ||
                    label.contains("الإضافات") ||
                    label.contains("تحميل") ||
                    label.contains("Game", true) ||
                    label.contains("Update", true) ||
                    label.contains("DLC", true)
                )
            ) {
                result.put(
                    JSONObject()
                        .put("label", label)
                        .put("url", href)
                )
            }
        }

        return result.toString()
    }

    private fun htmlToText(html: String): String {

        val withBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(
                Regex("(?i)</p>|</div>|</li>|</h[1-6]>"),
                "\n"
            )

        @Suppress("DEPRECATION")
        return Html.fromHtml(withBreaks).toString()
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    // IMAGE LOADER
    private fun loadCover(
        image: ImageView,
        localPath: String,
        imageUrl: String
    ) {

        val key = localPath.ifBlank { imageUrl }

        image.tag = key
        image.setImageResource(android.R.drawable.ic_menu_gallery)

        if (key.isBlank()) return

        Thread {
            try {

                val bitmap = if (localPath.isNotBlank()) {

                    BitmapFactory.decodeFile(localPath)

                } else {

                    val connection =
                        URL(imageUrl).openConnection() as HttpURLConnection

                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.instanceFollowRedirects = true

                    try {
                        connection.connect()
                        connection.inputStream.use {
                            BitmapFactory.decodeStream(it)
                        }
                    } finally {
                        connection.disconnect()
                    }
                }

                if (bitmap != null) {
                    mainHandler.post {
                        if (image.tag == key) {
                            image.setImageBitmap(bitmap)
                        }
                    }
                }

            } catch (_: Exception) {
            }
        }.start()
    }

    private fun chooseCover() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    @Deprecated("Using activity result for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SIGN_IN_REQUEST && resultCode == RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException::class.java)
                isAdminSession = account.email.equals(adminEmail, ignoreCase = true)
                if (!isAdminSession) googleSignInClient?.signOut()
                createInterface()
                Toast.makeText(this, if (isAdminSession) "تم ربط حساب Google المسؤول" else "هذا الحساب لا يملك صلاحية المسؤول. يمكنك المتابعة كزائر.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { isAdminSession = false; createInterface(); Toast.makeText(this, "فشل تسجيل الدخول إلى Google: ${e.message}", Toast.LENGTH_LONG).show() }
            return
        }
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != RESULT_OK) return

        val uri: Uri = data?.data ?: return

        Thread {
            try {

                val folder = File(filesDir, "covers")
                if (!folder.exists()) folder.mkdirs()

                val destination = File(
                    folder,
                    "cover_${System.currentTimeMillis()}.img"
                )

                contentResolver.openInputStream(uri).use { input ->

                    if (input == null) {
                        throw Exception("تعذرت قراءة الصورة")
                    }

                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }

                mainHandler.post {

                    pendingCoverLocalPath = destination.absolutePath
                    coverUrlField?.setText("")

                    coverPreview?.let {
                        loadCover(it, pendingCoverLocalPath, "")
                    }

                    Toast.makeText(
                        this,
                        "تم اختيار صورة الغلاف",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (_: Exception) {

                mainHandler.post {
                    Toast.makeText(
                        this,
                        "تعذر تحميل هذه الصورة",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }


    private fun profilePreferences() = getSharedPreferences("user_profile", MODE_PRIVATE)
    private fun historyPreferences() = getSharedPreferences("article_activity", MODE_PRIVATE)
    private fun articleKey(game: Game): String = (game.url.ifBlank { game.name }).hashCode().toString()
    private fun viewCount(game: Game): Int = historyPreferences().getInt("views_" + articleKey(game), 0)
    private fun lastViewedAt(game: Game): Long = historyPreferences().getLong("time_" + articleKey(game), 0L)
    private fun isRecentlyViewed(game: Game): Boolean = lastViewedAt(game) > 0L
    private fun recordArticleView(game: Game) {
        val prefs = historyPreferences()
        val key = articleKey(game)
        prefs.edit().putInt("views_" + key, viewCount(game) + 1)
            .putLong("time_" + key, System.currentTimeMillis()).apply()
    }
    private fun showProfile() {
        val prefs = profilePreferences()
        val input = makeInput("اسم العرض").apply {
            setSingleLine(true)
            setText(prefs.getString("nickname", "Player") ?: "Player")
        }
        val summary = "المفضلة: " + games.count { it.isFavorite } +
            "\nشوهدت مؤخراً: " + games.count { isRecentlyViewed(it) }
        AlertDialog.Builder(this).setTitle("ملفي الشخصي")
            .setMessage("ملف اللاعب على هذا الجهاز\n\n" + summary)
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.edit().putString("nickname", input.text.toString().trim().ifBlank { "Player" }).apply()
                Toast.makeText(this, "تم حفظ الملف الشخصي", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("نشاطي") { _, _ -> selected = "RECENT"; createInterface() }
            .setNegativeButton("إغلاق", null).show()
    }

    // BLOG-STYLE ARTICLE FEED
    // IMAGE-FIRST BLOG GALLERY: two clean cover tiles per row, no text overlays.
    private fun renderGames() {
        gameList.removeAllViews()
        for (i in 0 until filters.childCount) {
            val chip = filters.getChildAt(i) as TextView
            val active = chip.tag?.toString() == selected
            chip.background = rounded(if (active) red else panel, 15)
            chip.setTextColor(if (active) white else gray)
        }
        val query = search.text.toString().lowercase(Locale.ROOT).trim()
        val filtered = games.filter { item ->
            val categoryMatch = when (selected) {
                "ALL" -> true
                "FAVORITES" -> item.isFavorite
                "RECENT" -> isRecentlyViewed(item)
                "POPULAR" -> true
                "BLOG" -> item.blogImported
                else -> item.platform == selected
            }
            val searchMatch = item.name.lowercase(Locale.ROOT).contains(query) ||
                item.platform.lowercase(Locale.ROOT).contains(query) ||
                item.description.lowercase(Locale.ROOT).contains(query)
            categoryMatch && searchMatch
        }
        val results = when (sortMode) {
            "A-Z" -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            "FAVORITES" -> filtered.sortedByDescending { it.isFavorite }
            "POPULAR" -> filtered.sortedByDescending { viewCount(it) }
            else -> if (selected == "RECENT") filtered.sortedByDescending { lastViewedAt(it) } else filtered
        }
        countText.text = "${results.size} مقال"
        if (results.isEmpty()) {
            gameList.addView(makeText("لا توجد مقالات حالياً", 15f, gray, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(55), dp(10), dp(55))
            })
            return
        }

        // BLOG is a pure cover gallery: one post = one image, with no text or metadata.
        // Other platform filters keep the compact two-column artwork grid.
        val blogGallery = selected == "BLOG"
        var row: LinearLayout? = null
        results.forEachIndexed { index, post ->
            val columns = if (blogGallery) 1 else 2
            if (index % columns == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }
                gameList.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(10)
                })
            }
            val tile = FrameLayout(this).apply {
                background = rounded(panel, 14)
                clipToOutline = true
                setOnClickListener { showDetails(post) }
                contentDescription = post.name
            }
            val cover = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(32, 35, 47))
                contentDescription = post.name
            }
            val imageHeight = if (blogGallery) 230 else 220
            tile.addView(cover, FrameLayout.LayoutParams(-1, dp(imageHeight)))
            if (post.coverUrl.isNotBlank() || post.coverLocalPath.isNotBlank()) {
                loadCover(cover, post.coverLocalPath, post.coverUrl)
            } else {
                cover.setImageResource(android.R.drawable.ic_menu_gallery)
                cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val tileWidth = if (blogGallery) LinearLayout.LayoutParams(-1, dp(imageHeight))
                else LinearLayout.LayoutParams(0, dp(imageHeight), 1f).apply {
                    if (index % 2 == 0) rightMargin = dp(5) else leftMargin = dp(5)
                }
            row?.addView(tile, tileWidth)
        }
    }

    // Dedicated immersive article reader; the WebView owns its scroll instead of nesting inside ScrollView.
    private fun showDetails(game: Game) {
        recordArticleView(game)
        val reader = android.app.Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }
        val close = TextView(this).apply {
            text = "‹"
            textSize = 30f
            setTextColor(white)
            gravity = Gravity.CENTER
            setOnClickListener { reader.dismiss() }
        }
        topBar.addView(close, LinearLayout.LayoutParams(dp(42), dp(44)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(makeText(game.name, 16f, white, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        heading.addView(makeText(game.platform, 10f, red, true))
        topBar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val favorite = TextView(this).apply {
            text = if (game.isFavorite) "★" else "☆"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(if (game.isFavorite) red else white)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                val index = games.indexOfFirst { it.name == game.name && it.url == game.url }
                if (index >= 0) {
                    games[index] = games[index].copy(isFavorite = !games[index].isFavorite)
                    saveGames()
                    text = if (games[index].isFavorite) "★" else "☆"
                    setTextColor(if (games[index].isFavorite) red else white)
                    renderGames()
                }
            }
        }
        topBar.addView(favorite)
        root.addView(topBar)

        val articleView = android.webkit.WebView(this).apply {
            setBackgroundColor(bg)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.loadsImagesAutomatically = true
            val raw = if (game.bloggerHtml.isNotBlank()) game.bloggerHtml else
                "<h2>${game.name}</h2><p>${game.description}</p>"
            val articleHtml = raw.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
                .replace(Regex("(?i)\\s(onload|onclick|onerror)\\s*=\\s*(['\"]).*?\\2"), "")
            val style = """
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                *{box-sizing:border-box}
                html,body{margin:0;padding:0;width:100%;max-width:100%;overflow-x:hidden}
                body{background:#0a0b11;color:#f4f4f8;font-family:Roboto,Arial,sans-serif;font-size:16px;line-height:1.85;padding:18px 16px 32px;overflow-wrap:anywhere;direction:rtl;text-align:right}
                h1,h2,h3,h4{color:#ff6478;line-height:1.35;margin:25px 0 12px}
                p{margin:0 0 17px}
                img{display:block;max-width:100%!important;height:auto!important;width:auto;margin:18px auto;border-radius:12px;object-fit:contain}
                a{color:#79c5ff;text-decoration:none;font-weight:600}
                ul,ol{padding-left:25px;margin:10px 0 20px}
                li{padding:4px 0}
                blockquote{border-left:3px solid #ff6478;padding:8px 14px;margin:16px 0;background:#1b1d27}
                table{display:block;max-width:100%;overflow-x:auto;border-collapse:collapse}
                td,th{border:1px solid #444;padding:8px}
                iframe,video{max-width:100%!important}
                </style>
            """.trimIndent()
            loadDataWithBaseURL(game.url.ifBlank { "https://marcorx6.blogspot.com/" },
                "<!doctype html><html lang="ar" dir="rtl"><head>$style</head><body dir="rtl">$articleHtml</body></html>",
                "text/html", "UTF-8", null)
        }
        root.addView(articleView, LinearLayout.LayoutParams(-1, 0, 1f))
        val downloads = try { JSONArray(game.downloadLinks) } catch (_: Exception) { JSONArray() }
        if (downloads.length() > 0) {
            val downloadScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(panel) }
            val downloadRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(9), dp(12), dp(9)) }
            for (i in 0 until downloads.length()) {
                val item = downloads.optJSONObject(i) ?: continue
                val target = item.optString("url")
                if (!target.startsWith("https://")) continue
                downloadRow.addView(TextView(this).apply {
                    text = "⬇  " + item.optString("label").ifBlank { "تحميل " + (i + 1) }
                    textSize = 12f; setTextColor(white); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
                    background = rounded(Color.rgb(190, 43, 68), 11)
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                    setOnClickListener { openGameLink(target) }
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            }
            downloadScroll.addView(downloadRow)
            root.addView(downloadScroll)
        }

        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setBackgroundColor(Color.rgb(18, 21, 31))
        }
        ratingRow.addView(makeText("قيّم هذا المقال", 10f, gray, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val ratingPrefs = historyPreferences()
        val ratingKey = "rating_" + articleKey(game)
        val selectedRating = ratingPrefs.getInt(ratingKey, 0)
        for (star in 1..5) {
            ratingRow.addView(TextView(this).apply {
                text = if (star <= selectedRating) "★" else "☆"
                textSize = 22f
                setTextColor(if (star <= selectedRating) Color.rgb(255, 190, 75) else gray)
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnClickListener {
                    ratingPrefs.edit().putInt(ratingKey, star).apply()
                    for (index in 0 until ratingRow.childCount) {
                        val child = ratingRow.getChildAt(index)
                        if (index > 0) {
                            val score = index
                            (child as TextView).text = if (score <= star) "★" else "☆"
                            child.setTextColor(if (score <= star) Color.rgb(255, 190, 75) else gray)
                        }
                    }
                    Toast.makeText(this@MainActivity, "تقييمك: $star من 5", Toast.LENGTH_SHORT).show()
                }
            })
        }
        root.addView(ratingRow)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(panel)
        }
        fun readerAction(label: String, action: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(white)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = rounded(Color.rgb(48, 53, 70), 11)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { action() }
        }
        val share = readerAction("مشاركة") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, game.name)
                putExtra(Intent.EXTRA_TEXT, game.name + "\n" + game.url)
            }
            startActivity(Intent.createChooser(send, "مشاركة مقال من MARCO RX6"))
        }
        bottom.addView(share, LinearLayout.LayoutParams(0, -2, 1f))
        val comments = readerAction("التعليقات") { if (game.url.isNotBlank()) openGameLink(game.url) }
        bottom.addView(comments, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        if (isAdminSession) {
            val edit = readerAction("تعديل") { reader.dismiss(); showGameDialog(game) }
            bottom.addView(edit, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            val delete = readerAction("حذف") { reader.dismiss(); deleteGame(game) }
            bottom.addView(delete, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }
        root.addView(bottom)
        reader.setContentView(root)
        reader.window?.setBackgroundDrawableResource(android.R.color.transparent)
        reader.show()
        reader.window?.setLayout(-1, -1)
    }

    // BLOG POST COMPOSER: reusable article template published directly to Blogger
    private fun showGameDialog(existing: Game? = null) {
        if (!isAdminSession) { Toast.makeText(this, "يجب تسجيل الدخول بصلاحية المسؤول للنشر.", Toast.LENGTH_SHORT).show(); return }
        pendingCoverLocalPath = existing?.coverLocalPath ?: ""
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(5), dp(18), dp(5)) }
        val title = makeInput("عنوان المقال").apply { setSingleLine(true) }
        val category = Spinner(this)
        val categoryOptions = listOf("BLOG", "PSP", "PS1", "PS2", "PS3", "PS4")
        category.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryOptions)
        val body = makeInput("محتوى المقال (يدعم HTML)").apply { minLines = 10; gravity = Gravity.TOP; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        val coverUrl = makeInput("رابط صورة الغلاف (اختياري)").apply { setSingleLine(true) }
        val link = makeInput("رابط التحميل أو رابط إضافي (اختياري)").apply { setSingleLine(true) }
        coverUrlField = coverUrl
        if (existing != null) {
            title.setText(existing.name)
            category.setSelection(categoryOptions.indexOf(existing.platform).coerceAtLeast(0))
            body.setText(if (existing.bloggerHtml.isNotBlank()) existing.bloggerHtml else existing.description)
            coverUrl.setText(existing.coverUrl)
            link.setText(existing.url.takeIf { it != existing.sourcePostUrl } ?: "")
        } else {
            body.setText("""<p>أهلاً بكم متابعي مدونة MARCO_RX6 الكرام. إليكم لعبة جديدة من إضافاتنا الحصرية بروابط مباشرة وسريعة.</p>

<h3 style="color: #8b5cf6;">معلومات اللعبة:</h3>
<ul>
  <li><strong>الحجم:</strong> </li>
  <li><strong>النسخة:</strong> </li>
  <li><strong>الصيغة:</strong> PKG</li>
  <li><strong>اللغة:</strong> الإنجليزية</li>
  <li><strong>كلمة السر:</strong> </li>
  <li><strong>الحقوق:</strong> </li>
</ul>

<h3 style="color: #8b5cf6;">روابط التحميل المباشرة:</h3>
<p><a href="https://example.com" target="_blank">ضع رابط تحميل هنا</a></p>
<p><a href="https://example.com" target="_blank">ضع رابط تحميل هنا</a></p>
<p><a href="https://example.com" target="_blank">ضع رابط تحميل هنا</a></p>""")
        }
        val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(panel, 13) }
        coverPreview = preview
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(145)).apply { bottomMargin = dp(8) })
        loadCover(preview, pendingCoverLocalPath, existing?.coverUrl ?: "")
        box.addView(Button(this).apply { text = "اختيار صورة الغلاف من الهاتف"; setTextColor(white); background = rounded(red, 12); setOnClickListener { chooseCover() } }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(makeText("تصنيف المقال", 10f, red, true)); box.addView(category); box.addView(title)
        box.addView(makeText("محتوى المقال", 10f, red, true).apply { setPadding(0, dp(8), 0, dp(4)) })
        box.addView(body, LinearLayout.LayoutParams(-1, dp(190))); box.addView(coverUrl); box.addView(link)
        val scroll = ScrollView(this).apply { addView(box) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "مقال جديد" else "تعديل المقال").setView(scroll).setNegativeButton("CANCEL", null).setPositiveButton("نشر المقال", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val postTitle = title.text.toString().trim(); val categoryName = category.selectedItem.toString(); val coverLink = coverUrl.text.toString().trim(); val relatedLink = link.text.toString().trim()
                if (postTitle.isBlank()) { title.error = "أدخل عنوان المقال"; return@setOnClickListener }
                if (coverLink.isNotBlank() && !coverLink.startsWith("https://")) { coverUrl.error = "أدخل رابط صورة يبدأ بـ HTTPS"; return@setOnClickListener }
                if (relatedLink.isNotBlank() && !relatedLink.startsWith("https://")) { link.error = "أدخل رابطاً يبدأ بـ HTTPS"; return@setOnClickListener }
                val contentText = body.text.toString().trim()
                // Accept real Blogger HTML (headings, lists, images and download anchors).
                // Plain text is still escaped safely and converted to line breaks.
                val hasHtml = Regex("(?is)<\\s*(p|div|h[1-6]|ul|ol|li|a|img|table|br)\\b").containsMatchIn(contentText)
                val escaped = contentText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>")
                val articleBody = if (hasHtml) contentText else "<p>$escaped</p>"
                val imageHtml = if (coverLink.isNotBlank() && !articleBody.contains(coverLink)) "<p><img src=\"$coverLink\" style=\"max-width:100%;height:auto;\"/></p>" else ""
                val relatedHtml = if (relatedLink.isNotBlank() && !articleBody.contains(relatedLink)) "<p><a href=\"$relatedLink\">تحميل / رابط إضافي</a></p>" else ""
                val htmlContent = "<div class=\"marcorx6-post\">$imageHtml$articleBody$relatedHtml</div>"
                val post = Game(name = postTitle, platform = categoryName, description = contentText, url = relatedLink.ifBlank { existing?.url ?: "" }, coverUrl = coverLink, coverLocalPath = if (coverLink.isBlank()) pendingCoverLocalPath else "", sourcePostUrl = existing?.sourcePostUrl ?: "", downloadLinks = existing?.downloadLinks ?: "", blogImported = true, isFavorite = existing?.isFavorite ?: false, bloggerPostId = existing?.bloggerPostId ?: "", bloggerHtml = htmlContent)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Thread {
                    try {
                        if (bloggerBlogId.isBlank()) throw IllegalStateException("زامن المدونة أولاً، ثم حاول النشر.")
                        val payload = JSONObject().put("kind", "blogger#post").put("title", post.name).put("content", htmlContent).put("labels", JSONArray().put(categoryName))
                        val result = if (existing == null) bloggerRequest("POST", "blogs/$bloggerBlogId/posts/", payload) else { if (existing.bloggerPostId.isBlank()) throw IllegalStateException("لا يملك هذا المقال معرّف Blogger. زامن المدونة ثم حاول مجدداً."); bloggerRequest("PUT", "blogs/$bloggerBlogId/posts/${existing.bloggerPostId}", payload) }
                        mainHandler.post {
                            val saved = post.copy(bloggerPostId = result.optString("id", post.bloggerPostId), bloggerHtml = result.optString("content", htmlContent), url = result.optString("url", post.url), sourcePostUrl = result.optString("url", post.sourcePostUrl), blogImported = true)
                            val idx = if (existing == null) -1 else games.indexOf(existing); if (idx >= 0) games[idx] = saved else games.add(0, saved)
                            saveGames(); renderGames(); dialog.dismiss(); Toast.makeText(this, "تم نشر المقال على Blogger", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) { mainHandler.post { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; Toast.makeText(this, e.message ?: "فشل نشر المقال", Toast.LENGTH_LONG).show() } }
                }.start()
            }
        }
        dialog.show()
    }
    private fun deleteGame(game: Game) {

        AlertDialog.Builder(this)
            .setTitle("حذف المقال")
            .setMessage("هل تريد حذف المقال ${game.name}؟")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("حذف") { _, _ ->
                if (!isAdminSession || bloggerBlogId.isBlank() || game.bloggerPostId.isBlank()) { Toast.makeText(this, "يلزم تسجيل دخول المسؤول ومعرّف المقال في Blogger", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                Thread { try {
                    bloggerRequest("DELETE", "blogs/$bloggerBlogId/posts/${game.bloggerPostId}")
                    mainHandler.post { games.remove(game); if (game.coverLocalPath.isNotBlank()) File(game.coverLocalPath).delete(); saveGames(); renderGames(); Toast.makeText(this, "تم الحذف من Blogger", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) { mainHandler.post { Toast.makeText(this, e.message ?: "تعذر حذف المقال من Blogger", Toast.LENGTH_LONG).show() } } }.start()
            }
            .show()
    }

    private fun openGameLink(link: String) {

        if (link.isBlank()) {
            Toast.makeText(
                this,
                "لا يوجد رابط متاح",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(link)
                )
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "تعذر فتح هذا الرابط",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun saveGames() {

        val array = JSONArray()

        games.forEach { game ->

            array.put(
                JSONObject()
                    .put("name", game.name)
                    .put("platform", game.platform)
                    .put("description", game.description)
                    .put("url", game.url)
                    .put("coverUrl", game.coverUrl)
                    .put("coverLocalPath", game.coverLocalPath)
                    .put("sourcePostUrl", game.sourcePostUrl)
                    .put("downloadLinks", game.downloadLinks)
                    .put("blogImported", game.blogImported)
                    .put(
                        "platformManuallyCorrected",
                        game.platformManuallyCorrected
                    )
                    .put("isFavorite", game.isFavorite)
                    .put("bloggerPostId", game.bloggerPostId)
                    .put("bloggerHtml", game.bloggerHtml)
            )
        }

        getSharedPreferences(
            "games_library",
            MODE_PRIVATE
        ).edit()
            .putString("games", array.toString())
            .apply()
    }

    private fun loadGames() {

        val saved = getSharedPreferences(
            "games_library",
            MODE_PRIVATE
        ).getString("games", null) ?: return

        try {

            val array = JSONArray(saved)

            for (i in 0 until array.length()) {

                val item = array.getJSONObject(i)

                games.add(
                    Game(
                        item.optString("name"),
                        item.optString("platform"),
                        item.optString("description"),
                        item.optString("url"),
                        item.optString("coverUrl", ""),
                        item.optString("coverLocalPath", ""),
                        item.optString("sourcePostUrl", ""),
                        item.optString("downloadLinks", ""),
                        item.optBoolean("blogImported", false),
                        item.optBoolean(
                            "platformManuallyCorrected",
                            false
                        ),
                        item.optBoolean("isFavorite", false),
                        item.optString("bloggerPostId", ""),
                        item.optString("bloggerHtml", "")
                    )
                )
            }

        } catch (_: Exception) {
            games.clear()
        }
    }
}
