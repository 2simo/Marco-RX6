
package com.marcorx6.library

import android.Manifest
import android.animation.LayoutTransition
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.Calendar
import java.util.regex.Pattern
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.security.MessageDigest

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
    val bloggerHtml: String = "",
    val playStatus: String = "",
    val genre: String = "",
    val releaseYear: String = ""
)

class MainActivity : Activity() {

    private fun appearancePreferences() = getSharedPreferences("appearance_settings", MODE_PRIVATE)
    private fun isDarkMode() = appearancePreferences().getBoolean("dark_mode", true)

    private fun applySystemBarAppearance() {
        // Draw edge-to-edge and let each screen apply safe insets to its own content.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode()
            isAppearanceLightNavigationBars = !isDarkMode()
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }
    private fun accentColor(): Int = when (appearancePreferences().getString("accent", "violet")) {
        "blue" -> Color.rgb(65, 145, 245)
        "green" -> Color.rgb(35, 170, 125)
        "gold" -> Color.rgb(205, 157, 70)
        "rose" -> Color.rgb(215, 90, 135)
        else -> Color.rgb(139, 92, 246)
    }
    private val bg: Int get() = if (isDarkMode()) Color.rgb(9, 10, 22) else Color.rgb(245, 247, 251)
    private val panel: Int get() = if (isDarkMode()) Color.rgb(20, 22, 40) else Color.rgb(229, 234, 243)
    private val red: Int get() = accentColor()
    private val white: Int get() = if (isDarkMode()) Color.WHITE else Color.rgb(24, 29, 42)
    private fun accentForeground(): Int =
        if (appearancePreferences().getString("accent", "violet") == "violet") Color.WHITE else Color.rgb(24, 29, 42)
    private val gray: Int get() = if (isDarkMode()) Color.LTGRAY else Color.rgb(86, 96, 115)
    private val BACKUP_EXPORT_REQUEST = 1003
    private val BACKUP_IMPORT_REQUEST = 1004
    private val platforms = listOf("الكل", "BLOG", "FAVORITES", "PSP", "PS1", "PS2", "PS3", "PS4")
    private val games = mutableListOf<Game>()
    private val imageExecutor = Executors.newFixedThreadPool(3)
    private val coverBitmapCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(1024)) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private lateinit var gameList: LinearLayout
    private lateinit var search: EditText
    private lateinit var filters: LinearLayout
    private lateinit var countText: TextView

    private var selected = "ALL"
    private var sortMode = "الأحدث"
    private var selectedGenre = ""
    private var selectedYear = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchRenderRunnable: Runnable? = null
    private val PICK_IMAGE_REQUEST = 1001
    private var pendingCoverLocalPath = ""
    private var pendingCoverPublicUrl = ""
    private var coverUploadInProgress = false
    private var coverUploadFailed = false
    private var coverPreview: ImageView? = null
    private var coverUrlField: EditText? = null
    private var isSyncing = false
    private var isAdminSession = false
    private var bloggerBlogId = ""
    private var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient? = null
    // App owner plus the two Blogger administrators approved by the owner.
    // Blogger itself remains the authority: its API validates each signed-in account.
    private val adminEmails = setOf(
        "xpangwan@gmail.com",
        "an9910106@gmail.com",
        "slmankabos@gmail.com"
    )
    private fun isApprovedAdmin(email: String?): Boolean =
        email?.trim()?.lowercase(Locale.ROOT) in adminEmails
    private val SIGN_IN_REQUEST = 1002

    private val bloggerFeedUrl =
        "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applySystemBarAppearance()

        createNotificationChannel()
        configureGoogleSignIn()
        loadGames()
        restoreAdminSession()
        createInterface()
        showStartupExperience()
        if (notificationPreferences().getBoolean("enabled", false)) {
            scheduleBlogNotifications()
            requestNotificationPermissionIfNeeded()
        }
        syncFromBlog(showResult = false)
    }


    override fun onDestroy() {
        searchRenderRunnable?.let { mainHandler.removeCallbacks(it) }
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun configureGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestScopes(Scope("https://www.googleapis.com/auth/blogger"), Scope("https://www.googleapis.com/auth/drive.file")).build()
        googleSignInClient = GoogleSignIn.getClient(this, options)
    }
    private fun restoreAdminSession() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        isAdminSession = isApprovedAdmin(account?.email)
    }
    private fun startGoogleSignIn() { googleSignInClient?.let { startActivityForResult(it.signInIntent, SIGN_IN_REQUEST) } }
    private fun signOutAdmin() {
        googleSignInClient?.signOut()?.addOnCompleteListener { isAdminSession = false; createInterface() }
    }
    private fun adminToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب مسؤول المدونة")
        if (!isApprovedAdmin(account.email)) throw SecurityException("حساب Google غير مخوّل")
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
    private fun driveToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(this)
            ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب المسؤول")
        if (!isApprovedAdmin(account.email)) {
            throw SecurityException("حساب Google غير مخوّل لرفع الأغلفة")
        }
        val androidAccount = account.account
            ?: throw IllegalStateException("بيانات حساب Google غير متاحة")
        return GoogleAuthUtil.getToken(
            applicationContext,
            androidAccount,
            "oauth2:https://www.googleapis.com/auth/drive.file"
        )
    }

    private fun uploadCoverToDrive(file: File): String {
        if (!file.exists() || file.length() == 0L) throw IllegalStateException("ملف الصورة فارغ أو غير موجود")
        if (file.length() > 10L * 1024 * 1024) throw IllegalStateException("حجم صورة الغلاف يتجاوز 10 ميغابايت")
        // Detect the real image format from its contents. Picked images are stored
        // with a neutral .img suffix, so relying on the filename mislabels PNG/WebP as JPEG.
        val imageBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, imageBounds)
        val detectedMime = imageBounds.outMimeType?.lowercase(Locale.ROOT).orEmpty()
        val mime = when (detectedMime) {
            "image/png", "image/webp", "image/gif", "image/jpeg" -> detectedMime
            else -> when (file.extension.lowercase(Locale.ROOT)) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
        }
        val token = driveToken()
        val boundary = "MarcoRX6" + System.currentTimeMillis().toString()
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val metadata = JSONObject().put("name", "MARCO_RX6_" + file.nameWithoutExtension + "." + extension)
        val output = java.io.ByteArrayOutputStream()
        output.write(("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray(Charsets.UTF_8))
        output.write(metadata.toString().toByteArray(Charsets.UTF_8))
        output.write(("\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n").toByteArray(Charsets.UTF_8))
        file.inputStream().use { it.copyTo(output) }
        output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        val connection = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.outputStream.use { it.write(output.toByteArray()) }
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("فشل رفع الصورة إلى Google Drive ($code): $response")

        val id = JSONObject(response).optString("id")
        if (id.isBlank()) throw IllegalStateException("لم يرجع Google Drive معرّف الصورة")
        val permission = URL("https://www.googleapis.com/drive/v3/files/$id/permissions").openConnection() as HttpURLConnection
        permission.requestMethod = "POST"
        permission.connectTimeout = 20000
        permission.readTimeout = 20000
        permission.doOutput = true
        permission.setRequestProperty("Authorization", "Bearer $token")
        permission.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        permission.outputStream.use {
            it.write(JSONObject().put("type", "anyone").put("role", "reader").toString().toByteArray(Charsets.UTF_8))
        }
        val permissionCode = permission.responseCode
        val permissionBody = (if (permissionCode in 200..299) permission.inputStream else permission.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        permission.disconnect()
        if (permissionCode !in 200..299) throw IllegalStateException("تم رفع الصورة لكن تعذر جعلها متاحة للموقع ($permissionCode): $permissionBody")
        val directImageUrl = "https://lh3.googleusercontent.com/d/$id"
        verifyPublicImageUrl(directImageUrl)
        return directImageUrl
    }

    private fun verifyPublicImageUrl(imageUrl: String) {
        var lastFailure = "رابط الصورة لا يعيد ملف صورة مباشراً"
        repeat(3) { attempt ->
            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Range", "bytes=0-31")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                val code = connection.responseCode
                val mime = connection.contentType.orEmpty().substringBefore(";").lowercase(Locale.ROOT)
                if (code in 200..299 && mime.startsWith("image/")) return
                lastFailure = "تعذر التحقق من رابط الصورة (HTTP $code، النوع: ${mime.ifBlank { "غير معروف" }})"
            } catch (e: Exception) {
                lastFailure = e.message ?: lastFailure
            } finally {
                connection.disconnect()
            }
            if (attempt < 2) Thread.sleep(900L * (attempt + 1))
        }
        throw IllegalStateException(lastFailure)
    }

    private fun fetchBlogLabels(): List<String> {
        if (bloggerBlogId.isBlank()) throw IllegalStateException("زامن المدونة أولاً ثم حدّث التسميات.")
        val labels = linkedSetOf<String>()
        var pageToken = ""
        var pages = 0
        do {
            val tokenPart = if (pageToken.isBlank()) "" else "&pageToken=" + Uri.encode(pageToken)
            val response = bloggerRequest("GET", "blogs/$bloggerBlogId/posts?fetchBodies=false&maxResults=500$tokenPart")
            val posts = response.optJSONArray("items") ?: JSONArray()
            for (i in 0 until posts.length()) {
                val postLabels = posts.optJSONObject(i)?.optJSONArray("labels") ?: continue
                for (j in 0 until postLabels.length()) postLabels.optString(j).trim().takeIf { it.isNotBlank() }?.let { labels.add(it) }
            }
            pageToken = response.optString("nextPageToken", "")
            pages++
        } while (pageToken.isNotBlank() && pages < 10)
        return labels.toList().sortedBy { it.lowercase(Locale.ROOT) }
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
        val enabled = notificationPreferences().getBoolean("enabled", false)
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
            .show().also { styleAlertDialog(it) }
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


    private fun stylePrimaryButton(button: Button, primary: Boolean = false) {
        button.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            if (primary) intArrayOf(red, Color.rgb(104, 75, 190))
            else if (isDarkMode()) intArrayOf(Color.rgb(45, 51, 72), Color.rgb(29, 34, 51))
            else intArrayOf(Color.rgb(250, 251, 255), Color.rgb(232, 237, 247))).apply {
            cornerRadius = dp(14).toFloat()
            if (!primary) setStroke(dp(1), if (isDarkMode()) Color.rgb(72, 81, 108) else Color.rgb(211, 219, 232))
        }
        button.setTextColor(if (primary) accentForeground() else white)
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.textSize = 13.5f
        button.isAllCaps = false
        button.letterSpacing = 0.01f
        button.minHeight = dp(48)
        button.minWidth = dp(48)
        button.setPadding(dp(16), dp(10), dp(16), dp(10))
        button.stateListAnimator = null
    }

    private fun applyUnifiedSkin(view: View) {
        when (view) {
            is EditText -> {
                view.background = GradientDrawable().apply {
                    setColor(if (isDarkMode()) Color.rgb(25, 30, 47) else Color.rgb(248, 250, 254))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.rgb(67, 76, 104) else Color.rgb(211, 219, 232))
                }
                view.setPadding(dp(14), dp(12), dp(14), dp(12))
                view.minHeight = dp(52)
                view.textSize = 14f
                view.setTextColor(white)
                view.setHintTextColor(gray)
            }
            // CompoundButton subclasses Button, so handle them first to avoid
            // applying the generic button background to checkboxes and radio buttons.
            is CheckBox -> {
                view.setTextColor(white)
                view.buttonTintList = android.content.res.ColorStateList.valueOf(red)
                view.minHeight = dp(48)
            }
            is RadioButton -> {
                view.setTextColor(white)
                view.buttonTintList = android.content.res.ColorStateList.valueOf(red)
                view.minHeight = dp(48)
            }
            is Button -> stylePrimaryButton(view, view.tag == "marco_primary_action")
            is SeekBar -> {
                view.progressTintList = android.content.res.ColorStateList.valueOf(red)
                view.thumbTintList = android.content.res.ColorStateList.valueOf(red)
            }
            is Spinner -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(gray)
                view.minimumHeight = dp(48)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyUnifiedSkin(view.getChildAt(i))
        }
    }

    private fun styleAlertDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (isDarkMode()) intArrayOf(Color.rgb(28, 31, 54), Color.rgb(14, 19, 34))
            else intArrayOf(Color.WHITE, Color.rgb(239, 243, 251))).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), if (isDarkMode()) Color.rgb(82, 75, 130) else Color.rgb(211, 219, 234))
        })
        dialog.window?.let { window ->
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.66f }
        }
        dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.apply {
            setTextColor(white)
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        dialog.findViewById<View>(android.R.id.message)?.let {
            (it as? TextView)?.apply {
                setTextColor(gray)
                textSize = 14f
                setLineSpacing(dp(3).toFloat(), 1f)
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { stylePrimaryButton(it, true) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { stylePrimaryButton(it) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { stylePrimaryButton(it) }
        dialog.listView?.let { list ->
            list.setBackgroundColor(if (isDarkMode()) Color.rgb(20, 22, 40) else Color.WHITE)
            list.divider = android.graphics.drawable.ColorDrawable(if (isDarkMode()) Color.rgb(48, 55, 78) else Color.rgb(226, 231, 241))
            list.dividerHeight = dp(1)
            list.post {
                for (index in 0 until list.childCount) {
                    val row = list.getChildAt(index)
                    if (row is ViewGroup) {
                        for (childIndex in 0 until row.childCount) {
                            (row.getChildAt(childIndex) as? TextView)?.setTextColor(white)
                        }
                    } else {
                        (row as? TextView)?.setTextColor(white)
                    }
                }
            }
        }
        dialog.window?.decorView?.let { applyUnifiedSkin(it) }
    }

    private fun themedSpinner(items: List<String>, selectedIndex: Int = 0): Spinner {
        val spinner = Spinner(this)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(white)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(white)
                    setBackgroundColor(panel)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        spinner.backgroundTintList = android.content.res.ColorStateList.valueOf(gray)
        return spinner
    }

    private fun makeInput(hintText: String): EditText =
        EditText(this).apply {
            hint = hintText
            textSize = 14f
            minHeight = dp(52)
            setTextColor(white)
            setHintTextColor(gray)
            background = rounded(panel, 12)
            setPadding(dp(13), dp(10), dp(13), dp(10))
        }


    private fun currentLanguage(): String =
        getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", "ar") ?: "ar"

    private fun tr(ar: String): String {
        val lang = currentLanguage()
        if (lang == "ar") return ar
        val translations = mapOf(
            "حسابي" to listOf("My profile", "Mon profil", "Mi perfil"),
            "دخول المسؤول  ↗" to listOf("Admin login  ↗", "Connexion admin  ↗", "Acceso admin  ↗"),
            "● مسؤول" to listOf("● Admin", "● Admin", "● Admin"),
            "عالمك للألعاب" to listOf("Your gaming world", "Votre univers gaming", "Tu mundo gamer"),
            "اكتشف. العب.\nاستمتع أكثر." to listOf("Discover. Play.\nEnjoy more.", "Découvrez. Jouez.\nProfitez plus.", "Descubre. Juega.\nDisfruta más."),
            "ألعاب وشروحات وآخر الإصدارات، كلّها في مكان واحد." to listOf("Games, guides and latest releases in one place.", "Jeux, guides et dernières sorties au même endroit.", "Juegos, guías y últimos lanzamientos en un solo lugar."),
            "ابحث عن الألعاب والمقالات..." to listOf("Search games and articles...", "Rechercher jeux et articles...", "Buscar juegos y artículos..."),
            "الكل" to listOf("All", "Tout", "Todo"),
            "المدونة" to listOf("Blog", "Blog", "Blog"),
            "المفضلة" to listOf("Favorites", "Favoris", "Favoritos"),
            "الأخيرة" to listOf("Recent", "Récents", "Recientes"),
            "الأكثر مشاهدة" to listOf("Most viewed", "Les plus vus", "Más vistos"),
            "شوهدت مؤخراً" to listOf("Recently viewed", "Vus récemment", "Vistos recientemente"),
            "استكشف" to listOf("Explore", "Explorer", "Explorar"),
            "أحدث المقالات" to listOf("Latest articles", "Derniers articles", "Últimos artículos"),
            "ترتيب المقالات" to listOf("Sort articles", "Trier les articles", "Ordenar artículos"),
            "مقال" to listOf("articles", "articles", "artículos"),
            "لا توجد مقالات حالياً" to listOf("No articles yet", "Aucun article pour le moment", "Aún no hay artículos"),
            "⟳  مزامنة" to listOf("⟳  Sync", "⟳  Sync", "⟳  Sincronizar"),
            "♧  التنبيهات" to listOf("♧  Notifications", "♧  Notifications", "♧  Avisos"),
            "＋  نشر" to listOf("＋  Publish", "＋  Publier", "＋  Publicar"),
            "إشعارات المقالات الجديدة" to listOf("New article notifications", "Notifications des nouveaux articles", "Avisos de nuevos artículos"),
            "إغلاق" to listOf("Close", "Fermer", "Cerrar"),
            "إعدادات اللغة" to listOf("Language settings", "Paramètres de langue", "Idioma"),
            "اختر لغة التطبيق" to listOf("Choose app language", "Choisir la langue", "Elegir idioma"),
            "العربية" to listOf("Arabic", "Arabe", "Árabe"),
            "الإنجليزية" to listOf("English", "Anglais", "Inglés"),
            "الفرنسية" to listOf("French", "Français", "Francés"),
            "الإسبانية" to listOf("Spanish", "Espagnol", "Español"),
            "تم حفظ اللغة" to listOf("Language saved", "Langue enregistrée", "Idioma guardado"),
            "اسم العرض" to listOf("Display name", "Nom affiché", "Nombre para mostrar"),
            "ملفي الشخصي" to listOf("My profile", "Mon profil", "Mi perfil"),
            "ملف اللاعب على هذا الجهاز" to listOf("Player profile on this device", "Profil du joueur sur cet appareil", "Perfil del jugador en este dispositivo"),
            "حفظ" to listOf("Save", "Enregistrer", "Guardar"),
            "نشاطي" to listOf("My activity", "Mon activité", "Mi actividad"),
            "مشاركة" to listOf("Share", "Partager", "Compartir"),
            "التعليقات" to listOf("Comments", "Commentaires", "Comentarios"),
            "قيّم هذا المقال" to listOf("Rate this article", "Évaluer cet article", "Valorar este artículo"),
            "تحميل اللعبة" to listOf("Download game", "Télécharger le jeu", "Descargar juego"),
            "المدونة محدّثة" to listOf("Blog updated", "Blog à jour", "Blog actualizado"),
            "مقال" to listOf("article(s)", "article(s)", "artículo(s)"),
            "ترتيب المقالات" to listOf("Sort articles", "Trier les articles", "Ordenar artículos"),
            "الأحدث" to listOf("Latest", "Plus récents", "Más recientes"),
            "أبجدياً" to listOf("A–Z", "A–Z", "A–Z"),
            "المفضلة:" to listOf("Favorites:", "Favoris :", "Favoritos:"),
            "شوهدت مؤخراً:" to listOf("Recently viewed:", "Vus récemment :", "Vistos recientemente:")
        )
        val index = when (lang) { "en" -> 0; "fr" -> 1; "es" -> 2; else -> return ar }
        return translations[ar]?.get(index) ?: ar
    }

    private fun showLanguagePicker() {
        val codes = arrayOf("ar", "en", "fr", "es")
        val labels = arrayOf(tr("العربية"), tr("الإنجليزية"), tr("الفرنسية"), tr("الإسبانية"))
        val current = codes.indexOf(currentLanguage()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(tr("اختر لغة التطبيق"))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                    .putString("language", codes[which]).apply()
                dialog.dismiss()
                createInterface()
                Toast.makeText(this, tr("تم حفظ اللغة"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(tr("إغلاق"), null)
            .show().also { styleAlertDialog(it) }
    }

    private fun showSideMenu() {
        val gold = red
        val ink = bg
        val surface = if (isDarkMode()) panel else Color.rgb(255, 255, 255)
        val drawer = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(24), dp(20), dp(18))
            setBackground(GradientDrawable().apply {
                setColor(ink)
                cornerRadii = floatArrayOf(0f, 0f, dp(24).toFloat(), dp(24).toFloat(),
                    dp(24).toFloat(), dp(24).toFloat(), 0f, 0f)
                setStroke(dp(1), Color.rgb(99, 113, 141))
            })
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText("MARCO", 10f, gold, true))
        brand.addView(makeText("RX6", 27f, white, true))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = "×"; textSize = 30f; setTextColor(if (isDarkMode()) Color.LTGRAY else Color.rgb(67, 76, 94)); gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0); setOnClickListener { drawer.dismiss() }
        })
        panel.addView(top)
        panel.addView(makeText("القائمة الرئيسية", 10f, gray, true).apply {
            setPadding(0, dp(24), 0, dp(12))
        })
        fun menuItem(icon: String, label: String, detail: String = "", action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(surface, 15)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                isClickable = true
                isFocusable = true
                setOnClickListener { drawer.dismiss(); action() }
            }
            val iconView = TextView(this).apply {
                text = icon
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(if (isDarkMode()) Color.WHITE else Color.rgb(68, 76, 96))
                background = rounded(if (isDarkMode()) Color.rgb(43, 49, 68) else Color.rgb(239, 242, 248), 12)
                setPadding(dp(11), dp(9), dp(11), dp(9))
                minWidth = dp(42)
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(makeText(label, 14f, white, true))
            if (detail.isNotBlank()) labels.addView(makeText(detail, 10f, gray).apply { setPadding(0, dp(4), 0, 0) })
            row.addView(iconView, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12) })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        // الأكثر استخداماً أولاً، ثم الحساب والإدارة، وبعدها التخصيص والنسخ الاحتياطي والقنوات
        menuItem("⟳", tr("⟳  مزامنة"), if (isSyncing) "المزامنة قيد التنفيذ" else "تحديث المقالات من المدونة") {
            syncFromBlog(showResult = true)
        }
        menuItem("⬇", "مدير التنزيلات", "متابعة التنزيلات وحالاتها") { showDownloadManager() }
        menuItem("♧", tr("♧  التنبيهات"), "التحكم في إشعارات المقالات الجديدة") { showNotificationSettings() }
        menuItem("♙", tr("حسابي"), tr("ملفي الشخصي")) { showProfile() }
        menuItem("♛", if (isAdminSession) tr("● مسؤول") else tr("دخول المسؤول  ↗"),
            if (isAdminSession) "تسجيل الخروج من حساب المسؤول" else "إدارة ونشر مقالات المدونة") {
            if (isAdminSession) signOutAdmin() else startGoogleSignIn()
        }
        if (isAdminSession) {
            menuItem("＋", tr("＋  نشر"), "إضافة مقال جديد إلى المدونة") { showGameDialog() }
        }
        menuItem("✧", "لعبة اليوم", "اكتشف اختيار MARCO RX6 اليومي") { showGameOfTheDay() }
        menuItem("文", tr("إعدادات اللغة"), tr("اختر لغة التطبيق")) { showLanguagePicker() }
        menuItem("✦", "ألوان وحركات الواجهة", "تخصيص اللون الرئيسي وتأثيرات الحركة") { showAppearanceSettings() }
        menuItem("Aa", "الخط وحجم النص", "تخصيص قراءة وواجهة التطبيق") { showTypographySettings() }
        menuItem("⇧", "نسخ احتياطي", "تصدير مكتبتك وإعداداتك إلى ملف") { exportBackup() }
        menuItem("⇩", "استعادة نسخة", "استيراد نسخة احتياطية محفوظة") { importBackup() }
        menuItem("▶", "قناة YouTube", "@marco_rx7") { openGameLink("https://youtube.com/@marco_rx7") }
        menuItem("➤", "قناة Telegram", "@MARCO_RX1") { openGameLink("https://t.me/MARCO_RX1") }
        panel.addView(View(this), LinearLayout.LayoutParams(1, dp(12)))
        panel.addView(makeText("MARCO RX6  •  PREMIUM", 9f, gray, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        })
        val menuScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(panel, ViewGroup.LayoutParams(-1, -2))
        }
        ViewCompat.setOnApplyWindowInsetsListener(menuScroll) { view, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safeAreas.top, 0, safeAreas.bottom)
            insets
        }
        applyMotionPreferences(menuScroll)
        applyUnifiedSkin(menuScroll)
        drawer.setContentView(menuScroll)
        val drawerWidth = minOf(dp(360), resources.displayMetrics.widthPixels - dp(24))
        drawer.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val lp = w.attributes
            lp.width = drawerWidth
            lp.height = -1
            lp.gravity = if (currentLanguage() == "ar") Gravity.RIGHT else Gravity.LEFT
            lp.dimAmount = 0.62f
            w.attributes = lp
        }
        drawer.show()
        drawer.window?.setLayout(drawerWidth, -1)
        menuScroll.requestApplyInsets()
    }

    private fun fontPreferences() = getSharedPreferences("font_settings", MODE_PRIVATE)
    private fun fontScale(): Float = fontPreferences().getFloat("scale", 1.0f).coerceIn(0.85f, 1.25f)
    private fun fontFamilyName(): String = when (fontPreferences().getString("family", "sans")) {
        "serif" -> "serif"
        "mono" -> "monospace"
        else -> "sans-serif"
    }
    private fun applyTypography(view: View) {
        if (view is TextView) {
            val style = view.typeface?.style ?: Typeface.NORMAL
            view.setTypeface(Typeface.create(fontFamilyName(), style))
            view.textSize = view.textSize / resources.displayMetrics.scaledDensity * fontScale()
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) applyTypography(view.getChildAt(i))
    }
    private fun applyMotionPreferences(view: View) {
        val animationsEnabled = appearancePreferences().getBoolean("animations", true)
        if (view is ViewGroup) {
            view.layoutTransition = if (animationsEnabled) LayoutTransition().apply { setDuration(150) } else null
            for (i in 0 until view.childCount) applyMotionPreferences(view.getChildAt(i))
        }
        if (view.isClickable && view.tag != "marco_press_motion") {
            view.tag = "marco_press_motion"
            view.setOnTouchListener { target, event ->
                if (!appearancePreferences().getBoolean("animations", true)) return@setOnTouchListener false
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        target.animate().cancel()
                        target.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f).setDuration(90L).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> target.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f).setDuration(170L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.35f)).start()
                }
                false
            }
        }
    }

    private fun showAppearanceSettings() {
        val choices = arrayOf("بنفسجي", "أزرق", "أخضر", "ذهبي", "وردي")
        val keys = arrayOf("violet", "blue", "green", "gold", "rose")
        val selectedIndex = keys.indexOf(appearancePreferences().getString("accent", "violet")).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("تخصيص المظهر")
            .setSingleChoiceItems(choices, selectedIndex) { dialog, which ->
                appearancePreferences().edit().putString("accent", keys[which]).apply()
                dialog.dismiss()
                recreate()
            }
            .setNeutralButton(if (appearancePreferences().getBoolean("animations", true)) "إيقاف الحركات" else "تفعيل الحركات") { _, _ ->
                val enabled = !appearancePreferences().getBoolean("animations", true)
                appearancePreferences().edit().putBoolean("animations", enabled).apply()
                applyMotionPreferences(window.decorView)
                Toast.makeText(this, if (enabled) "تم تفعيل انتقالات الواجهة" else "تم إيقاف انتقالات الواجهة", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إغلاق", null).show().also { styleAlertDialog(it) }
    }

    private fun exportBackup() {
        val payload = JSONObject()
            .put("format", "MARCO_RX6_BACKUP")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("games", JSONArray().also { array ->
                games.forEach { game ->
                    array.put(JSONObject()
                        .put("name", game.name).put("platform", game.platform)
                        .put("description", game.description).put("url", game.url)
                        .put("coverUrl", game.coverUrl).put("coverLocalPath", "")
                        .put("sourcePostUrl", game.sourcePostUrl).put("downloadLinks", game.downloadLinks)
                        .put("blogImported", game.blogImported)
                        .put("platformManuallyCorrected", game.platformManuallyCorrected)
                        .put("isFavorite", game.isFavorite).put("bloggerPostId", game.bloggerPostId)
                        .put("bloggerHtml", game.bloggerHtml).put("playStatus", game.playStatus)
                        .put("genre", game.genre).put("releaseYear", game.releaseYear))
                }
            })
            .put("appearance", JSONObject()
                .put("darkMode", isDarkMode())
                .put("accent", appearancePreferences().getString("accent", "violet"))
                .put("animations", appearancePreferences().getBoolean("animations", true)))
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "MARCO-RX6-backup.json")
        }
        backupExportPayload = payload.toString(2)
        startActivityForResult(intent, BACKUP_EXPORT_REQUEST)
    }
    private var backupExportPayload: String = ""

    private fun importBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
        }
        startActivityForResult(intent, BACKUP_IMPORT_REQUEST)
    }

    private fun handleBackupResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (resultCode != RESULT_OK) return requestCode == BACKUP_EXPORT_REQUEST || requestCode == BACKUP_IMPORT_REQUEST
        val uri = data?.data ?: return true
        return try {
            if (requestCode == BACKUP_EXPORT_REQUEST) {
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(backupExportPayload) }
                    ?: throw IllegalStateException("تعذر إنشاء ملف النسخة الاحتياطية")
                Toast.makeText(this, "تم تصدير النسخة الاحتياطية", Toast.LENGTH_LONG).show()
            } else if (requestCode == BACKUP_IMPORT_REQUEST) {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("تعذر قراءة الملف")
                val backup = JSONObject(raw)
                require(backup.optString("format") == "MARCO_RX6_BACKUP") { "ملف النسخة الاحتياطية غير صالح" }
                require(backup.optInt("version", 0) == 1) { "إصدار النسخة الاحتياطية غير مدعوم" }
                val array = backup.optJSONArray("games") ?: JSONArray()
                val restored = mutableListOf<Game>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    if (name.isBlank()) continue
                    restored.add(Game(name, item.optString("platform", "BLOG"), item.optString("description"),
                        item.optString("url"), item.optString("coverUrl"), "", item.optString("sourcePostUrl"),
                        item.optString("downloadLinks"), item.optBoolean("blogImported"),
                        item.optBoolean("platformManuallyCorrected"), item.optBoolean("isFavorite"),
                        item.optString("bloggerPostId"), item.optString("bloggerHtml"), item.optString("playStatus", ""),
                        item.optString("genre", ""), item.optString("releaseYear", "")))
                }
                AlertDialog.Builder(this).setTitle("استعادة النسخة الاحتياطية")
                    .setMessage("سيتم استبدال المكتبة الحالية بـ ${restored.size} عنصر. يُفضّل تصدير نسخة من بياناتك الحالية أولاً. هل تريد المتابعة؟")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("استعادة") { _, _ ->
                        games.clear(); games.addAll(restored); saveGames()
                        backup.optJSONObject("appearance")?.let { a ->
                            appearancePreferences().edit()
                                .putBoolean("dark_mode", a.optBoolean("darkMode", true))
                                .putString("accent", a.optString("accent", "violet"))
                                .putBoolean("animations", a.optBoolean("animations", true)).apply()
                        }
                        recreate()
                        Toast.makeText(this, "تمت استعادة المكتبة", Toast.LENGTH_LONG).show()
                    }.show().also { styleAlertDialog(it) }
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "تعذرت معالجة النسخة: ${e.message}", Toast.LENGTH_LONG).show()
            true
        }
    }

    private fun showTypographySettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(12), dp(22), dp(8)) }
        val preview = makeText("MARCO RX6  •  معاينة شكل النص\nقراءة أوضح وتجربة مريحة", 17f, white, true)
        preview.setPadding(dp(14), dp(14), dp(14), dp(14)); preview.background = rounded(panel, 13)
        box.addView(preview, LinearLayout.LayoutParams(-1, -2))
        box.addView(makeText("حجم الخط", 13f, red, true).apply { setPadding(0, dp(18), 0, dp(5)) })
        val size = SeekBar(this).apply { max = 8; progress = ((fontScale() - 0.85f) / 0.05f).toInt().coerceIn(0, 8) }
        box.addView(size)
        val sizeLabel = makeText("حجم النص: " + (fontScale() * 100).toInt() + "%", 11f, gray)
        box.addView(sizeLabel)
        box.addView(makeText("نوع الخط", 13f, red, true).apply { setPadding(0, dp(16), 0, dp(5)) })
        val family = themedSpinner(listOf("واضح (افتراضي)", "كلاسيكي", "أحادي المسافة"), when (fontPreferences().getString("family", "sans")) { "serif" -> 1; "mono" -> 2; else -> 0 })
        box.addView(family)
        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.85f + progress * 0.05f
                sizeLabel.text = "حجم النص: " + (scale * 100).toInt() + "%"
                preview.textSize = 17f * scale
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        AlertDialog.Builder(this).setTitle("تخصيص الخط").setView(box)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                val familyValue = when (family.selectedItemPosition) { 1 -> "serif"; 2 -> "mono"; else -> "sans" }
                fontPreferences().edit().putFloat("scale", 0.85f + size.progress * 0.05f).putString("family", familyValue).apply()
                createInterface()
                Toast.makeText(this, "تم حفظ إعدادات الخط", Toast.LENGTH_SHORT).show()
            }.show().also { styleAlertDialog(it) }
    }

    private fun showAdvancedFilters() {
        val genreValues = listOf("كل الأنواع") + games.map { it.genre.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        val yearValues = listOf("كل السنوات") + games.map { it.releaseYear.trim() }.filter { it.isNotBlank() }.distinct().sortedDescending()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        box.addView(makeText("نوع اللعبة", 12f, red, true))
        val genreSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, genreValues)
            setSelection(genreValues.indexOf(selectedGenre.takeIf { it.isNotBlank() } ?: "كل الأنواع").coerceAtLeast(0))
        }
        box.addView(genreSpinner)
        box.addView(makeText("سنة الإصدار", 12f, red, true).apply { setPadding(0, dp(12), 0, 0) })
        val yearSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, yearValues)
            setSelection(yearValues.indexOf(selectedYear.takeIf { it.isNotBlank() } ?: "كل السنوات").coerceAtLeast(0))
        }
        box.addView(yearSpinner)
        AlertDialog.Builder(this)
            .setTitle("الفلاتر المتقدمة")
            .setView(box)
            .setPositiveButton("تطبيق") { _, _ ->
                selectedGenre = genreSpinner.selectedItem?.toString()?.takeUnless { it == "كل الأنواع" }.orEmpty()
                selectedYear = yearSpinner.selectedItem?.toString()?.takeUnless { it == "كل السنوات" }.orEmpty()
                renderGames()
            }
            .setNeutralButton("مسح الفلاتر") { _, _ ->
                selectedGenre = ""
                selectedYear = ""
                renderGames()
            }
            .setNegativeButton("إغلاق", null)
            .show().also { styleAlertDialog(it) }
    }

    private fun createInterface() {
        // LIQUID METAL redesign: premium chrome surfaces, clear hierarchy and quick navigation.
        val silver = if (isDarkMode()) Color.rgb(226, 232, 242) else Color.rgb(43, 52, 68)
        val muted = gray
        val violet = red
        val ink = bg
        val glass = panel
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (currentLanguage() == "ar") View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            setPadding(dp(16), dp(10), dp(16), dp(104))
            setBackgroundColor(ink)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(16), dp(10) + safeAreas.top, dp(16), dp(104) + safeAreas.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(17))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText("MARCO RX6", 10f, muted, true))
        brand.addView(makeText("LIQUID  /  PLAY", 20f, silver, true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.06f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(silver)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(75, 83, 105), Color.rgb(25, 30, 45), Color.rgb(11, 14, 24))
                else intArrayOf(Color.WHITE, Color.rgb(235, 239, 247), Color.rgb(222, 229, 241))).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(93, 105, 130) else Color.rgb(190, 199, 215))
            }
            setPadding(dp(15), dp(10), dp(15), dp(10))
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = "القائمة الجانبية"
            setOnClickListener { showSideMenu() }
        }
        val themeToggle = TextView(this).apply {
            text = if (isDarkMode()) "☾" else "☀"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode()) Color.rgb(224, 231, 255) else Color.rgb(130, 83, 8))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(75, 83, 105), Color.rgb(25, 30, 45))
                else intArrayOf(Color.rgb(255, 246, 214), Color.rgb(255, 224, 155))).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(111, 126, 157) else Color.rgb(235, 181, 83))
            }
            setPadding(dp(13), dp(9), dp(13), dp(9))
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = if (isDarkMode()) "التبديل إلى الوضع النهاري" else "التبديل إلى الوضع الليلي"
            setOnClickListener {
                val nextDarkMode = !isDarkMode()
                isEnabled = false
                animate().rotationBy(180f).scaleX(0.2f).scaleY(0.2f).setDuration(220L).withEndAction {
                    text = if (nextDarkMode) "☾" else "☀"
                    setTextColor(if (nextDarkMode) Color.rgb(224, 231, 255) else Color.rgb(130, 83, 8))
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        if (nextDarkMode) intArrayOf(Color.rgb(75, 83, 105), Color.rgb(25, 30, 45))
                        else intArrayOf(Color.rgb(255, 246, 214), Color.rgb(255, 224, 155))).apply {
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), if (nextDarkMode) Color.rgb(111, 126, 157) else Color.rgb(235, 181, 83))
                    }
                    appearancePreferences().edit().putBoolean("dark_mode", nextDarkMode).apply()
                    animate().rotationBy(180f).scaleX(1f).scaleY(1f).setDuration(220L).withEndAction {
                        recreate()
                    }.start()
                }.start()
            }
        }
        header.addView(themeToggle, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(9) })
        header.addView(menu)
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(105, 115, 139), Color.rgb(44, 52, 73), Color.rgb(17, 22, 36), Color.rgb(49, 35, 83))
                else intArrayOf(Color.rgb(250, 251, 255), Color.rgb(226, 234, 247), Color.rgb(246, 241, 255), Color.rgb(226, 215, 247))).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(130, 143, 170) else Color.rgb(157, 166, 190))
            }
            elevation = dp(5).toFloat()
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        hero.addView(makeText("✧  PREMIUM GAME LIBRARY", 9f, if (isDarkMode()) silver else Color.rgb(62, 72, 94), true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        hero.addView(makeText(tr("اكتشف. العب.\nاستمتع أكثر."), 27f, white, true).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, dp(10))
        })
        hero.addView(makeText(tr("ألعاب وشروحات وآخر الإصدارات، كلّها في مكان واحد."), 11f,
            if (isDarkMode()) Color.rgb(218, 222, 235) else Color.rgb(75, 85, 105)).apply {
            gravity = Gravity.CENTER
        })
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        stats.addView(makeText("◈  ${games.size} " + tr("مقال"), 11f, if (isDarkMode()) silver else Color.rgb(49, 59, 80), true).apply {
            gravity = Gravity.CENTER
        })
        stats.addView(makeText("•  " + tr("المدونة محدّثة"), 10f,
            if (isDarkMode()) Color.rgb(197, 184, 255) else Color.rgb(112, 76, 174), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        hero.addView(stats)
        root.addView(hero, LinearLayout.LayoutParams(-1, dp(212)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(18)
        })

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(39, 46, 63), Color.rgb(17, 21, 33))
                else intArrayOf(Color.rgb(255, 255, 255), Color.rgb(232, 237, 247))).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(75, 87, 112) else Color.rgb(184, 193, 211))
            }
            setPadding(dp(14), 0, dp(14), 0)
            elevation = if (isDarkMode()) 0f else dp(2).toFloat()
        }
        searchWrap.addView(makeText("⌕", 26f, if (isDarkMode()) silver else Color.rgb(73, 84, 105), true))
        search = AutoCompleteTextView(this).apply {
            hint = tr("ابحث عن الألعاب والمقالات...")
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setSingleLine(true)
            threshold = 1
            setTextColor(white)
            setHintTextColor(if (isDarkMode()) gray else Color.rgb(105, 115, 134))
            background = null
            setPadding(dp(10), 0, dp(6), 0)
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line,
                games.map { it.name }.distinct().take(80)))
            setOnFocusChangeListener { _, focused ->
                if (focused && text.isNotEmpty()) showDropDown()
            }
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(searchWrap, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        (listOf("ALL", "BLOG", "FAVORITES") + platforms.drop(3) + listOf("WANT_TO_PLAY", "PLAYING", "COMPLETED")).forEach { category ->
            val chip = TextView(this).apply {
                text = when (category) {
                    "ALL" -> tr("الكل")
                    "BLOG" -> tr("المدونة")
                    "FAVORITES" -> tr("المفضلة")
                    "WANT_TO_PLAY" -> "أريد لعبها"
                    "PLAYING" -> "ألعبها حالياً"
                    "COMPLETED" -> "أنهيتها"
                    else -> category
                }
                tag = category
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                minHeight = dp(44)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = rounded(glass, 18)
                setTextColor(muted)
                setOnClickListener { selected = category; renderGames() }
            }
            filters.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
        }
        filterScroll.addView(filters)
        root.addView(filterScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(17) })

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(makeText("DISCOVER", 8f, violet, true).apply { letterSpacing = 0.14f })
        titles.addView(makeText(tr("أحدث المقالات"), 21f, silver, true))
        section.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        countText = makeText("0 " + tr("مقال"), 10f, muted, true)
        section.addView(countText)
        val sort = TextView(this).apply {
            text = "⇅"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(silver)
            background = rounded(glass, 13)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = tr("ترتيب المقالات")
            setOnClickListener {
                sortMode = when (sortMode) { "LATEST" -> "NEW"; "NEW" -> "A-Z"; "A-Z" -> "FAVORITES"; else -> "LATEST" }
                renderGames()
                Toast.makeText(this@MainActivity, "الترتيب: " + when (sortMode) {
                    "LATEST" -> "الأحدث"; "NEW" -> "المضاف حديثاً"; "A-Z" -> "أبجدياً"; else -> "المفضلة"
                }, Toast.LENGTH_SHORT).show()
            }
        }
        section.addView(sort, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(9) })
        val advancedFilter = TextView(this).apply {
            text = "⚙"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(silver)
            background = rounded(glass, 13)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = "الفلاتر المتقدمة"
            setOnClickListener { showAdvancedFilters() }
        }
        section.addView(advancedFilter, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7) })
        root.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        gameList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(18))
        }
        root.addView(gameList, LinearLayout.LayoutParams(-1, -2))

        if (isAdminSession) {
            val publish = TextView(this).apply {
                text = tr("＋  نشر")
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(118, 127, 151), Color.rgb(100, 77, 157))).apply {
                    cornerRadius = dp(15).toFloat()
                }
                setPadding(dp(18), dp(13), dp(18), dp(13))
                setOnClickListener { showGameDialog() }
            }
            root.addView(publish, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }

        applyTypography(root)
        applyUnifiedSkin(root)
        val pageScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
        val shell = FrameLayout(this).apply {
            setBackgroundColor(ink)
            addView(pageScroll, FrameLayout.LayoutParams(-1, -1))
        }
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = if (isDarkMode()) GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(74, 83, 105), Color.rgb(27, 33, 48), Color.rgb(13, 17, 28))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(100, 113, 140))
            } else GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 255, 255), Color.rgb(241, 244, 250))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(190, 199, 214))
            }
            elevation = dp(14).toFloat()
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        fun dockItem(icon: String, label: String, action: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = rounded(if (isDarkMode()) Color.rgb(45, 52, 71) else Color.rgb(232, 237, 246), 15)
                setOnClickListener { action() }
            }
            item.addView(makeText(icon, 15f, if (isDarkMode()) silver else Color.rgb(92, 76, 143), true).apply {
                gravity = Gravity.CENTER
            })
            item.addView(makeText(label, 10f, if (isDarkMode()) silver else Color.rgb(43, 51, 68), true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(2), dp(3), dp(2), dp(2))
            })
            dock.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        dockItem("⌂", tr("الرئيسية")) { selected = "ALL"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("♡", tr("المفضلة")) { selected = "FAVORITES"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("◷", "متابعتي") { selected = "WANT_TO_PLAY"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("♙", tr("حسابي")) { showProfile() }
        val dockParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(12)
        }
        shell.addView(dock, dockParams)
        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            )
            dockParams.bottomMargin = dp(12) + safeAreas.bottom
            dock.layoutParams = dockParams
            insets
        }
        setContentView(shell)
        applyMotionPreferences(shell)
        root.requestApplyInsets()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRenderRunnable?.let { mainHandler.removeCallbacks(it) }
                searchRenderRunnable = Runnable { renderGames() }.also {
                    mainHandler.postDelayed(it, 180L)
                }
            }
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
                    playStatus = old.playStatus,
                    genre = old.genre.ifBlank { incoming.genre },
                    releaseYear = old.releaseYear.ifBlank { incoming.releaseYear },
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
            .show().also { styleAlertDialog(it) }
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
            .show().also { styleAlertDialog(it) }
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
        val image = extractFirstImage(content).ifBlank { extractFeedThumbnail(entry) }
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
            bloggerHtml = entry.optString("offlineHtml").ifBlank { content }
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

    // Blogger serves the same uploaded image through size-specific URL paths
    // such as /s320/filename.jpg. Use a larger rendition for the app's cover cards.
    private fun normalizeBloggerImageUrl(rawUrl: String): String {
        val decoded = rawUrl.trim()
            .replace("&amp;", "&")
            .replace("\\/", "/")
            .replace("\\u003d", "=")
        if (decoded.isBlank()) return ""
        return try {
            val uri = Uri.parse(decoded)
            val host = uri.host.orEmpty().lowercase(Locale.ROOT)
            if (host == "blogger.googleusercontent.com" || host.endsWith(".bp.blogspot.com") ||
                host == "bp.blogspot.com" || host.endsWith(".blogspot.com")
            ) {
                // Blogger path-style transforms: /s320/, /s640/, /w320-h240/
                val path = uri.encodedPath.orEmpty()
                val resizedPath = path.replace(
                    Regex("/(?:s\\d+(?:-[a-z0-9]+)*|w\\d+-h\\d+(?:-[a-z0-9]+)*)/"),
                    "/s960/"
                )
                // Newer Google image URLs may put the transform after '='.
                val resized = if (resizedPath != path) resizedPath else
                    uri.toString().replace(Regex("=s\\d+(?:-[a-z0-9]+)*$"), "=s960")
                if (resizedPath != path) uri.buildUpon().encodedPath(resizedPath).build().toString() else resized
            } else decoded
        } catch (_: Exception) {
            decoded
        }
    }

    private fun normalizeBloggerImageTags(html: String): String {
        val imageTagPattern = Regex("(?is)<img\\b[^>]*>")
        val sourcePattern = Regex("(?i)(\\b(?:src|data-src)\\s*=\\s*)([\"'])([^\"']+)([\"'])")
        return imageTagPattern.replace(html) { tagMatch ->
            sourcePattern.replace(tagMatch.value) { sourceMatch ->
                val normalized = normalizeBloggerImageUrl(sourceMatch.groupValues[3])
                sourceMatch.groupValues[1] + sourceMatch.groupValues[2] +
                    normalized + sourceMatch.groupValues[4]
            }
        }
    }

    private fun extractFeedThumbnail(entry: JSONObject): String {
        val mediaThumbnail = entry.optJSONObject("media\$thumbnail")?.optString("url").orEmpty()
        if (mediaThumbnail.isNotBlank()) return normalizeBloggerImageUrl(mediaThumbnail)
        val mediaContent = entry.optJSONArray("media\$content")
        for (index in 0 until (mediaContent?.length() ?: 0)) {
            val url = mediaContent?.optJSONObject(index)?.optString("url").orEmpty()
            if (url.startsWith("https://") || url.startsWith("http://")) {
                return normalizeBloggerImageUrl(url)
            }
        }
        return ""
    }

    private fun extractFirstImage(html: String): String {
        // Blogger feed HTML can contain lazy-load attributes or escaped URL entities.
        val tagPattern = Pattern.compile("(?is)<img\\b[^>]*>")
        val tags = tagPattern.matcher(html)
        val attributePattern = Pattern.compile(
            "(?i)(?:src|data-src|data-original|data-lazy-src)\\s*=\\s*[\"']([^\"']+)[\"']"
        )
        while (tags.find()) {
            val attributes = attributePattern.matcher(tags.group())
            while (attributes.find()) {
                val candidate = attributes.group(1)?.trim().orEmpty()
                if (candidate.startsWith("https://") || candidate.startsWith("http://") || candidate.startsWith("//")) {
                    val absolute = if (candidate.startsWith("//")) "https:$candidate" else candidate
                    return normalizeBloggerImageUrl(absolute)
                }
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
    private fun stableCacheName(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decodeCoverFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 1100) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeCoverAsset(assetPath: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 1100) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun trimCoverDiskCache(folder: File) {
        val files = folder.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        val limit = 64L * 1024L * 1024L
        for (file in files) {
            if (total <= limit) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun downloadCoverToCache(imageUrl: String, destination: File) {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        connection.instanceFollowRedirects = true
        connection.useCaches = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            if (connection.contentLength > 12 * 1024 * 1024) throw IllegalStateException("Cover too large")
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 12L * 1024L * 1024L) throw IllegalStateException("Cover too large")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) throw IllegalStateException("Cache write failed")
            trimCoverDiskCache(destination.parentFile ?: cacheDir)
        } finally {
            temporary.delete()
            connection.disconnect()
        }
    }

    private fun loadCover(
        image: ImageView,
        localPath: String,
        imageUrl: String
    ) {
        val resolvedImageUrl = normalizeBloggerImageUrl(imageUrl)
        val localFile = localPath.takeIf { it.isNotBlank() && File(it).isFile }?.let(::File)
        val bundledAsset = if (resolvedImageUrl.isNotBlank()) {
            "offline_images/${stableCacheName(resolvedImageUrl)}.webp"
        } else ""
        val hasBundledAsset = if (bundledAsset.isNotBlank()) {
            try {
                assets.open(bundledAsset).use { true }
            } catch (_: Exception) {
                false
            }
        } else false

        // Include both sources in the key: a broken local copy must not hide a valid remote URL.
        val key = when {
            localFile != null -> "file:${localFile.absolutePath}|url:$resolvedImageUrl"
            hasBundledAsset -> "asset:$bundledAsset|url:$resolvedImageUrl"
            else -> "url:$resolvedImageUrl"
        }
        image.tag = key
        image.setImageResource(android.R.drawable.ic_menu_gallery)
        if (localFile == null && !hasBundledAsset && resolvedImageUrl.isBlank()) return

        coverBitmapCache.get(key)?.let {
            image.setImageBitmap(it)
            return
        }

        imageExecutor.execute {
            try {
                // Prefer the original local image, but fall back to the published URL if it
                // cannot be decoded. This is important for covers selected from document providers.
                var bitmap = localFile?.let { file ->
                    try { decodeCoverFile(file) } catch (_: Exception) { null }
                }

                if (bitmap == null && hasBundledAsset) {
                    bitmap = try { decodeCoverAsset(bundledAsset) } catch (_: Exception) { null }
                }

                if (bitmap == null && resolvedImageUrl.isNotBlank()) {
                    val folder = File(cacheDir, "cover_cache").apply { mkdirs() }
                    val cached = File(folder, stableCacheName(resolvedImageUrl) + ".img")
                    val fresh = cached.isFile &&
                        System.currentTimeMillis() - cached.lastModified() < 7L * 24L * 60L * 60L * 1000L
                    if (fresh) {
                        bitmap = try { decodeCoverFile(cached) } catch (_: Exception) { null }
                    }
                    // A stale or invalid cached response (for example an HTML error page) is
                    // discarded and downloaded again once before giving up.
                    if (bitmap == null) {
                        cached.delete()
                        downloadCoverToCache(resolvedImageUrl, cached)
                        bitmap = decodeCoverFile(cached)
                    }
                }

                bitmap?.let { decodedBitmap ->
                    coverBitmapCache.put(key, decodedBitmap)
                    mainHandler.post {
                        if (image.tag == key) image.setImageBitmap(decodedBitmap)
                    }
                }
            } catch (_: Exception) {
                // Keep the lightweight placeholder when no valid local or remote cover exists.
            }
        }
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

        if (requestCode == BACKUP_EXPORT_REQUEST || requestCode == BACKUP_IMPORT_REQUEST) {
            handleBackupResult(requestCode, resultCode, data)
            return
        }
        if (requestCode == SIGN_IN_REQUEST && resultCode == RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException::class.java)
                isAdminSession = isApprovedAdmin(account.email)
                if (!isAdminSession) googleSignInClient?.signOut()
                createInterface()
                Toast.makeText(this, if (isAdminSession) "تم ربط حساب Google المسؤول" else "هذا الحساب لا يملك صلاحية المسؤول. يمكنك المتابعة كزائر.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { isAdminSession = false; createInterface(); Toast.makeText(this, "فشل تسجيل الدخول إلى Google: ${e.message}", Toast.LENGTH_LONG).show() }
            return
        }
        if (requestCode == 1005 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            Thread {
                try {
                    val folder = File(filesDir, "profile")
                    if (!folder.exists()) folder.mkdirs()
                    val destination = File(folder, "avatar.img")
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "تعذرت قراءة الصورة" }
                        FileOutputStream(destination).use { output -> input.copyTo(output) }
                    }
                    profilePreferences().edit().putString("avatar_path", destination.absolutePath).apply()
                    mainHandler.post {
                        Toast.makeText(this, "تم تحديث صورة الملف الشخصي", Toast.LENGTH_SHORT).show()
                        showProfile()
                    }
                } catch (e: Exception) {
                    mainHandler.post { Toast.makeText(this, "تعذر حفظ الصورة: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }.start()
            return
        }
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != RESULT_OK) return

        val uri: Uri = data?.data ?: return
        coverUploadInProgress = true
        coverUploadFailed = false
        pendingCoverPublicUrl = ""
        coverUrlField?.setText("")

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
                    coverPreview?.let { loadCover(it, pendingCoverLocalPath, "") }
                    Toast.makeText(this, "تم اختيار الصورة، جارٍ رفعها للموقع...", Toast.LENGTH_SHORT).show()
                }
                val publicImageUrl = uploadCoverToDrive(destination)
                mainHandler.post {
                    pendingCoverPublicUrl = publicImageUrl
                    coverUploadInProgress = false
                    coverUploadFailed = false
                    coverUrlField?.setText(publicImageUrl)
                    coverPreview?.let { loadCover(it, pendingCoverLocalPath, publicImageUrl) }
                    Toast.makeText(this, "تم رفع الغلاف بنجاح وأصبح جاهزاً للنشر", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                mainHandler.post {
                    coverUploadInProgress = false
                    coverUploadFailed = true
                    val details = (e.message ?: e.javaClass.simpleName).ifBlank { "خطأ غير معروف" }
                    val detailsView = TextView(this).apply {
                        text = details
                        setTextIsSelectable(true)
                        setTextColor(white)
                        textSize = 13f
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                    }
                    val scroll = ScrollView(this).apply {
                        addView(detailsView)
                        setPadding(dp(8), 0, dp(8), 0)
                    }
                    AlertDialog.Builder(this)
                        .setTitle("فشل رفع الغلاف")
                        .setMessage("التفاصيل الكاملة لخطأ Google Drive:")
                        .setView(scroll)
                        .setPositiveButton("نسخ التفاصيل") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Google Drive error", details))
                            Toast.makeText(this, "تم نسخ تفاصيل الخطأ", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("إغلاق", null)
                        .show().also { styleAlertDialog(it) }
                }
            }
        }.start()
    }


    private fun showStartupExperience() {
        val splash = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val splashText = white
        val splashMuted = gray
        val splashAccent = red
        val content = FrameLayout(this).apply {
            setBackground(GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(20, 14, 39), Color.rgb(7, 10, 23), Color.rgb(4, 7, 16))
                else intArrayOf(Color.rgb(248, 249, 253), Color.rgb(237, 241, 249), Color.rgb(229, 234, 244))))
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(30), dp(28), dp(26))
        }
        val contentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(34), dp(24), dp(34))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(22, 20, 47), Color.rgb(8, 12, 27), Color.rgb(18, 12, 39))
                else intArrayOf(Color.WHITE, Color.rgb(244, 246, 251), Color.rgb(232, 237, 247))).apply {
                cornerRadius = dp(34).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(93, 76, 155) else Color.rgb(199, 207, 224))
            }
            elevation = dp(28).toFloat()
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.marco_rx6_brand)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "MARCO RX6"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isDarkMode()) Color.rgb(13, 10, 29) else Color.rgb(242, 244, 250))
                setStroke(dp(1), splashAccent)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            alpha = 0f
            scaleX = 0.82f
            scaleY = 0.82f
        }
        contentCard.addView(logo, LinearLayout.LayoutParams(dp(148), dp(148)).apply {
            bottomMargin = dp(18)
        })

        contentCard.addView(makeText("MARCO RX6", 23f, splashText, true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        contentCard.addView(makeText("GAME LIBRARY  •  BLOG HUB", 10f, splashMuted, true).apply {
            letterSpacing = 0.13f
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(25))
        })

        val progressTrack = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(if (isDarkMode()) Color.rgb(42, 34, 63) else Color.rgb(220, 226, 237))
                cornerRadius = dp(20).toFloat()
            }
            clipToOutline = true
        }
        val progressFill = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(splashAccent, Color.rgb(178, 112, 255), Color.rgb(95, 210, 220))
            ).apply { cornerRadius = dp(20).toFloat() }
        }
        progressTrack.addView(progressFill, FrameLayout.LayoutParams(0, -1, Gravity.START))
        contentCard.addView(progressTrack, LinearLayout.LayoutParams(-1, dp(10)))

        val loadingText = makeText("جارٍ تجهيز تجربتك...", 11f, splashMuted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        contentCard.addView(loadingText)
        center.addView(contentCard, LinearLayout.LayoutParams(-1, -2))
        content.addView(center, FrameLayout.LayoutParams(-1, -1))
        splash.setContentView(content)
        splash.setCancelable(false)
        splash.setCanceledOnTouchOutside(false)
        splash.show()
        splash.window?.setBackgroundDrawableResource(android.R.color.transparent)
        splash.window?.setLayout(-1, -1)
        splash.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        WindowInsetsControllerCompat(splash.window!!, splash.window!!.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650L)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.15f)).start()

        progressTrack.post {
            val animator = android.animation.ValueAnimator.ofInt(0, 100).apply {
                duration = 1750L
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animation ->
                    val percent = animation.animatedValue as Int
                    val params = progressFill.layoutParams
                    params.width = (progressTrack.width * percent / 100f).toInt()
                    progressFill.layoutParams = params
                    loadingText.text = when {
                        percent < 35 -> "جارٍ تهيئة المكتبة..."
                        percent < 75 -> "جارٍ تجهيز المحتوى..."
                        else -> "مرحباً بك في MARCO RX6"
                    }
                }
            }
            animator.start()
        }

        mainHandler.postDelayed({
            if (!isFinishing) {
                splash.dismiss()
                val settings = getSharedPreferences("app_settings", MODE_PRIVATE)
                if (!settings.getBoolean("setup_completed", false)) {
                    showFirstRunSetup()
                } else if (!settings.getBoolean("profile_prompted", false)) {
                    settings.edit().putBoolean("profile_prompted", true).apply()
                    showProfile()
                }
            }
        }, 1850L)
    }


    private fun showFirstRunSetup() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(6))
        }
        fun heading(value: String) {
            box.addView(makeText(value, 12f, red, true).apply { setPadding(0, dp(12), 0, dp(5)) })
        }
        heading("لغة التطبيق")
        val language = themedSpinner(listOf("العربية", "English", "Français", "Español"), listOf("ar", "en", "fr", "es").indexOf(currentLanguage()).coerceAtLeast(0))
        box.addView(language)
        heading("المظهر")
        val mode = themedSpinner(listOf("الوضع الليلي", "الوضع النهاري"), if (isDarkMode()) 0 else 1)
        box.addView(mode)
        heading("لون واجهة MARCO RX6")
        val themeKeys = listOf("violet", "blue", "green", "gold", "rose")
        val theme = themedSpinner(listOf("بنفسجي", "أزرق", "أخضر", "ذهبي", "وردي"), themeKeys.indexOf(appearancePreferences().getString("accent", "violet")).coerceAtLeast(0))
        box.addView(theme)
        heading("الإشعارات")
        val notifications = CheckBox(this).apply {
            text = "أريد استقبال إشعارات المقالات الجديدة"
            setTextColor(white)
            isChecked = false
            buttonTintList = android.content.res.ColorStateList.valueOf(red)
        }
        box.addView(notifications)
        AlertDialog.Builder(this)
            .setTitle("مرحباً بك في MARCO RX6")
            .setMessage("خصص تجربتك قبل البدء. يمكنك تغيير هذه الخيارات لاحقاً من القائمة.")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("حفظ وابدأ") { _, _ ->
                val languageCode = listOf("ar", "en", "fr", "es")[language.selectedItemPosition]
                getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                    .putString("language", languageCode).putBoolean("setup_completed", true).apply()
                appearancePreferences().edit()
                    .putBoolean("dark_mode", mode.selectedItemPosition == 0)
                    .putString("accent", themeKeys[theme.selectedItemPosition]).apply()
                notificationPreferences().edit().putBoolean("enabled", notifications.isChecked).apply()
                if (notifications.isChecked) {
                    scheduleBlogNotifications()
                    requestNotificationPermissionIfNeeded()
                } else {
                    WorkManager.getInstance(this).cancelUniqueWork(BlogNotificationWorker.WORK_NAME)
                }
                recreate()
            }
            .setNegativeButton("لاحقاً") { _, _ ->
                getSharedPreferences("app_settings", MODE_PRIVATE).edit().putBoolean("setup_completed", true).apply()
            }
            .setCancelable(false)
            .create().apply { setOnShowListener { styleAlertDialog(this) } }.show()
    }

    private var activeProfileDialog: AlertDialog? = null
    private fun profilePreferences() = getSharedPreferences("user_profile", MODE_PRIVATE)
    private fun showProfile() {
        val prefs = profilePreferences()
        val settings = getSharedPreferences("app_settings", MODE_PRIVATE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(6))
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 60)
            clipToOutline = true
            val path = prefs.getString("avatar_path", "").orEmpty()
            if (path.isNotBlank() && File(path).exists()) {
                BitmapFactory.decodeFile(path)?.let { setImageBitmap(it) }
            } else {
                setImageResource(android.R.drawable.ic_menu_camera)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        box.addView(avatar, LinearLayout.LayoutParams(dp(92), dp(92)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(8)
        })
        val chooseAvatar = TextView(this).apply {
            text = "اختيار صورة الملف الشخصي"
            textSize = 12f
            setTextColor(red)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(14))
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                activeProfileDialog?.dismiss()
                startActivityForResult(intent, 1005)
            }
        }
        box.addView(chooseAvatar, LinearLayout.LayoutParams(-1, -2))
        val nameLabel = makeText(tr("اسم العرض"), 11f, red, true)
        val input = makeInput(tr("اسم العرض")).apply {
            setSingleLine(true)
            setText(prefs.getString("nickname", "Player") ?: "Player")
        }
        box.addView(nameLabel)
        box.addView(input, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6); bottomMargin = dp(14) })

        val favoriteCount = games.count { it.isFavorite }
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(panel, 14)
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        fun stat(value: String, label: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(makeText(value, 21f, red, true))
            addView(makeText(label, 10f, gray))
        }
        stats.addView(stat(games.size.toString(), "المقالات"), LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(stat(favoriteCount.toString(), "المفضلة"), LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        fun settingButton(label: String, detail: String, action: () -> Unit) =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(panel, 12)
                setPadding(dp(13), dp(11), dp(13), dp(11))
                addView(makeText(label, 13f, white, true))
                addView(makeText(detail, 10f, gray).apply { setPadding(0, dp(3), 0, 0) })
                setOnClickListener { action() }
            }
        box.addView(settingButton("🌐  اللغة", "العربية • English • Français • Español") {
            showLanguagePicker()
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(settingButton("♧  الإشعارات", "التحكم في تنبيهات المقالات الجديدة") {
            showNotificationSettings()
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (prefs.getBoolean("profile_created", false)) tr("ملفي الشخصي") else "إنشاء ملف اللاعب")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(tr("حفظ"), null)
            .setNegativeButton(tr("إغلاق"), null)
            .create()
        activeProfileDialog = dialog
        dialog.setOnDismissListener { if (activeProfileDialog === dialog) activeProfileDialog = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                prefs.edit().putString("nickname", input.text.toString().trim().ifBlank { "Player" }).putBoolean("profile_created", true).apply()
                Toast.makeText(this, "تم حفظ الملف الشخصي", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
        styleAlertDialog(dialog)
        applyUnifiedSkin(box)
    }

    // PLAYSTATION LIBRARY: image-only horizontal shelves for every category.
    private fun renderGames() {
        gameList.removeAllViews()
        for (i in 0 until filters.childCount) {
            val chip = filters.getChildAt(i) as TextView
            val active = chip.tag?.toString() == selected
            chip.background = rounded(if (active) red else panel, 15)
            chip.setTextColor(if (active) accentForeground() else gray)
        }

        val query = search.text.toString().trim()
        val filtered = games.filter { item ->
            val categoryMatch = when (selected) {
                "ALL" -> true
                "FAVORITES" -> item.isFavorite
                "WANT_TO_PLAY" -> item.playStatus == "WANT_TO_PLAY"
                "PLAYING" -> item.playStatus == "PLAYING"
                "COMPLETED" -> item.playStatus == "COMPLETED"
                "BLOG" -> item.blogImported
                else -> item.platform == selected
            }
            val searchMatch = item.name.contains(query, ignoreCase = true) ||
                item.platform.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true)
            val genreMatch = selectedGenre.isBlank() || item.genre.equals(selectedGenre, ignoreCase = true)
            val yearMatch = selectedYear.isBlank() || item.releaseYear == selectedYear
            categoryMatch && searchMatch && genreMatch && yearMatch
        }
        val sorted = when (sortMode) {
            "A-Z" -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            "FAVORITES" -> filtered.sortedByDescending { it.isFavorite }
            "NEW" -> filtered.reversed()
            else -> filtered
        }
        countText.text = sorted.size.toString() + " " + tr("مقال")
        if (sorted.isEmpty()) {
            gameList.addView(makeText(tr("لا توجد مقالات حالياً"), 15f, gray, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(55), dp(10), dp(55))
            })
            return
        }

        val shelves: List<Pair<String, List<Game>>> = if (selected == "ALL" && query.isBlank()) {
            val order = listOf("PS4", "PS3", "PS2", "PS1", "PSP", "BLOG")
            order.mapNotNull { platform ->
                val items = sorted.filter { if (platform == "BLOG") it.platform == "BLOG" else it.platform == platform }
                if (items.isEmpty()) null else platform to items
            } + sorted.filter { it.platform !in order }.groupBy { it.platform }.map { it.key to it.value }
        } else {
            val title = when (selected) {
                "ALL" -> "الكل"
                "FAVORITES" -> tr("المفضلة")
                "WANT_TO_PLAY" -> "أريد لعبها"
                "PLAYING" -> "ألعبها حالياً"
                "COMPLETED" -> "أنهيتها"
                "BLOG" -> "BLOG"
                else -> selected
            }
            listOf(title to sorted)
        }

        shelves.forEach { (title, posts) ->
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(9), dp(2), dp(10))
            }
            heading.addView(makeText(title, 17f, if (isDarkMode()) Color.rgb(226, 232, 242) else Color.rgb(43, 52, 68), true), LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(makeText(posts.size.toString(), 10f, if (isDarkMode()) Color.rgb(169, 139, 255) else red, true))
            gameList.addView(heading, LinearLayout.LayoutParams(-1, -2))

            val horizontal = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(2), dp(2), dp(2), dp(13))
            }
            // Build cards in small batches so large libraries do not block the first draw.
            var nextPostIndex = 0
            fun appendNextBatch() {
                val endIndex = (nextPostIndex + 8).coerceAtMost(posts.size)
                while (nextPostIndex < endIndex) {
                    val post = posts[nextPostIndex++]
                    // Keep the original 142x190 cover intact; metadata sits below it.
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            if (isDarkMode()) intArrayOf(Color.rgb(42, 48, 67), Color.rgb(22, 27, 42))
                            else intArrayOf(Color.WHITE, Color.rgb(239, 243, 250))).apply {
                            cornerRadius = dp(18).toFloat()
                            setStroke(dp(1), if (isDarkMode()) Color.rgb(73, 84, 111) else Color.rgb(205, 213, 228))
                        }
                        setPadding(dp(4), dp(4), dp(4), dp(5))
                        elevation = dp(3).toFloat()
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { showDetails(post) }
                    }
                    val frame = FrameLayout(this).apply {
                        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            if (isDarkMode()) intArrayOf(Color.rgb(116, 127, 151), Color.rgb(35, 41, 59)) else intArrayOf(Color.rgb(235, 239, 247), Color.rgb(211, 220, 234))).apply {
                            cornerRadius = dp(14).toFloat()
                            setStroke(dp(1), if (isDarkMode()) Color.rgb(125, 139, 169) else Color.rgb(174, 185, 207))
                        }
                        clipToOutline = true
                        elevation = dp(2).toFloat()
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { showDetails(post) }
                    }
                    val cover = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(if (isDarkMode()) Color.rgb(29, 32, 58) else Color.rgb(238, 241, 247))
                        contentDescription = post.name
                        isClickable = true
                        setOnClickListener { showDetails(post) }
                    }
                    frame.addView(cover, FrameLayout.LayoutParams(-1, -1))
                    if (post.coverUrl.isNotBlank() || post.coverLocalPath.isNotBlank()) {
                        loadCover(cover, post.coverLocalPath, post.coverUrl)
                    } else {
                        cover.setImageResource(android.R.drawable.ic_menu_gallery)
                        cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                    card.addView(frame, LinearLayout.LayoutParams(dp(142), dp(190)))

                    val title = makeText(post.name, 12f, if (isDarkMode()) Color.rgb(238, 241, 248) else Color.rgb(43, 51, 68), true).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(3), dp(7), dp(3), dp(3))
                        isClickable = true
                        setOnClickListener { showDetails(post) }
                    }
                    card.addView(title, LinearLayout.LayoutParams(dp(142), dp(30)))

                    row.addView(card, LinearLayout.LayoutParams(dp(150), -2).apply {
                        marginEnd = dp(11)
                    })
                }
            }
            appendNextBatch()
            horizontal.setOnScrollChangeListener { view, scrollX, _, _, _ ->
                if (nextPostIndex < posts.size &&
                    scrollX + view.width >= row.width - dp(220)
                ) {
                    appendNextBatch()
                }
            }
            horizontal.addView(row)
            gameList.addView(horizontal, LinearLayout.LayoutParams(-1, dp(250)).apply {
                bottomMargin = dp(5)
            })
        }
    }

    // Dedicated immersive article reader; the WebView owns its scroll instead of nesting inside ScrollView.
    private fun showDetails(game: Game) {
        val reader = android.app.Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safeAreas.top, 0, safeAreas.bottom)
            insets
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
        close.minWidth = dp(48)
        close.minHeight = dp(48)
        topBar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
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
            minWidth = dp(48)
            minHeight = dp(48)
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

        // Liquid Metal 2.0 identity panel. Keep portrait cover proportions intact.
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(66, 74, 96), Color.rgb(25, 30, 46), Color.rgb(35, 27, 58))
                else intArrayOf(Color.WHITE, Color.rgb(235, 239, 248), Color.rgb(244, 239, 252))).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(91, 105, 133) else Color.rgb(199, 207, 224))
            }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(if (isDarkMode()) Color.rgb(48, 54, 72) else Color.rgb(235, 239, 247), 12)
            contentDescription = game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) loadCover(cover, game.coverLocalPath, game.coverUrl)
        else cover.setImageResource(android.R.drawable.ic_menu_gallery)
        identity.addView(cover, LinearLayout.LayoutParams(dp(104), dp(140)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        info.addView(makeText(game.name, 18f, white, true).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(makeText(game.platform + "  •  MARCO RX6", 10f, if (isDarkMode()) Color.rgb(190, 174, 255) else Color.rgb(104, 66, 165), true).apply { setPadding(0, dp(6), 0, dp(4)) })
        if (game.genre.isNotBlank() || game.releaseYear.isNotBlank()) {
            val metadata = listOf(game.genre, game.releaseYear).filter { it.isNotBlank() }.joinToString("  •  ")
            info.addView(makeText(metadata, 10f, gray, true).apply { setPadding(0, 0, 0, dp(7)) })
        }
        val watchlistNames = arrayOf("بدون حالة", "أريد لعبها", "ألعبها حالياً", "أنهيتها")
        val watchlistKeys = arrayOf("", "WANT_TO_PLAY", "PLAYING", "COMPLETED")
        fun watchlistLabel(status: String) = when (status) {
            "WANT_TO_PLAY" -> "أريد لعبها"
            "PLAYING" -> "ألعبها حالياً"
            "COMPLETED" -> "أنهيتها"
            else -> "إضافة إلى قائمة المتابعة"
        }
        val watchlistButton = TextView(this).apply {
            text = "＋  " + watchlistLabel(game.playStatus)
            textSize = 11f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = rounded(panel, 11)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            setOnClickListener {
                val current = watchlistKeys.indexOf(game.playStatus).coerceAtLeast(0)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("قائمة متابعة الألعاب")
                    .setSingleChoiceItems(watchlistNames, current) { dialog, which ->
                        val index = games.indexOfFirst { it.name == game.name && it.url == game.url }
                        if (index >= 0) {
                            games[index] = games[index].copy(playStatus = watchlistKeys[which])
                            saveGames()
                            text = "＋  " + watchlistLabel(watchlistKeys[which])
                            renderGames()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("إغلاق", null)
                    .show().also { styleAlertDialog(it) }
            }
        }
        info.addView(watchlistButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        identity.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(identity, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(10), dp(12), dp(10)) })

        val similar = games.filter { it.name != game.name && (it.platform == game.platform || it.blogImported == game.blogImported) }.take(5)
        if (similar.isNotEmpty()) {
            val similarBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(5), dp(14), dp(9)) }
            similarBox.addView(makeText("قد يعجبك أيضاً", 12f, if (isDarkMode()) Color.rgb(190, 174, 255) else Color.rgb(104, 66, 165), true).apply { setPadding(0, 0, 0, dp(7)) })
            val similarRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            similar.forEach { item ->
                similarRow.addView(TextView(this).apply {
                    text = item.name
                    textSize = 10f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(if (isDarkMode()) white else Color.rgb(48, 57, 77))
                    background = rounded(if (isDarkMode()) Color.rgb(35, 40, 57) else Color.rgb(232, 236, 245), 10)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    setOnClickListener { reader.dismiss(); showDetails(item) }
                }, LinearLayout.LayoutParams(dp(118), -2).apply { marginEnd = dp(7) })
            }
            val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(similarRow) }
            similarBox.addView(scroll)
            root.addView(similarBox)
        }

        val articleWebView = android.webkit.WebView(this).apply {
            setBackgroundColor(bg)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                enqueueGameDownload(url, game.name, userAgent, contentDisposition, mimeType, game.url)
            }
            val raw = if (game.bloggerHtml.isNotBlank()) game.bloggerHtml else
                "<h2>${game.name}</h2><p>${game.description}</p>"
            val articleHtml = raw.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
                .replace(Regex("(?i)\\s(onload|onclick|onerror)\\s*=\\s*(['\"]).*?\\2"), "")
            val style = """
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                *{box-sizing:border-box}
                html,body{margin:0;padding:0;width:100%;max-width:100%;overflow-x:hidden}
                body{background:${if (isDarkMode()) "#0a0b11" else "#f5f7fb"} !important;color:${if (isDarkMode()) "#f4f4f8" else "#202638"} !important;font-family:${fontFamilyName()},Arial,sans-serif;font-size:${(17 * fontScale()).toInt()}px;line-height:1.9;padding:20px 17px 36px;overflow-wrap:anywhere;direction:rtl;text-align:right}
                body *{color:inherit !important;background-color:transparent !important}
                body h1,body h2,body h3,body h4{color:${if (isDarkMode()) "#b9a2f5" else "#6842a5"} !important;line-height:1.4;margin:25px 0 12px}
                p{margin:0 0 18px}
                img{display:block;max-width:100%!important;height:auto!important;width:auto;margin:18px auto;border-radius:12px;object-fit:contain}
                body a{color:${if (isDarkMode()) "#e2c17c" else "#6842a5"} !important;text-decoration:none;font-weight:700}
                ul,ol{padding-left:25px;margin:10px 0 20px}
                li{padding:4px 0}
                body blockquote{border-right:3px solid ${if (isDarkMode()) "#d3b170" else "#8b5cf6"};padding:8px 14px;margin:16px 0;background:${if (isDarkMode()) "#1b1d3b" else "#eee9f8"} !important}
                table{display:block;max-width:100%;overflow-x:auto;border-collapse:collapse}
                td,th{border:1px solid ${if (isDarkMode()) "#596174" else "#cbd2df"};padding:8px}
                iframe,video{max-width:100%!important}
                </style>
            """.trimIndent()
            loadDataWithBaseURL(game.url.ifBlank { "https://marcorx6.blogspot.com/" },
                "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head>$style</head><body dir=\"rtl\">$articleHtml</body></html>",
                "text/html", "UTF-8", null)
        }
        root.addView(articleWebView, LinearLayout.LayoutParams(-1, 0, 1f))
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
                    background = rounded(red, 11)
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                    setOnClickListener { enqueueGameDownload(target, item.optString("label").ifBlank { game.name }) }
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            }
            downloadScroll.addView(downloadRow)
            root.addView(downloadScroll)
        }

        val archiveFolder = File(filesDir, "reader_cache").apply { mkdirs() }
        val archiveFile = File(archiveFolder, stableCacheName(game.url.ifBlank { game.name }) + ".mht")

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(panel)
        }
        fun readerAction(label: String, action: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(if (isDarkMode()) white else Color.rgb(48, 57, 77))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = rounded(if (isDarkMode()) Color.rgb(48, 53, 70) else Color.rgb(230, 235, 245), 11)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minHeight = dp(48)
            setOnClickListener { action() }
        }
        val offline = readerAction(if (archiveFile.isFile) "قراءة دون إنترنت" else "حفظ دون إنترنت") {
            if (archiveFile.isFile) {
                articleWebView.settings.allowFileAccess = true
                articleWebView.loadUrl(Uri.fromFile(archiveFile).toString())
                Toast.makeText(this, "تم فتح النسخة المحفوظة", Toast.LENGTH_SHORT).show()
            } else {
                articleWebView.saveWebArchive(archiveFile.absolutePath, false) { savedPath ->
                    mainHandler.post {
                        if (!savedPath.isNullOrBlank() && File(savedPath).isFile) {
                            Toast.makeText(this, "تم حفظ المقال للقراءة دون اتصال", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "تعذر حفظ المقال. انتظر اكتمال تحميل الصور ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        bottom.addView(offline, LinearLayout.LayoutParams(0, -2, 1.2f).apply { rightMargin = dp(6) })

        val share = readerAction(tr("مشاركة")) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, game.name)
                putExtra(Intent.EXTRA_TEXT, game.name + "\n" + game.url)
            }
            startActivity(Intent.createChooser(send, "مشاركة مقال من MARCO RX6"))
        }
        bottom.addView(share, LinearLayout.LayoutParams(0, -2, 1f))
        val comments = readerAction(tr("التعليقات")) { if (game.url.isNotBlank()) openGameLink(game.url) }
        bottom.addView(comments, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        if (isAdminSession) {
            val edit = readerAction("تعديل") { reader.dismiss(); showGameDialog(game) }
            bottom.addView(edit, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            val delete = readerAction("حذف") { reader.dismiss(); deleteGame(game) }
            bottom.addView(delete, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }
        root.addView(bottom)
        applyMotionPreferences(root)
        applyUnifiedSkin(root)
        reader.setContentView(root)
        reader.window?.setBackgroundDrawableResource(android.R.color.transparent)
        reader.show()
        reader.window?.let { readerWindow ->
            WindowCompat.setDecorFitsSystemWindows(readerWindow, false)
            readerWindow.statusBarColor = Color.TRANSPARENT
            readerWindow.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(readerWindow, readerWindow.decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode()
                isAppearanceLightNavigationBars = !isDarkMode()
            }
            readerWindow.setLayout(-1, -1)
            ViewCompat.requestApplyInsets(readerWindow.decorView)
        }
        reader.window?.setBackgroundDrawable(rounded(if (isDarkMode()) Color.rgb(9, 10, 22) else Color.rgb(245, 247, 251), 0))
        reader.setOnDismissListener { articleWebView.stopLoading(); articleWebView.destroy() }
    }

    private fun escapeArticleHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    // Structured article template shared by the preview and Blogger publishing.
    // Modern responsive editorial template shared by preview and Blogger publishing.
    private fun buildBloggerArticleHtml(title: String, categories: String, genre: String, releaseYear: String, description: String, coverUrl: String, downloadUrl: String): String {
        val safeTitle = escapeArticleHtml(title)
        val safeCategories = escapeArticleHtml(categories)
        val safeGenre = escapeArticleHtml(genre)
        val safeYear = escapeArticleHtml(releaseYear)
        val descriptionHtml = description.trim().split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .joinToString("") {
                "<p style=\"margin:0 0 16px;\">" + escapeArticleHtml(it).replace("\n", "<br/>") + "</p>"
            }
        val safeCover = escapeArticleHtml(normalizeBloggerImageUrl(coverUrl))
        val safeDownload = escapeArticleHtml(downloadUrl)
        val categoryBlock = if (safeCategories.isNotBlank()) {
            "<span style=\"display:inline-block;padding:6px 12px;border:1px solid #514078;border-radius:30px;background:#211936;color:#d8c6ff;font-size:12px;font-weight:700;\">$safeCategories</span>"
        } else ""
        val metaItems = listOfNotNull(
            safeGenre.takeIf { it.isNotBlank() }?.let { "<span style=\"display:inline-block;margin:5px 8px 5px 0;padding:7px 11px;background:#19243a;border:1px solid #293752;border-radius:9px;color:#cbd5e1;font-size:12px;\">🎮 $it</span>" },
            safeYear.takeIf { it.isNotBlank() }?.let { "<span style=\"display:inline-block;margin:5px 8px 5px 0;padding:7px 11px;background:#19243a;border:1px solid #293752;border-radius:9px;color:#cbd5e1;font-size:12px;\">📅 $it</span>" }
        ).joinToString("")
        val coverBlock = if (safeCover.isNotBlank()) {
            "<div style=\"margin:22px 0 24px;padding:7px;background:#111b2d;border:1px solid #2a3650;border-radius:18px;\"><img src=\"$safeCover\" alt=\"$safeTitle\" style=\"display:block;width:100%;height:auto;max-height:520px;object-fit:cover;border-radius:13px;\"/></div>"
        } else ""
        val downloadBlock = if (safeDownload.isNotBlank()) {
            "<div style=\"text-align:center;margin:30px 0 24px;\"><a href=\"$safeDownload\" style=\"display:inline-block;padding:14px 30px;border-radius:999px;background:#8b5cf6;color:#ffffff;text-decoration:none;font-size:15px;font-weight:800;letter-spacing:.2px;box-shadow:0 8px 24px rgba(139,92,246,.25);\">⬇ تحميل اللعبة الآن</a></div>"
        } else ""
        return """
            <article class="marcorx6-post" dir="rtl" style="box-sizing:border-box;max-width:900px;margin:18px auto;padding:clamp(16px,3vw,30px);overflow:hidden;border:1px solid #29334a;border-radius:22px;background:linear-gradient(145deg,#111a2c 0%,#0b1120 58%,#17112b 100%);color:#e8edf7;font-family:Arial,Tahoma,sans-serif;line-height:1.9;">
              <header style="padding:4px 0 18px;border-bottom:1px solid #29334a;">
                <div style="margin-bottom:12px;color:#a78bfa;font-size:11px;font-weight:800;letter-spacing:2px;">MARCO RX6 <span style="color:#64748b;">/ GAME DATABASE</span></div>
                <h1 style="margin:0 0 14px;color:#ffffff;font-size:clamp(24px,5vw,34px);font-weight:800;line-height:1.35;">$safeTitle</h1>
                $categoryBlock
              </header>
              $coverBlock
              <div style="margin:0 0 18px;">$metaItems</div>
              <section style="color:#d5deec;font-size:16px;line-height:2.05;overflow-wrap:anywhere;">$descriptionHtml</section>
              $downloadBlock
              <footer style="display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;margin-top:28px;padding-top:16px;border-top:1px solid #29334a;color:#8793a8;font-size:11px;">
                <span style="font-weight:800;letter-spacing:1px;color:#b9a2f5;">MARCO RX6</span>
                <span>مكتبة الألعاب والتطبيقات • اكتشف المزيد</span>
              </footer>
            </article>
        """.trimIndent()
    }

    // BLOG POST COMPOSER: reusable article template published directly to Blogger
    private fun showGameDialog(existing: Game? = null) {
        if (!isAdminSession) { Toast.makeText(this, "يجب تسجيل الدخول بصلاحية المسؤول للنشر.", Toast.LENGTH_SHORT).show(); return }
        pendingCoverLocalPath = existing?.coverLocalPath ?: pendingCoverLocalPath
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(16)); background = rounded(bg, 18) }
        val intro = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(panel, 15) }
        intro.addView(makeText(if (existing == null) "استوديو النشر • MARCO RX6" else "استوديو التحرير • MARCO RX6", 17f, white, true))
        intro.addView(makeText("جهّز الغلاف والتفاصيل والمحتوى، ثم عاين المقال قبل إرساله إلى Blogger.", 11f, gray).apply { setPadding(0, dp(5), 0, 0) })
        box.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val title = makeInput("عنوان المقال").apply { setSingleLine(true) }
        val categoryButton = Button(this).apply { text = "تحميل تسميات المدونة..."; setTextColor(white); background = rounded(panel, 12) }
        val selectedLabels = linkedSetOf<String>()
        val availableLabels = mutableListOf<String>()
        fun refreshLabelButton() {
            categoryButton.text = if (selectedLabels.isEmpty()) "اختيار Labels من المدونة" else "الأقسام (${selectedLabels.size}): " + selectedLabels.joinToString("، ")
        }
        categoryButton.setOnClickListener {
            if (availableLabels.isEmpty()) { Toast.makeText(this, "لم يتم تحميل تسميات المدونة بعد", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val checked = BooleanArray(availableLabels.size) { availableLabels[it] in selectedLabels }
            AlertDialog.Builder(this).setTitle("اختر أقسام المقال من المدونة")
                .setMultiChoiceItems(availableLabels.toTypedArray(), checked) { _, which, isChecked ->
                    if (isChecked) selectedLabels.add(availableLabels[which]) else selectedLabels.remove(availableLabels[which])
                    refreshLabelButton()
                }.setPositiveButton("تم", null)
                .setNeutralButton("تحديث التسميات") { _, _ ->
                    Thread {
                        try {
                            val latest = fetchBlogLabels()
                            mainHandler.post {
                                availableLabels.clear(); availableLabels.addAll(latest)
                                selectedLabels.retainAll(availableLabels.toSet()); refreshLabelButton()
                                Toast.makeText(this, "تم تحديث ${latest.size} تسمية من المدونة", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) { mainHandler.post { Toast.makeText(this, e.message ?: "تعذر تحديث التسميات", Toast.LENGTH_LONG).show() } }
                    }.start()
                }.show().also { styleAlertDialog(it) }
        }
        if (existing != null && existing.platform.isNotBlank()) selectedLabels.add(existing.platform)
        val body = makeInput("وصف المقال — اكتب المعلومات فقط، وسيُنسّقها القالب تلقائياً").apply { minLines = 8; gravity = Gravity.TOP or Gravity.START; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; setPadding(dp(13), dp(12), dp(13), dp(12)) }
        val coverUrl = makeInput("رابط صورة الغلاف (اختياري)").apply { setSingleLine(true) }
        val link = makeInput("رابط التحميل أو رابط إضافي (اختياري)").apply { setSingleLine(true) }
        val genre = makeInput("نوع اللعبة (مثال: أكشن، مغامرات)").apply { setSingleLine(true) }
        val releaseYear = makeInput("سنة الإصدار (مثال: 2024)").apply { setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        coverUrlField = coverUrl
        if (existing != null) {
            title.setText(existing.name)
            if (existing.platform.isNotBlank()) selectedLabels.add(existing.platform)
            body.setText(existing.description)
            coverUrl.setText(existing.coverUrl)
            link.setText(existing.url.takeIf { it != existing.sourcePostUrl } ?: "")
            genre.setText(existing.genre)
            releaseYear.setText(existing.releaseYear)
        } else {
            body.setText("")
            if (pendingCoverPublicUrl.isNotBlank()) coverUrl.setText(pendingCoverPublicUrl)
        }
        val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(panel, 13) }
        coverPreview = preview
        box.addView(makeText("02 • صورة الغلاف", 12f, red, true).apply { setPadding(0, dp(8), 0, dp(6)) })
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(165)).apply { bottomMargin = dp(8) })
        loadCover(preview, pendingCoverLocalPath, existing?.coverUrl ?: "")
        box.addView(Button(this).apply { text = "اختيار صورة الغلاف من الهاتف"; tag = "marco_primary_action"; setTextColor(accentForeground()); background = rounded(red, 12); setOnClickListener { chooseCover() } }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(makeText("أقسام المقال • Labels من المدونة", 10f, red, true)); box.addView(categoryButton)
        Thread {
            try {
                val labels = fetchBlogLabels()
                mainHandler.post {
                    availableLabels.clear(); availableLabels.addAll(labels)
                    if (existing == null && selectedLabels.isEmpty() && labels.isNotEmpty()) selectedLabels.add(labels.first())
                    refreshLabelButton()
                    categoryButton.isEnabled = labels.isNotEmpty()
                    if (labels.isEmpty()) categoryButton.text = "لا توجد Labels في المقالات المنشورة"
                }
            } catch (e: Exception) {
                mainHandler.post { categoryButton.text = "تعذر تحميل Labels • حاول التحديث"; categoryButton.isEnabled = true }
            }
        }.start()
        box.addView(genre); box.addView(releaseYear); box.addView(title)
        box.addView(makeText("03 • محتوى المقال", 12f, red, true).apply { setPadding(0, dp(12), 0, dp(5)) })
        box.addView(body, LinearLayout.LayoutParams(-1, dp(190))); box.addView(coverUrl); box.addView(link)
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false; setPadding(0, dp(4), 0, dp(8)); addView(box) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "إنشاء مقال جديد" else "تعديل المقال").setView(scroll).setNegativeButton("إغلاق", null).setNeutralButton("معاينة", null).setPositiveButton(if (existing == null) "نشر المقال" else "حفظ التعديلات", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val previewTitle = title.text.toString().trim().ifBlank { "معاينة المقال" }
                val previewHtml = "<html><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><body style=\"background:${if (isDarkMode()) "#0a0b11" else "#f5f7fb"};color:${if (isDarkMode()) "#f4f4f8" else "#202638"};padding:12px;\">" +
                    buildBloggerArticleHtml(previewTitle, selectedLabels.joinToString("، "), genre.text.toString(), releaseYear.text.toString(), body.text.toString(), coverUrl.text.toString(), link.text.toString()) +
                    "</body></html>"
                val web = WebView(this).apply {
                    setBackgroundColor(bg)
                    settings.javaScriptEnabled = false
                    loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
                }
                AlertDialog.Builder(this).setTitle("معاينة قبل النشر").setView(web)
                    .setPositiveButton("إغلاق", null).show().also { styleAlertDialog(it) }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val postTitle = title.text.toString().trim(); val categoryName = selectedLabels.firstOrNull() ?: ""; val coverLink = coverUrl.text.toString().trim(); val relatedLink = link.text.toString().trim()
                if (coverUploadInProgress) { Toast.makeText(this, "انتظر حتى يكتمل رفع صورة الغلاف", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                if (pendingCoverLocalPath.isNotBlank() && coverUrl.text.toString().isBlank()) { Toast.makeText(this, "صورة الغلاف لم تكتمل عملية رفعها. أعد اختيار الصورة وانتظر رسالة نجاح الرفع.", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                if (selectedLabels.isEmpty()) { Toast.makeText(this, "اختر Label واحداً على الأقل من المدونة", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (postTitle.isBlank()) { title.error = "أدخل عنوان المقال"; return@setOnClickListener }
                if (coverLink.isNotBlank() && !coverLink.startsWith("https://")) { coverUrl.error = "أدخل رابط صورة يبدأ بـ HTTPS"; return@setOnClickListener }
                if (relatedLink.isNotBlank() && !relatedLink.startsWith("https://")) { link.error = "أدخل رابطاً يبدأ بـ HTTPS"; return@setOnClickListener }
                val contentText = body.text.toString().trim()
                if (contentText.isBlank()) { body.error = "أدخل وصف المقال"; return@setOnClickListener }
                val publishedCoverLink = normalizeBloggerImageUrl(coverLink)
                val htmlContent = buildBloggerArticleHtml(postTitle, selectedLabels.joinToString("، "), genre.text.toString().trim(), releaseYear.text.toString().trim(), contentText, publishedCoverLink, relatedLink)
                val post = Game(name = postTitle, platform = categoryName, description = contentText, url = relatedLink.ifBlank { existing?.url ?: "" }, coverUrl = publishedCoverLink, coverLocalPath = if (coverLink.isBlank()) pendingCoverLocalPath else "", sourcePostUrl = existing?.sourcePostUrl ?: "", downloadLinks = existing?.downloadLinks ?: "", blogImported = true, isFavorite = existing?.isFavorite ?: false, bloggerPostId = existing?.bloggerPostId ?: "", bloggerHtml = htmlContent, playStatus = existing?.playStatus ?: "", genre = genre.text.toString().trim(), releaseYear = releaseYear.text.toString().trim())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Thread {
                    try {
                        if (bloggerBlogId.isBlank()) throw IllegalStateException("زامن المدونة أولاً، ثم حاول النشر.")
                        val payload = JSONObject().put("kind", "blogger#post").put("title", post.name).put("content", htmlContent).put("labels", JSONArray().apply { selectedLabels.forEach { put(it) } })
                        val result = if (existing == null) bloggerRequest("POST", "blogs/$bloggerBlogId/posts/", payload) else { if (existing.bloggerPostId.isBlank()) throw IllegalStateException("لا يملك هذا المقال معرّف Blogger. زامن المدونة ثم حاول مجدداً."); bloggerRequest("PUT", "blogs/$bloggerBlogId/posts/${existing.bloggerPostId}", payload) }
                        mainHandler.post {
                            val saved = post.copy(bloggerPostId = result.optString("id", post.bloggerPostId), bloggerHtml = result.optString("content", htmlContent), url = result.optString("url", post.url), sourcePostUrl = result.optString("url", post.sourcePostUrl), blogImported = true)
                            val idx = if (existing == null) -1 else games.indexOf(existing); if (idx >= 0) games[idx] = saved else games.add(0, saved)
                            pendingCoverLocalPath = ""; pendingCoverPublicUrl = ""; coverUploadFailed = false
                            saveGames(); renderGames(); dialog.dismiss(); Toast.makeText(this, "تم نشر المقال على Blogger", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) { mainHandler.post { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; Toast.makeText(this, e.message ?: "فشل نشر المقال", Toast.LENGTH_LONG).show() } }
                }.start()
            }
        }
        dialog.show()
        styleAlertDialog(dialog)
        applyUnifiedSkin(box)
        dialog.window?.setLayout(
            -1,
            (resources.displayMetrics.heightPixels * 0.88f).toInt()
        )
    }
    private fun deleteGame(game: Game) {

        AlertDialog.Builder(this)
            .setTitle("حذف المقال")
            .setMessage("هل تريد حذف المقال ${game.name}؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ ->
                if (!isAdminSession || bloggerBlogId.isBlank() || game.bloggerPostId.isBlank()) { Toast.makeText(this, "يلزم تسجيل دخول المسؤول ومعرّف المقال في Blogger", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                Thread { try {
                    bloggerRequest("DELETE", "blogs/$bloggerBlogId/posts/${game.bloggerPostId}")
                    mainHandler.post { games.remove(game); if (game.coverLocalPath.isNotBlank()) File(game.coverLocalPath).delete(); saveGames(); renderGames(); Toast.makeText(this, "تم الحذف من Blogger", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) { mainHandler.post { Toast.makeText(this, e.message ?: "تعذر حذف المقال من Blogger", Toast.LENGTH_LONG).show() } } }.start()
            }
            .create().apply { setOnShowListener { styleAlertDialog(this) } }
            .show()
    }

    private fun showGameOfTheDay() {
        if (games.isEmpty()) {
            Toast.makeText(this, "لا توجد ألعاب متاحة لاختيار لعبة اليوم", Toast.LENGTH_SHORT).show()
            return
        }
        val calendar = Calendar.getInstance()
        val dayKey = calendar.get(Calendar.YEAR) * 366 + calendar.get(Calendar.DAY_OF_YEAR)
        val game = games[Math.floorMod(dayKey, games.size)]
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(4))
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 14)
            contentDescription = game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
            loadCover(cover, game.coverLocalPath, game.coverUrl)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
            cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        box.addView(cover, LinearLayout.LayoutParams(-1, dp(190)))
        box.addView(makeText(game.name, 18f, white, true).apply { setPadding(0, dp(12), 0, dp(5)) })
        box.addView(makeText(game.platform + if (game.genre.isNotBlank()) " • ${game.genre}" else "", 11f, red, true))
        val summary = game.description.ifBlank { "اكتشف تفاصيل هذه اللعبة وروابطها داخل مكتبة MARCO RX6." }
        box.addView(makeText(summary, 12f, gray).apply { setPadding(0, dp(8), 0, 0); maxLines = 5; ellipsize = android.text.TextUtils.TruncateAt.END })
        AlertDialog.Builder(this)
            .setTitle("✧ لعبة اليوم")
            .setView(box)
            .setPositiveButton("تفاصيل اللعبة") { _, _ -> showDetails(game) }
            .setNeutralButton("فتح الرابط") { _, _ -> openGameLink(game.url) }
            .setNegativeButton("إغلاق", null)
            .create().apply { setOnShowListener { styleAlertDialog(this) } }
            .show()
    }

    private data class DownloadProbe(
        val finalUrl: String,
        val mimeType: String,
        val contentDisposition: String,
        val isDirectFile: Boolean
    )

    // Resolve redirects and verify the response is a file before handing it to Android DownloadManager.
    private fun inspectDownloadUrl(
        link: String,
        userAgent: String,
        hintedDisposition: String,
        hintedMimeType: String
    ): DownloadProbe {
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
            if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(link)?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Cookie", it)
            }
        }
        try {
            val code = connection.responseCode
            val mime = (connection.contentType ?: hintedMimeType)
                .substringBefore(';').trim().lowercase(Locale.ROOT)
            val disposition = connection.getHeaderField("Content-Disposition")
                ?: hintedDisposition
            val finalUrl = connection.url.toString()
            if (code !in 200..299) {
                throw IllegalStateException("الخادم رفض رابط التنزيل (HTTP $code)")
            }
            if (mime == "text/html" || mime == "application/xhtml+xml") {
                throw IllegalStateException("هذا الرابط يفتح صفحة HTML وليس ملفاً مباشراً. افتح صفحة التحميل وابحث عن رابط الملف نفسه.")
            }
            val path = Uri.parse(finalUrl).lastPathSegment.orEmpty().lowercase(Locale.ROOT)
            val hasFileExtension = Regex("""\.(apk|zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|pdf|mp4|mp3)$""").containsMatchIn(path)
            val isAttachment = disposition.contains("attachment", ignoreCase = true)
            val isBinary = mime == "application/octet-stream" ||
                mime.startsWith("application/") ||
                mime.startsWith("video/") || mime.startsWith("audio/")
            // HTML pages and ambiguous responses belong in the external browser.
            val isHtml = mime == "text/html" || mime == "application/xhtml+xml"
            val isDirectFile = !isHtml && (isAttachment || isBinary || hasFileExtension)
            return DownloadProbe(finalUrl, mime, disposition, isDirectFile)
        } finally {
            connection.disconnect()
        }
    }

    private fun enqueueGameDownload(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = ""
    ) {
        if (!link.startsWith("https://") && !link.startsWith("http://")) {
            Toast.makeText(this, "رابط التنزيل غير صالح", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "جارٍ التحقق من رابط الملف…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val probe = inspectDownloadUrl(link, userAgent, contentDisposition, hintedMimeType)
                if (!probe.isDirectFile) {
                    mainHandler.post {
                        Toast.makeText(
                            this,
                            "هذا رابط صفحة أو تعذّر التأكد أنه ملف مباشر؛ سيتم فتحه في المتصفح",
                            Toast.LENGTH_LONG
                        ).show()
                        openGameLink(probe.finalUrl)
                    }
                    return@Thread
                }
                val request = DownloadManager.Request(Uri.parse(probe.finalUrl))
                    .setTitle(title.ifBlank { "MARCO RX6" })
                    .setDescription("تنزيل ملف عبر MARCO RX6")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                if (probe.mimeType.isNotBlank()) request.setMimeType(probe.mimeType)
                if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
                if (Uri.parse(link).host.equals(Uri.parse(probe.finalUrl).host, ignoreCase = true)) {
                    CookieManager.getInstance().getCookie(probe.finalUrl)?.takeIf { it.isNotBlank() }?.let {
                        request.addRequestHeader("Cookie", it)
                    }
                }
                if (referrer.isNotBlank()) request.addRequestHeader("Referer", referrer)
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                mainHandler.post {
                    Toast.makeText(this, "تم التحقق من الملف وإضافته إلى مدير التنزيلات", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, e.message ?: "تعذر بدء تنزيل الملف", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showDownloadManager() {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or
                DownloadManager.STATUS_FAILED
        )
        val entries = mutableListOf<String>()
        try {
            manager.query(query)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val statusText = when (status) {
                        DownloadManager.STATUS_PENDING -> "في الانتظار"
                        DownloadManager.STATUS_RUNNING -> "قيد التنزيل"
                        DownloadManager.STATUS_PAUSED -> "متوقف مؤقتاً"
                        DownloadManager.STATUS_SUCCESSFUL -> "اكتمل"
                        DownloadManager.STATUS_FAILED -> "فشل"
                        else -> "غير معروف"
                    }
                    val progress = if (total > 0L) " • ${(downloaded * 100L / total).coerceIn(0L, 100L)}%" else ""
                    entries.add("$title\n$statusText$progress")
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة التنزيلات: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val message = if (entries.isEmpty()) "لا توجد تنزيلات مسجلة حتى الآن. افتح تفاصيل لعبة واضغط على رابط التنزيل لإضافته هنا."
            else entries.joinToString("\n\n")
        val dialog = AlertDialog.Builder(this)
            .setTitle("مدير التنزيلات • ${entries.size}")
            .setMessage(message)
            .setPositiveButton("تحديث", null)
            .setNeutralButton("فتح التنزيلات", null)
            .setNegativeButton("إغلاق", null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        dismiss()
                        showDownloadManager()
                    }
                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        try {
                            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "تعذر فتح مدير تنزيلات النظام", Toast.LENGTH_SHORT).show()
                        }
                        dismiss()
                    }
                }
            }
        dialog.show()
        styleAlertDialog(dialog)
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
                    .put("playStatus", game.playStatus)
                    .put("genre", game.genre)
                    .put("releaseYear", game.releaseYear)
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
        val preferences = getSharedPreferences("games_library", MODE_PRIVATE)
        val saved = preferences.getString("games", null)
        try {
            if (saved.isNullOrBlank()) {
                val offlineCatalog = assets.open("offline_catalog.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val entries = JSONArray(offlineCatalog)
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    parseBlogEntry(entry)?.let { games.add(it) }
                }
                if (games.isNotEmpty()) saveGames()
                return
            }

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
                        item.optBoolean("platformManuallyCorrected", false),
                        item.optBoolean("isFavorite", false),
                        item.optString("bloggerPostId", ""),
                        item.optString("bloggerHtml", ""),
                        item.optString("playStatus", ""),
                        item.optString("genre", ""),
                        item.optString("releaseYear", "")
                    )
                )
            }
        } catch (_: Exception) {
            // Recover the bundled starter catalog if saved preferences are malformed.
            // Never leave the library half-loaded after a JSON parsing failure.
            games.clear()
            try {
                val offlineCatalog = assets.open("offline_catalog.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val entries = JSONArray(offlineCatalog)
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    parseBlogEntry(entry)?.let { games.add(it) }
                }
                if (games.isNotEmpty()) saveGames()
            } catch (_: Exception) {
                games.clear()
            }
        }
    }


}