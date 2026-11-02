
package com.marcorx6.library

import android.Manifest
import android.app.Activity
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
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

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

    private val bg = Color.rgb(9, 10, 22)
    private val panel = Color.rgb(20, 22, 40)
    private val red = Color.rgb(139, 92, 246)
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
    // Bound image work to a small pool and keep decoded thumbnails in memory.
    private val coverExecutor = Executors.newFixedThreadPool(3)
    private val coverCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private var searchRenderRunnable: Runnable? = null
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

        // Hide the top status bar (clock, battery and status icons) to prevent overlap.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }

        createNotificationChannel()
        configureGoogleSignIn()
        loadGames()
        clearLegacyEngagementData()
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

    override fun onDestroy() {
        super.onDestroy()
    }

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
            .show()
    }

    private fun showSideMenu() {
        val gold = Color.rgb(211, 220, 236)
        val ink = Color.rgb(8, 11, 19)
        val surface = Color.rgb(24, 30, 44)
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
            text = "×"; textSize = 30f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0); setOnClickListener { drawer.dismiss() }
        })
        panel.addView(top)
        panel.addView(makeText("القائمة الرئيسية", 10f, Color.GRAY, true).apply {
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
            row.addView(makeText(icon, 21f, gold, true), LinearLayout.LayoutParams(dp(38), -2))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(makeText(label, 13f, white, true))
            if (detail.isNotBlank()) labels.addView(makeText(detail, 9f, Color.LTGRAY).apply { setPadding(0, dp(3), 0, 0) })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(makeText("‹", 20f, gold, true))
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        menuItem("♙", tr("حسابي"), tr("ملفي الشخصي")) { showProfile() }
        menuItem("文", tr("إعدادات اللغة"), tr("اختر لغة التطبيق")) { showLanguagePicker() }
        menuItem("♛", if (isAdminSession) tr("● مسؤول") else tr("دخول المسؤول  ↗"),
            if (isAdminSession) "تسجيل الخروج من حساب المسؤول" else "إدارة ونشر مقالات المدونة") {
            if (isAdminSession) signOutAdmin() else startGoogleSignIn()
        }
        menuItem("⟳", tr("⟳  مزامنة"), if (isSyncing) "المزامنة قيد التنفيذ" else "تحديث المقالات من المدونة") {
            syncFromBlog(showResult = true)
        }
        menuItem("♧", tr("♧  التنبيهات"), "التحكم في إشعارات المقالات الجديدة") { showNotificationSettings() }
        if (isAdminSession) {
            menuItem("＋", tr("＋  نشر"), "إضافة مقال جديد إلى المدونة") { showGameDialog() }
        }
        menuItem("Aa", "الخط وحجم النص", "تخصيص قراءة وواجهة التطبيق") { showTypographySettings() }
        panel.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(makeText("MARCO RX6  •  PREMIUM", 9f, Color.rgb(139, 151, 177), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        })
        drawer.setContentView(panel)
        drawer.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val lp = w.attributes
            lp.width = dp(310)
            lp.height = -1
            lp.gravity = if (currentLanguage() == "ar") Gravity.RIGHT else Gravity.LEFT
            lp.dimAmount = 0.62f
            w.attributes = lp
        }
        drawer.show()
        drawer.window?.setLayout(dp(310), -1)
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
        val family = Spinner(this)
        family.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("واضح (افتراضي)", "كلاسيكي", "أحادي المسافة"))
        family.setSelection(when (fontPreferences().getString("family", "sans")) { "serif" -> 1; "mono" -> 2; else -> 0 })
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
            }.show()
    }

    private fun createInterface() {
        // LIQUID METAL redesign: premium chrome surfaces, clear hierarchy and quick navigation.
        val silver = Color.rgb(226, 232, 242)
        val muted = Color.rgb(150, 162, 181)
        val violet = Color.rgb(151, 112, 255)
        val ink = Color.rgb(7, 9, 17)
        val glass = Color.rgb(19, 23, 36)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (currentLanguage() == "ar") View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            setPadding(dp(18), dp(12), dp(18), dp(96))
            setBackgroundColor(ink)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(18), dp(12) + bars.top, dp(18), dp(96) + bars.bottom)
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
                intArrayOf(Color.rgb(75, 83, 105), Color.rgb(25, 30, 45), Color.rgb(11, 14, 24))).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.rgb(93, 105, 130))
            }
            setPadding(dp(15), dp(10), dp(15), dp(10))
            contentDescription = "القائمة الجانبية"
            setOnClickListener { showSideMenu() }
        }
        header.addView(menu)
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(105, 115, 139), Color.rgb(44, 52, 73), Color.rgb(17, 22, 36), Color.rgb(49, 35, 83))).apply {
                cornerRadius = dp(27).toFloat()
                setStroke(dp(1), Color.rgb(130, 143, 170))
            }
            elevation = dp(5).toFloat()
            setPadding(dp(20), dp(19), dp(20), dp(18))
        }
        hero.addView(makeText("✧  PREMIUM GAME LIBRARY", 9f, silver, true).apply { letterSpacing = 0.12f })
        hero.addView(makeText(tr("اكتشف. العب.\nاستمتع أكثر."), 27f, Color.WHITE, true).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(5))
        })
        hero.addView(makeText(tr("ألعاب وشروحات وآخر الإصدارات، كلّها في مكان واحد."), 11f,
            Color.rgb(218, 222, 235)))
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        stats.addView(makeText("◈  ${games.size} " + tr("مقال"), 10f, silver, true))
        stats.addView(makeText("   •   " + tr("المدونة محدّثة"), 10f, Color.rgb(197, 184, 255), true))
        hero.addView(stats)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })

        val lastRead = games.filter { isRecentlyViewed(it) }.maxByOrNull { lastViewedAt(it) }
        if (lastRead != null) {
            val resume = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(33, 39, 55), Color.rgb(18, 22, 34))).apply {
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.rgb(66, 78, 105))
                }
                setPadding(dp(14), dp(11), dp(14), dp(11))
                setOnClickListener { showDetails(lastRead) }
            }
            val resumeLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            resumeLabels.addView(makeText("CONTINUE", 8f, violet, true))
            resumeLabels.addView(makeText(lastRead.name, 12f, silver, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            resume.addView(resumeLabels, LinearLayout.LayoutParams(0, -2, 1f))
            resume.addView(makeText("↗", 20f, silver, true))
            root.addView(resume, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(39, 46, 63), Color.rgb(17, 21, 33))).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.rgb(75, 87, 112))
            }
            setPadding(dp(14), 0, dp(14), 0)
        }
        searchWrap.addView(makeText("⌕", 26f, silver, true))
        search = AutoCompleteTextView(this).apply {
            hint = tr("ابحث عن الألعاب والمقالات...")
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setSingleLine(true)
            threshold = 1
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(139, 149, 170))
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
        (listOf("ALL", "BLOG", "FAVORITES", "RECENT") + platforms.drop(3)).forEach { category ->
            val chip = TextView(this).apply {
                text = when (category) {
                    "ALL" -> tr("الكل")
                    "BLOG" -> tr("المدونة")
                    "FAVORITES" -> tr("المفضلة")
                    "RECENT" -> tr("شوهدت مؤخراً")
                    else -> category
                }
                tag = category
                textSize = 10f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
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
        titles.addView(makeText(tr("أحدث المقالات"), 20f, silver, true))
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
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(74, 83, 105), Color.rgb(27, 33, 48), Color.rgb(13, 17, 28))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(100, 113, 140))
            }
            elevation = dp(14).toFloat()
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        fun dockItem(icon: String, label: String, action: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { action() }
            }
            item.addView(makeText(icon, 19f, silver, true).apply { gravity = Gravity.CENTER })
            item.addView(makeText(label, 8f, muted, true).apply { gravity = Gravity.CENTER })
            dock.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        dockItem("⌂", tr("الكل")) { selected = "ALL"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("♡", tr("المفضلة")) { selected = "FAVORITES"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("◷", tr("شوهدت مؤخراً")) { selected = "RECENT"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("☰", tr("القائمة")) { showSideMenu() }
        shell.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(12)
        })
        setContentView(shell)
        root.requestApplyInsets()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Avoid rebuilding every shelf for every keystroke.
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

    // IMAGE LOADER: bounded background work, downsampled bitmaps and an LRU cache.
    private fun loadCover(
        image: ImageView,
        localPath: String,
        imageUrl: String
    ) {
        val key = localPath.ifBlank { imageUrl }
        image.tag = key
        image.setImageResource(android.R.drawable.ic_menu_gallery)
        if (key.isBlank()) return

        coverCache.get(key)?.let {
            image.setImageBitmap(it)
            return
        }

        coverExecutor.execute {
            try {
                val bytes = if (localPath.isNotBlank()) {
                    File(localPath).inputStream().use { readCoverBytes(it) }
                } else {
                    val connection = URL(imageUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.instanceFollowRedirects = true
                    try {
                        connection.inputStream.use { readCoverBytes(it) }
                    } finally {
                        connection.disconnect()
                    }
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@execute

                val targetWidth = dp(142) * 2
                val targetHeight = dp(190) * 2
                var sampleSize = 1
                while (bounds.outWidth / (sampleSize * 2) >= targetWidth &&
                    bounds.outHeight / (sampleSize * 2) >= targetHeight
                ) {
                    sampleSize *= 2
                }

                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: return@execute
                coverCache.put(key, bitmap)

                mainHandler.post {
                    if (!isFinishing && image.tag == key) image.setImageBitmap(bitmap)
                }
            } catch (_: Exception) {
                // Keep the lightweight placeholder when an image is unavailable.
            }
        }
    }

    // Protect the UI from unusually large or malformed cover downloads.
    private fun readCoverBytes(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > 12 * 1024 * 1024) throw IllegalStateException("Cover image is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
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
    private fun lastViewedAt(game: Game): Long = historyPreferences().getLong("time_" + articleKey(game), 0L)
    private fun isRecentlyViewed(game: Game): Boolean = lastViewedAt(game) > 0L

    private fun clearLegacyEngagementData() {
        val prefs = historyPreferences()
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("rating_") || it.startsWith("views_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun recordRecentRead(game: Game) {
        historyPreferences().edit()
            .putLong("time_" + articleKey(game), System.currentTimeMillis())
            .apply()
    }

    private fun showProfile() {
        val prefs = profilePreferences()
        val settings = getSharedPreferences("app_settings", MODE_PRIVATE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(6))
        }
        val nameLabel = makeText(tr("اسم العرض"), 11f, red, true)
        val input = makeInput(tr("اسم العرض")).apply {
            setSingleLine(true)
            setText(prefs.getString("nickname", "Player") ?: "Player")
        }
        box.addView(nameLabel)
        box.addView(input, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6); bottomMargin = dp(14) })

        val favoriteCount = games.count { it.isFavorite }
        val recentCount = games.count { isRecentlyViewed(it) }
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
        stats.addView(stat(recentCount.toString(), "شوهدت"), LinearLayout.LayoutParams(0, -2, 1f))
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
        box.addView(settingButton("◷  سجل القراءة", "عرض المقالات التي شاهدتها مؤخراً") {
            selected = "RECENT"
            createInterface()
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(settingButton("✦  وضع القراءة", "حجم الخط والوضع الليلي متاحان داخل المقال") {
            Toast.makeText(this, "افتح أي مقال لتخصيص تجربة القراءة", Toast.LENGTH_SHORT).show()
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle(tr("ملفي الشخصي"))
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(tr("حفظ"), null)
            .setNegativeButton(tr("إغلاق"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                prefs.edit().putString("nickname", input.text.toString().trim().ifBlank { "Player" }).apply()
                Toast.makeText(this, "تم حفظ الملف الشخصي", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // PLAYSTATION LIBRARY: image-only horizontal shelves for every category.
    private fun renderGames() {
        gameList.removeAllViews()
        for (i in 0 until filters.childCount) {
            val chip = filters.getChildAt(i) as TextView
            val active = chip.tag?.toString() == selected
            chip.background = rounded(if (active) red else panel, 15)
            chip.setTextColor(if (active) bg else gray)
        }

        val query = search.text.toString().lowercase(Locale.ROOT).trim()
        val filtered = games.filter { item ->
            val categoryMatch = when (selected) {
                "ALL" -> true
                "FAVORITES" -> item.isFavorite
                "RECENT" -> isRecentlyViewed(item)
                "BLOG" -> item.blogImported
                else -> item.platform == selected
            }
            val searchMatch = item.name.lowercase(Locale.ROOT).contains(query) ||
                item.platform.lowercase(Locale.ROOT).contains(query) ||
                item.description.lowercase(Locale.ROOT).contains(query)
            categoryMatch && searchMatch
        }
        val sorted = when (sortMode) {
            "A-Z" -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            "FAVORITES" -> filtered.sortedByDescending { it.isFavorite }
            "NEW" -> filtered.reversed()
            else -> if (selected == "RECENT") filtered.sortedByDescending { lastViewedAt(it) } else filtered
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
                "RECENT" -> tr("شوهدت مؤخراً")
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
            heading.addView(makeText(title, 17f, Color.rgb(226, 232, 242), true), LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(makeText(posts.size.toString(), 10f, Color.rgb(169, 139, 255), true))
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
            posts.forEach { post ->
                // Keep the original 142x190 cover intact; metadata sits below it.
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showDetails(post) }
                }
                val frame = FrameLayout(this).apply {
                    background = rounded(Color.rgb(198, 204, 218), 13)
                    clipToOutline = true
                    elevation = dp(4).toFloat()
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showDetails(post) }
                }
                val cover = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.rgb(29, 32, 58))
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

                val title = makeText(post.name, 11f, Color.rgb(238, 241, 248), true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(3), dp(7), dp(3), dp(3))
                    isClickable = true
                    setOnClickListener { showDetails(post) }
                }
                card.addView(title, LinearLayout.LayoutParams(dp(142), dp(27)))

                row.addView(card, LinearLayout.LayoutParams(dp(142), -2).apply {
                    marginEnd = dp(11)
                })
            }
            horizontal.addView(row)
            gameList.addView(horizontal, LinearLayout.LayoutParams(-1, dp(250)).apply {
                bottomMargin = dp(5)
            })
        }
    }

    // Dedicated immersive article reader; the WebView owns its scroll instead of nesting inside ScrollView.
    private fun showDetails(game: Game) {
        recordRecentRead(game)
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
        val readerPrefs = historyPreferences()
        var readerFontSize = readerPrefs.getInt("reader_font_size", 17).coerceIn(14, 24)
        var readerLightMode = readerPrefs.getBoolean("reader_light_mode", false)
        var articleView: android.webkit.WebView? = null
        fun readerTool(label: String, action: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(red)
            gravity = Gravity.CENTER
            background = rounded(bg, 9)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { action() }
        }
        topBar.addView(readerTool("A−") {
            readerFontSize = (readerFontSize - 1).coerceAtLeast(14)
            readerPrefs.edit().putInt("reader_font_size", readerFontSize).apply()
            articleView?.reload()
        })
        topBar.addView(readerTool("A+") {
            readerFontSize = (readerFontSize + 1).coerceAtMost(24)
            readerPrefs.edit().putInt("reader_font_size", readerFontSize).apply()
            articleView?.reload()
        })
        topBar.addView(readerTool(if (readerLightMode) "☾" else "☼") {
            readerLightMode = !readerLightMode
            readerPrefs.edit().putBoolean("reader_light_mode", readerLightMode).apply()
            articleView?.reload()
        })
        root.addView(topBar)

        // Liquid Metal 2.0 identity panel. Keep portrait cover proportions intact.
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(66, 74, 96), Color.rgb(25, 30, 46), Color.rgb(35, 27, 58))).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(91, 105, 133))
            }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(48, 54, 72), 12)
            contentDescription = game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) loadCover(cover, game.coverLocalPath, game.coverUrl)
        else cover.setImageResource(android.R.drawable.ic_menu_gallery)
        identity.addView(cover, LinearLayout.LayoutParams(dp(104), dp(140)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        info.addView(makeText(game.name, 18f, white, true).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(makeText(game.platform + "  •  MARCO RX6", 10f, Color.rgb(190, 174, 255), true).apply { setPadding(0, dp(6), 0, dp(12)) })
        identity.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(identity, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(10), dp(12), dp(10)) })

        val similar = games.filter { it.name != game.name && (it.platform == game.platform || it.blogImported == game.blogImported) }.take(5)
        if (similar.isNotEmpty()) {
            val similarBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(5), dp(14), dp(9)) }
            similarBox.addView(makeText("قد يعجبك أيضاً", 12f, Color.rgb(190, 174, 255), true).apply { setPadding(0, 0, 0, dp(7)) })
            val similarRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            similar.forEach { item ->
                similarRow.addView(TextView(this).apply {
                    text = item.name
                    textSize = 10f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(white)
                    background = rounded(Color.rgb(35, 40, 57), 10)
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
                body{background:${if (readerLightMode) "#f6f2e9" else "#0a0b11"};color:${if (readerLightMode) "#24221e" else "#f4f4f8"};font-family:${fontFamilyName()},Arial,sans-serif;font-size:${(readerFontSize * fontScale()).toInt()}px;line-height:1.9;padding:20px 17px 36px;overflow-wrap:anywhere;direction:rtl;text-align:right}
                h1,h2,h3,h4{color:${if (readerLightMode) "#a78bfa" else "#8b5cf6"};line-height:1.4;margin:25px 0 12px}
                p{margin:0 0 18px}
                img{display:block;max-width:100%!important;height:auto!important;width:auto;margin:18px auto;border-radius:12px;object-fit:contain}
                a{color:${if (readerLightMode) "#8b5cf6" else "#d3b170"};text-decoration:none;font-weight:700}
                ul,ol{padding-left:25px;margin:10px 0 20px}
                li{padding:4px 0}
                blockquote{border-right:3px solid #d3b170;padding:8px 14px;margin:16px 0;background:${if (readerLightMode) "#eee7d9" else "#1b1d3b"}}
                table{display:block;max-width:100%;overflow-x:auto;border-collapse:collapse}
                td,th{border:1px solid #444;padding:8px}
                iframe,video{max-width:100%!important}
                </style>
            """.trimIndent()
            loadDataWithBaseURL(game.url.ifBlank { "https://marcorx6.blogspot.com/" },
                "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head>$style</head><body dir=\"rtl\">$articleHtml</body></html>",
                "text/html", "UTF-8", null)
        }
        articleView = articleWebView
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
                    background = rounded(Color.rgb(190, 43, 68), 11)
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                    setOnClickListener { openGameLink(target) }
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            }
            downloadScroll.addView(downloadRow)
            root.addView(downloadScroll)
        }

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