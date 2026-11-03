
package com.marcorx6.library.ui

import android.Manifest
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.marcorx6.library.R
import com.marcorx6.library.BlogNotificationWorker
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameRepository
import com.marcorx6.library.data.GameLibraryManager
import com.marcorx6.library.data.GameLibraryStore
import com.marcorx6.library.data.ProfileAvatarRepository
import com.marcorx6.library.data.BloggerPostRepository
import com.marcorx6.library.data.DownloadRepository
import com.marcorx6.library.network.BloggerApiClient
import com.marcorx6.library.network.BloggerFeedClient
import com.marcorx6.library.network.BloggerArticleBuilder
import com.marcorx6.library.notification.BlogNotificationScheduler
import com.marcorx6.library.network.BloggerFeedParser
import android.app.AlertDialog
import android.app.Dialog
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
import java.util.Locale
import java.util.Calendar
import java.util.regex.Pattern
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private val notificationScheduler by lazy { BlogNotificationScheduler(applicationContext) }
    private val downloadRepository by lazy { DownloadRepository(applicationContext) }
    private val downloadUiController by lazy {
        DownloadUiController(this, downloadRepository) { dialog -> styleAlertDialog(dialog) }
    }
    private val profileAvatarRepository by lazy { ProfileAvatarRepository(contentResolver, filesDir) }

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
    private fun accentColor(): Int = when (appearancePreferences().getString("accent", "blue")) {
        "blue" -> Color.rgb(42, 145, 255)
        "green" -> Color.rgb(35, 170, 125)
        "gold" -> Color.rgb(205, 157, 70)
        "rose" -> Color.rgb(215, 90, 135)
        else -> Color.rgb(42, 145, 255)
    }
    private val bg: Int get() = if (isDarkMode()) Color.rgb(7, 13, 25) else Color.rgb(245, 247, 251)
    private val panel: Int get() = if (isDarkMode()) Color.rgb(16, 27, 45) else Color.rgb(229, 234, 243)
    private val red: Int get() = accentColor()
    private val white: Int get() = if (isDarkMode()) Color.WHITE else Color.rgb(24, 29, 42)
    private fun accentForeground(): Int = Color.WHITE
    private val gray: Int get() = if (isDarkMode()) Color.LTGRAY else Color.rgb(86, 96, 115)
    private val platforms = listOf("الكل", "BLOG", "FAVORITES", "PS2", "PS3", "PS4")
    private val games = mutableListOf<Game>()
    private val googleDriveClient by lazy { com.marcorx6.library.network.GoogleDriveClient() }
    private val coverUploadRepository by lazy { com.marcorx6.library.data.CoverUploadRepository(filesDir, contentResolver, googleDriveClient) }
    private val coverRepository by lazy { com.marcorx6.library.data.CoverRepository(applicationContext).also { coverRepositoryCreated = true } }
    private var coverRepositoryCreated = false

    private lateinit var gameList: LinearLayout
    private lateinit var search: EditText
    private lateinit var filters: LinearLayout
    private lateinit var countText: TextView

    private var selected = "ALL"
    private var sortMode = "الأحدث"
    private var selectedGenre = ""
    private var selectedYear = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun postToUi(action: () -> Unit) {
        mainHandler.post {
            if (!isFinishing && !isDestroyed) action()
        }
    }

    private val ioExecutor = Executors.newCachedThreadPool()
    private var startupAnimator: ValueAnimator? = null
    private var startupRunnable: Runnable? = null
    private var startupDialog: Dialog? = null
    private var setupDialog: Dialog? = null
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
    private val bloggerApiClient by lazy { BloggerApiClient { googleBloggerToken() } }
    private val bloggerPostRepository by lazy {
        BloggerPostRepository(bloggerApiClient, { bloggerBlogId }, { isAdminSession })
    }
    private val gameRepository by lazy { GameRepository(applicationContext) }
    private val gameLibraryStore by lazy { GameLibraryStore(games) { saveGames() } }
    private val SIGN_IN_REQUEST = 1002

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleActivityResult(SIGN_IN_REQUEST, result.resultCode, result.data)
        }
    private val coverPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleActivityResult(PICK_IMAGE_REQUEST, result.resultCode, result.data)
        }
    private val profileAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleActivityResult(1005, result.resultCode, result.data)
        }

    private val bloggerFeedUrl =
        "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selected = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getString("preferred_platform", "ALL")
            ?.takeIf { it in listOf("PS2", "PS3", "PS4") } ?: "ALL"

        applySystemBarAppearance()

        notificationScheduler.createNotificationChannel()
        configureGoogleSignIn()
        loadGames()
        restoreAdminSession()
        createInterface()
        showStartupExperience()
        // Keep background prompts and sync away from the full-screen first-run setup.
        // They resume on the next activity creation after setup has been completed.
        val setupCompleted = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("setup_completed", false)
        if (setupCompleted) {
            if (notificationScheduler.isEnabled()) {
                notificationScheduler.schedule()
                requestNotificationPermissionIfNeeded()
            }
            syncFromBlog(showResult = false)
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBarAppearance()
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        ViewCompat.requestApplyInsets(window.decorView)
    }

    override fun onDestroy() {
        startupAnimator?.cancel()
        startupAnimator = null
        startupRunnable?.let(mainHandler::removeCallbacks)
        startupRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
        startupDialog?.let { if (it.isShowing) it.dismiss() }
        startupDialog = null
        setupDialog?.let { if (it.isShowing) it.dismiss() }
        setupDialog = null
        ioExecutor.shutdownNow()
        if (coverRepositoryCreated) coverRepository.close()
        super.onDestroy()
    }

    private fun configureGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestScopes(Scope("https://www.googleapis.com/auth/blogger"), Scope("https://www.googleapis.com/auth/drive.file")).build()
        googleSignInClient = GoogleSignIn.getClient(this, options)
    }
    private fun restoreAdminSession() {
        isAdminSession = false
        refreshBloggerAccess()
    }

    private fun refreshBloggerAccess() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val blogId = bloggerBlogId
        if (account?.account == null || blogId.isBlank()) {
            isAdminSession = false
            return
        }
        isAdminSession = false
        ioExecutor.execute {
            val allowed = try {
                bloggerApiClient.hasWriteAccess(blogId)
            } catch (_: Exception) {
                false
            }
            postToUi {
                val current = GoogleSignIn.getLastSignedInAccount(this)
                if (bloggerBlogId == blogId && current?.id == account.id) {
                    isAdminSession = allowed
                    createInterface()
                    if (!allowed) {
                        Toast.makeText(
                            this,
                            "هذا الحساب لا يملك صلاحية النشر أو التعديل في مدونة Blogger",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    private fun startGoogleSignIn() { googleSignInClient?.let { googleSignInLauncher.launch(it.signInIntent) } }
    private fun signOutAdmin() {
        googleSignInClient?.signOut()?.addOnCompleteListener { isAdminSession = false; createInterface() }
    }
    private fun googleBloggerToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(this)
            ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب Google")
        val androidAccount = account.account
            ?: throw IllegalStateException("بيانات حساب Google غير متاحة")
        return GoogleAuthUtil.getToken(
            applicationContext,
            androidAccount,
            "oauth2:https://www.googleapis.com/auth/blogger"
        )
    }

    private fun driveToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(this)
            ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب المسؤول")
        if (!isAdminSession) {
            throw SecurityException("يلزم التحقق من صلاحيات Blogger قبل رفع الأغلفة")
        }
        val androidAccount = account.account
            ?: throw IllegalStateException("بيانات حساب Google غير متاحة")
        return GoogleAuthUtil.getToken(
            applicationContext,
            androidAccount,
            "oauth2:https://www.googleapis.com/auth/drive.file"
        )
    }


    private fun bloggerHtml(game: Game): String {
        val text = game.description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
        val image = if (game.coverUrl.isNotBlank()) "<p><img src=\"${game.coverUrl}\" /></p>" else ""
        return "<div><p>$text</p>$image<p><a href=\"${game.url}\">تحميل اللعبة</a></p></div>"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2002)
        }
    }

    private val notificationSettingsController by lazy {
        NotificationSettingsController(this, notificationScheduler, ::styleAlertDialog) {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun showNotificationSettings() = notificationSettingsController.show()

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
            if (primary) intArrayOf(Color.rgb(36, 139, 255), Color.rgb(23, 194, 224))
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
            setStroke(dp(1), if (isDarkMode()) Color.rgb(57, 105, 158) else Color.rgb(211, 219, 234))
        })
        dialog.window?.let { window ->
            // Alert dialogs use their own window: keep system bars visible and let
            // Android place the dialog inside the safe area on edge-to-edge devices.
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            window.statusBarColor = bg
            window.navigationBarColor = bg
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
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
            list.setBackgroundColor(if (isDarkMode()) Color.rgb(16, 27, 45) else Color.WHITE)
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
                setStroke(dp(1), Color.rgb(57, 105, 158))
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
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    if (isDarkMode()) intArrayOf(Color.rgb(24, 39, 63), Color.rgb(12, 22, 39))
                    else intArrayOf(Color.WHITE, Color.rgb(237, 243, 252))).apply {
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.rgb(43, 83, 128) else Color.rgb(207, 220, 239))
                }
                elevation = dp(1).toFloat()
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
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    if (isDarkMode()) intArrayOf(Color.rgb(29, 103, 169), Color.rgb(18, 55, 91))
                    else intArrayOf(Color.rgb(220, 239, 255), Color.rgb(195, 222, 250))).apply {
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.rgb(69, 139, 201) else Color.rgb(165, 204, 239))
                }
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
        // الأكثر استخداماً أولاً، ثم الحساب والإدارة والتخصيص والقنوات
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
    private fun fontScale(): Float = TypographyPreferences.scale(fontPreferences())
    private fun fontFamilyName(): String = TypographyPreferences.family(fontPreferences())
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
        val selectedIndex = keys.indexOf(appearancePreferences().getString("accent", "blue")).coerceAtLeast(0)
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
        // RX6 GAME HUB: unified midnight-blue surfaces, electric-blue accents and clear game-first hierarchy.
        val silver = if (isDarkMode()) Color.rgb(226, 232, 242) else Color.rgb(43, 52, 68)
        val muted = gray
        val violet = red
        val ink = bg
        val glass = panel
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (currentLanguage() == "ar") View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            setPadding(dp(12), dp(8), dp(12), dp(104))
            setBackgroundColor(ink)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(12), dp(8) + safeAreas.top, dp(12), dp(104) + safeAreas.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(17))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText("MARCO RX6  /  OFFICIAL GAME HUB", 9f, if (isDarkMode()) Color.rgb(111, 190, 255) else Color.rgb(30, 116, 204), true).apply { letterSpacing = 0.08f })
        brand.addView(makeText("مكتبة الألعاب", 21f, silver, true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.01f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        fun headerAction(icon: String, label: String, action: () -> Unit) = TextView(this).apply {
            text = icon
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode()) Color.rgb(196, 222, 250) else Color.rgb(35, 104, 169))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(24, 42, 66), Color.rgb(10, 20, 35))
                else intArrayOf(Color.WHITE, Color.rgb(230, 239, 250))).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(47, 91, 139) else Color.rgb(191, 211, 235))
            }
            minWidth = dp(43); minHeight = dp(43)
            contentDescription = label
            setOnClickListener { action() }
        }
        header.addView(headerAction("♧", "الإشعارات") { showNotificationSettings() },
            LinearLayout.LayoutParams(dp(43), dp(43)).apply { marginEnd = dp(8) })
        header.addView(headerAction("⚙", "الإعدادات") { showAppearanceSettings() },
            LinearLayout.LayoutParams(dp(43), dp(43)))
        root.addView(header)

        // Compact game-first layout: the reference has no promotional hero banner.

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
        listOf("ALL", "PS4", "PS3", "PS2", "BLOG", "FAVORITES", "WANT_TO_PLAY", "PLAYING", "COMPLETED").forEach { category ->
            val chip = TextView(this).apply {
                text = when (category) {
                    "ALL" -> tr("الكل")
                    "PS4" -> "PS4"
                    "PS3" -> "PS3"
                    "PS2" -> "PS2"
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
        root.addView(makeText("اختر المنصة", 11f, red, true).apply {
            setPadding(dp(2), dp(2), dp(2), dp(7))
        })
        filterScroll.addView(filters)
        root.addView(filterScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(17) })

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(makeText("MARCO RX6  /  GAMES", 8f, violet, true).apply { letterSpacing = 0.14f })
        titles.addView(makeText(tr("اكتشف الألعاب"), 21f, silver, true))
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
                    intArrayOf(Color.rgb(118, 127, 151), Color.rgb(39, 126, 210))).apply {
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
                intArrayOf(Color.rgb(19, 39, 65), Color.rgb(10, 23, 41), Color.rgb(7, 15, 28))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(43, 105, 164))
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
                background = rounded(if (isDarkMode()) Color.rgb(22, 43, 68) else Color.rgb(232, 237, 246), 15)
                setOnClickListener { action() }
            }
            item.addView(makeText(icon, 15f, if (isDarkMode()) silver else Color.rgb(43, 123, 194), true).apply {
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
        dockItem("⌕", "بحث") { search.requestFocus(); pageScroll.smoothScrollTo(0, 0); (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
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

        ioExecutor.execute {
            try {
                val feedResult = BloggerFeedClient(bloggerFeedUrl).fetch()
                val imported = feedResult.games

                postToUi {
                    bloggerBlogId = feedResult.blogId
                    val result = mergeImportedGames(imported)
                    isSyncing = false
                    refreshBloggerAccess()
                    renderGames()

                    if (showResult) {
                        Toast.makeText(
                            this,
                            "اكتملت المزامنة: تمت إضافة ${result.first} وتحديث ${result.second}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                postToUi {
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
        }
    }

    private fun mergeImportedGames(imported: List<Game>): Pair<Int, Int> {
        val result = gameLibraryStore.mergeImported(imported)
        return result.added to result.updated
    }

    private fun showPlatformCorrection(game: Game) {

        val choices = arrayOf("PS2", "PS3", "PS4")

        AlertDialog.Builder(this)
            .setTitle("اختر المنصة")
            .setItems(choices) { _, which ->

                if (gameLibraryStore.correctPlatform(game.sourcePostUrl, choices[which])) {
                    renderGames()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show().also { styleAlertDialog(it) }
    }

    // IMAGE LOADER
    private fun loadCover(
        image: ImageView,
        localPath: String,
        imageUrl: String
    ) {
        val resolvedImageUrl = BloggerFeedParser.normalizeBloggerImageUrl(imageUrl)
        image.setImageResource(android.R.drawable.ic_menu_gallery)
        if (localPath.isBlank() && resolvedImageUrl.isBlank()) {
            image.tag = null
            return
        }
        val key = keyForCover(localPath, resolvedImageUrl)
        image.tag = key
        coverRepository.load(localPath, resolvedImageUrl) { bitmap ->
            if (bitmap != null && image.tag == key) image.setImageBitmap(bitmap)
        }
    }

    private fun keyForCover(localPath: String, imageUrl: String): String {
        val localFile = localPath.takeIf { it.isNotBlank() && File(it).isFile }?.let(::File)
        val asset = if (imageUrl.isNotBlank()) {
            "offline_images/${coverRepository.stableCacheName(imageUrl)}.webp"
        } else ""
        val hasAsset = asset.isNotBlank() && try {
            assets.open(asset).use { true }
        } catch (_: Exception) { false }
        return when {
            localFile != null -> "file:${localFile.absolutePath}|url:$imageUrl"
            hasAsset -> "asset:$asset|url:$imageUrl"
            else -> "url:$imageUrl"
        }
    }

    private fun chooseCover() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        coverPickerLauncher.launch(intent)
    }

    private fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        if (requestCode == SIGN_IN_REQUEST && resultCode == RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException::class.java)
                isAdminSession = false
                createInterface()
                refreshBloggerAccess()
                Toast.makeText(this, "تم ربط حساب Google؛ جارٍ التحقق من صلاحيات Blogger…", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { isAdminSession = false; createInterface(); Toast.makeText(this, "فشل تسجيل الدخول إلى Google: ${e.message}", Toast.LENGTH_LONG).show() }
            return
        }
        if (requestCode == 1005 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            ioExecutor.execute {
                try {
                    val destination = profileAvatarRepository.save(uri)
                    profilePreferences().edit().putString("avatar_path", destination.absolutePath).apply()
                    postToUi {
                        Toast.makeText(this, "تم تحديث صورة الملف الشخصي", Toast.LENGTH_SHORT).show()
                        showProfile()
                    }
                } catch (e: Exception) {
                    postToUi { Toast.makeText(this, "تعذر حفظ الصورة: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
            return
        }
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != RESULT_OK) return

        val uri: Uri = data?.data ?: return
        coverUploadInProgress = true
        coverUploadFailed = false
        pendingCoverPublicUrl = ""
        coverUrlField?.setText("")

        ioExecutor.execute {
            try {
                val uploadResult = coverUploadRepository.saveAndUploadCover(
                    uri,
                    driveToken()
                ) { localFile ->
                    postToUi {
                        val previousPendingPath = pendingCoverLocalPath
                        pendingCoverLocalPath = localFile.absolutePath
                        deleteCoverFileIfUnused(previousPendingPath)
                        coverUrlField?.setText("")
                        coverPreview?.let { loadCover(it, pendingCoverLocalPath, "") }
                        Toast.makeText(this, "تم اختيار الصورة، جارٍ رفعها للموقع...", Toast.LENGTH_SHORT).show()
                    }
                }
                postToUi {
                    pendingCoverLocalPath = uploadResult.localFile.absolutePath
                    pendingCoverPublicUrl = uploadResult.publicUrl
                    coverUploadInProgress = false
                    coverUploadFailed = false
                    coverUrlField?.setText(uploadResult.publicUrl)
                    coverPreview?.let { loadCover(it, pendingCoverLocalPath, uploadResult.publicUrl) }
                    Toast.makeText(this, "تم رفع الغلاف بنجاح وأصبح جاهزاً للنشر", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                postToUi {
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
                    val scroll = ScrollView(this).apply {                        addView(detailsView)
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
        }
    }


    private fun showStartupExperience() {
        startupAnimator?.cancel()
        startupRunnable?.let(mainHandler::removeCallbacks)
        val splash = Dialog(this, android.R.style.Theme_Material_NoActionBar)
        startupDialog = splash
        val content = FrameLayout(this).apply {
            setBackground(GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                Color.rgb(3, 10, 22), Color.rgb(5, 19, 39), Color.rgb(2, 8, 19)
            )))
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(24), dp(30), dp(24))
        }
        val logoHalo = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(7, 24, 45))
                setStroke(dp(2), Color.rgb(39, 160, 255))
            }
            elevation = dp(18).toFloat()
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.marco_rx6_dragon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "MARCO RX6"
            setPadding(dp(15), dp(15), dp(15), dp(15))
            alpha = 0f
            scaleX = 0.84f
            scaleY = 0.84f
        }
        logoHalo.addView(logo, FrameLayout.LayoutParams(-1, -1))
        center.addView(logoHalo, LinearLayout.LayoutParams(dp(154), dp(154)).apply { bottomMargin = dp(25) })
        center.addView(makeText("MARCO RX6", 25f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
        })
        center.addView(makeText("GAME LIBRARY", 10f, Color.rgb(105, 190, 255), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.28f
            setPadding(0, dp(9), 0, 0)
        })
        val loading = makeText("جارٍ تجهيز عالم الألعاب...", 11f, Color.rgb(153, 174, 199)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(34), 0, 0)
        }
        center.addView(loading)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 1
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(42, 145, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(29, 48, 70))
            progressDrawable = progressDrawable.mutate().apply {
                if (this is android.graphics.drawable.LayerDrawable) {
                    findDrawableByLayerId(android.R.id.background)?.let {
                        it.setTint(Color.rgb(29, 48, 70))
                    }
                    findDrawableByLayerId(android.R.id.progress)?.let {
                        it.setTint(Color.rgb(42, 145, 255))
                    }
                }
            }
        }
        center.addView(progress, LinearLayout.LayoutParams(dp(238), dp(7)).apply {
            topMargin = dp(18)
        })
        val percent = makeText("1%", 12f, Color.rgb(105, 190, 255), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, 0)
        }
        center.addView(percent)
        content.addView(center, FrameLayout.LayoutParams(-1, -1))
        splash.setContentView(content)
        splash.setCancelable(false)
        splash.setCanceledOnTouchOutside(false)
        splash.show()
        splash.window?.setBackgroundDrawableResource(android.R.color.transparent)
        splash.window?.setLayout(-1, -1)
        splash.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(w, w.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700L)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.08f)).start()
        startupAnimator = ValueAnimator.ofInt(1, 100).apply {
            duration = 4500L
            addUpdateListener { animator ->
                if (!isFinishing && !isDestroyed && startupDialog === splash && splash.isShowing) {
                    val value = animator.animatedValue as Int
                    progress.progress = value
                    percent.text = "$value%"
                }
            }
            start()
        }
        startupRunnable = Runnable {
            if (!isFinishing && !isDestroyed && startupDialog === splash && splash.isShowing) {
                startupAnimator?.cancel()
                startupAnimator = null
                splash.dismiss()
                startupDialog = null
                val settings = getSharedPreferences("app_settings", MODE_PRIVATE)
                if (!settings.getBoolean("setup_completed", false)) showFirstRunSetup()
                else if (!settings.getBoolean("profile_prompted", false)) {
                    settings.edit().putBoolean("profile_prompted", true).apply()
                    showProfile()
                }
            }
            startupRunnable = null
        }.also { mainHandler.postDelayed(it, 4600L) }
        splash.setOnDismissListener {
            startupAnimator?.cancel()
            startupAnimator = null
            startupRunnable?.let(mainHandler::removeCallbacks)
            startupRunnable = null
            if (startupDialog === splash) startupDialog = null
        }
    }

    private fun showFirstRunSetup() {
        val settings = getSharedPreferences("app_settings", MODE_PRIVATE)
        val appearance = appearancePreferences()
        var step = 0
        var preferredPlatform = settings.getString("preferred_platform", "PS3")
            ?.takeIf { it in listOf("PS2", "PS3", "PS4") } ?: "PS3"
        var languageCode = currentLanguage()
        var darkMode = isDarkMode()
        var accentKey = appearance.getString("accent", "blue")
            ?.takeIf { it in listOf("violet", "blue", "green", "gold", "rose") } ?: "blue"
        var notificationsEnabled = notificationScheduler.isEnabled()

        val steps = listOf(
            "منصة المكتبة", "لغة التطبيق", "المظهر", "لون الواجهة", "الإشعارات", "جاهز للانطلاق"
        )
        val dialog = Dialog(this, android.R.style.Theme_Material_NoActionBar)
        setupDialog = dialog
        dialog.setOnDismissListener { if (setupDialog === dialog) setupDialog = null }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 10, 22), Color.rgb(8, 22, 42), Color.rgb(4, 11, 24)))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = makeText("MARCO RX6", 13f, Color.WHITE, true).apply { letterSpacing = 0.12f }
        val stepCount = makeText("1 / 5", 11f, Color.rgb(126, 190, 245), true)
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(stepCount)
        root.addView(top)

        val progressTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Color.rgb(24, 43, 65), 8)
        }
        val progressFill = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(42, 145, 255), Color.rgb(23, 194, 224))).apply {
                cornerRadius = dp(8).toFloat()
            }
        }
        progressTrack.addView(progressFill, LinearLayout.LayoutParams(0, dp(5), 1f))
        root.addView(progressTrack, LinearLayout.LayoutParams(-1, dp(5)).apply {
            topMargin = dp(18)
            bottomMargin = dp(28)
        })

        val content = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        root.addView(content)
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        lateinit var renderStep: (Int, Boolean) -> Unit
        val back = Button(this).apply { text = "السابق"; setOnClickListener { renderStep(step - 1, true) } }
        val next = Button(this).apply { text = "التالي"; tag = "marco_primary_action" }
        footer.addView(back, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        footer.addView(next, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        fun optionCard(label: String, selected: Boolean, detail: String = ""): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = GradientDrawable().apply {
                    cornerRadius = dp(17).toFloat()
                    setColor(if (selected) Color.rgb(12, 48, 83) else Color.rgb(14, 29, 48))
                    setStroke(dp(if (selected) 2 else 1),
                        if (selected) Color.rgb(42, 145, 255) else Color.rgb(54, 76, 103))
                }
                addView(makeText(if (selected) "✓   $label" else label, 16f,
                    if (selected) Color.WHITE else Color.rgb(222, 232, 245), true))
                if (detail.isNotBlank()) addView(makeText(detail, 10f, Color.rgb(153, 174, 199)).apply {
                    setPadding(0, dp(5), 0, 0)
                })
                isClickable = true
                isFocusable = true
            }
        }

        fun animateIn(view: View) {
            view.alpha = 0f
            view.translationY = dp(18).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(360L)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }

        renderStep = { target: Int, animate: Boolean ->
            step = target.coerceIn(0, steps.lastIndex)
            stepCount.text = "${step + 1} / ${steps.size}"
            progressFill.layoutParams = LinearLayout.LayoutParams(0, dp(5), (step + 1).toFloat())
            progressTrack.removeAllViews()
            progressTrack.addView(progressFill)
            back.isEnabled = step > 0
            back.alpha = if (step > 0) 1f else 0.35f
            next.text = if (step == steps.lastIndex) "ابدأ الآن" else "التالي"
            content.animate().cancel()
            content.removeAllViews()
            val page = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                setPadding(0, dp(8), 0, dp(8))
            }
            page.addView(makeText("خطوة ${step + 1}", 10f, Color.rgb(92, 180, 255), true))
            page.addView(makeText(steps[step], 27f, Color.WHITE, true).apply {
                setPadding(0, dp(8), 0, dp(8))
            })
            val description = when (step) {
                0 -> "اختر المنصة التي تريد أن تبدأ بها مكتبتك. يمكنك تغييرها في أي وقت."
                1 -> "اختر اللغة التي تناسبك لعرض واجهة MARCO RX6."
                2 -> "اختر المظهر الذي يريح عينيك أثناء التصفح."
                3 -> "اختر اللون الذي سيظهر في الأزرار والعناصر التفاعلية."
                4 -> "هل ترغب في تلقي إشعار عند نشر مقالات جديدة في المدونة؟"
                else -> "تم إعداد مكتبتك. اختياراتك محفوظة ويمكنك تغييرها لاحقاً من الإعدادات."
            }
            page.addView(makeText(description, 13f, Color.rgb(164, 184, 207)).apply {
                setPadding(0, 0, 0, dp(24))
            })

            val choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun addChoice(label: String, detail: String = "", selectedNow: Boolean, action: () -> Unit) {
                val card = optionCard(label, selectedNow, detail)
                choices.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
                card.setOnClickListener {
                    action()
                    renderStep(step, false)
                }
                if (animate) {
                    card.alpha = 0f
                    card.translationY = dp(14).toFloat()
                    card.animate().alpha(1f).translationY(0f).setStartDelay((choices.childCount - 1) * 65L)
                        .setDuration(300L).start()
                }
            }

            when (step) {
                0 -> listOf("PS2", "PS3", "PS4").forEach { platform ->
                    addChoice(platform, when (platform) {
                        "PS2" -> "PlayStation 2"
                        "PS3" -> "PlayStation 3"
                        else -> "PlayStation 4"
                    }, preferredPlatform == platform) { preferredPlatform = platform }
                }
                1 -> listOf("ar" to "العربية", "en" to "English", "fr" to "Français", "es" to "Español")
                    .forEach { (code, label) -> addChoice(label, "", languageCode == code) { languageCode = code } }
                2 -> listOf(true to "الوضع الليلي", false to "الوضع النهاري")
                    .forEach { (dark, label) -> addChoice(label, "", darkMode == dark) { darkMode = dark } }
                3 -> listOf(
                    "violet" to "بنفسجي", "blue" to "أزرق", "green" to "أخضر",
                    "gold" to "ذهبي", "rose" to "وردي"
                ).forEach { (key, label) ->
                    val swatchColor = when (key) {
                        "violet" -> Color.rgb(145, 95, 235)
                        "green" -> Color.rgb(35, 170, 125)
                        "gold" -> Color.rgb(205, 157, 70)
                        "rose" -> Color.rgb(215, 90, 135)
                        else -> Color.rgb(42, 145, 255)
                    }
                    val card = optionCard(label, accentKey == key)
                    val dot = View(this).apply { background = rounded(swatchColor, 20) }
                    card.addView(dot, LinearLayout.LayoutParams(dp(24), dp(24)).apply { topMargin = dp(9) })
                    choices.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
                    card.setOnClickListener { accentKey = key; renderStep(step, false) }
                    if (animate) animateIn(card)
                }
                4 -> {
                    addChoice("تفعيل الإشعارات", "تنبيه عند وصول مقال جديد", notificationsEnabled) { notificationsEnabled = true }
                    addChoice("إيقاف الإشعارات", "يمكنك تفعيلها لاحقاً من الإعدادات", !notificationsEnabled) { notificationsEnabled = false }
                }
                5 -> {
                    val readyCard = optionCard("مكتبتك جاهزة!", true, "تم حفظ المنصة واللغة والمظهر واللون والإشعارات.")
                    choices.addView(readyCard, LinearLayout.LayoutParams(-1, -2))
                    if (animate) animateIn(readyCard)
                }
            }
            val scroll = ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(choices)
            }
            page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(page, FrameLayout.LayoutParams(-1, -1))
            if (animate) animateIn(page)
            next.setOnClickListener {
                if (step < steps.lastIndex) {
                    val old = step
                    content.animate().alpha(0f).translationX(-dp(28).toFloat()).setDuration(150L)
                        .withEndAction { renderStep(old + 1, true); content.translationX = dp(28).toFloat(); content.animate().alpha(1f).translationX(0f).setDuration(230L).start() }
                        .start()
                } else {
                    settings.edit()
                        .putString("language", languageCode)
                        .putString("preferred_platform", preferredPlatform)
                        .putBoolean("setup_completed", true)
                        .putBoolean("profile_prompted", true)
                        .apply()
                    appearance.edit().putBoolean("dark_mode", darkMode).putString("accent", accentKey).apply()
                    selected = preferredPlatform
                    notificationScheduler.setEnabled(notificationsEnabled)
                    if (notificationsEnabled) {
                        notificationScheduler.schedule()
                        requestNotificationPermissionIfNeeded()
                    } else notificationScheduler.cancel()
                    dialog.dismiss()
                    recreate()
                }
            }
        }

        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(-1, -1)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
        applyUnifiedSkin(root)
        renderStep(0, true)
    }

    private var activeProfileDialog: AlertDialog? = null
    private fun profilePreferences() = getSharedPreferences("user_profile", MODE_PRIVATE)
    private fun showProfile() {
        val prefs = profilePreferences()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(19, 91, 158), Color.rgb(12, 39, 67), Color.rgb(8, 26, 43))
                else intArrayOf(Color.rgb(226, 241, 255), Color.rgb(241, 247, 255))).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(65, 133, 194) else Color.rgb(173, 207, 237))
            }
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(if (isDarkMode()) Color.rgb(36, 65, 91) else Color.WHITE, 48)
            clipToOutline = true
            val path = prefs.getString("avatar_path", "").orEmpty()
            if (path.isNotBlank() && File(path).exists()) {
                BitmapFactory.decodeFile(path)?.let { setImageBitmap(it) }
            } else {
                setImageResource(android.R.drawable.ic_menu_camera)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        hero.addView(avatar, LinearLayout.LayoutParams(dp(78), dp(78)))
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        identity.addView(makeText("PLAYER PROFILE", 9f, if (isDarkMode()) Color.rgb(151, 207, 255) else Color.rgb(34, 119, 190), true).apply { letterSpacing = 0.12f })
        identity.addView(makeText(prefs.getString("nickname", "Player") ?: "Player", 19f, if (isDarkMode()) Color.WHITE else Color.rgb(22, 43, 65), true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, dp(3))
        })
        identity.addView(makeText("MARCO RX6  •  GAMER", 9f, if (isDarkMode()) Color.rgb(191, 211, 230) else Color.rgb(79, 105, 129), true))
        hero.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        hero.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
            activeProfileDialog?.dismiss()
            profileAvatarLauncher.launch(intent)
        }
        box.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        box.addView(makeText("اضغط على البطاقة لتغيير الصورة الشخصية", 10f, gray).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })

        val input = makeInput(tr("اسم العرض")).apply {
            setSingleLine(true)
            setText(prefs.getString("nickname", "Player") ?: "Player")
        }
        box.addView(makeText(tr("اسم العرض"), 11f, red, true))
        box.addView(input, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6); bottomMargin = dp(14) })

        val favoriteCount = games.count { it.isFavorite }
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun statCard(value: String, label: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(panel, 16)
            setPadding(dp(10), dp(14), dp(10), dp(14))
            addView(makeText(value, 23f, red, true))
            addView(makeText(label, 10f, gray).apply { setPadding(0, dp(3), 0, 0) })
        }
        stats.addView(statCard(games.size.toString(), "الألعاب"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        stats.addView(statCard(favoriteCount.toString(), "المفضلة"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        box.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        fun settingButton(icon: String, label: String, detail: String, action: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 15)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addView(makeText(icon, 20f, red, true).apply { gravity = Gravity.CENTER })
            val copy = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(makeText(label, 13f, white, true))
                addView(makeText(detail, 10f, gray).apply { setPadding(0, dp(3), 0, 0) })
            }
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(makeText("‹", 22f, gray, true))
            setOnClickListener { action() }
        }
        box.addView(settingButton("文", "اللغة", "العربية • English • Français • Español") { showLanguagePicker() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(settingButton("♧", "الإشعارات", "التحكم في تنبيهات المقالات الجديدة") { showNotificationSettings() })

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (prefs.getBoolean("profile_created", false)) tr("ملفي الشخصي") else "إنشاء ملف اللاعب")
            .setView(ScrollView(this).apply { isFillViewport = true; addView(box) })
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
    private fun renderGames() {
        gameList.removeAllViews()
        for (i in 0 until filters.childCount) {
            val chip = filters.getChildAt(i) as TextView
            val active = chip.tag?.toString() == selected
            chip.background = rounded(if (active) red else panel, 13)
            chip.setTextColor(if (active) accentForeground() else gray)
        }
        val sorted = GameLibraryManager.filterAndSort(games, selected, search.text.toString().trim(), selectedGenre, selectedYear, sortMode)
        countText.text = sorted.size.toString() + " " + tr("لعبة")
        if (sorted.isEmpty()) {
            gameList.addView(makeText(tr("لا توجد ألعاب حالياً"), 15f, gray, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(10), dp(55), dp(10), dp(55))
            })
            return
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = when (selected) {
            "ALL" -> tr("اكتشف الألعاب"); "FAVORITES" -> tr("المفضلة"); "BLOG" -> tr("المدونة")
            "WANT_TO_PLAY" -> "أريد لعبها"; "PLAYING" -> "ألعبها حالياً"; "COMPLETED" -> "أنهيتها"
            else -> "ألعاب " + selected
        }
        heading.addView(makeText(title, 18f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(makeText(sorted.size.toString() + " " + tr("لعبة"), 10f, red, true))
        gameList.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(2), dp(10), dp(2), dp(12)) })
        for (index in sorted.indices step 3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            for (offset in 0..2) {
                val itemIndex = index + offset
                if (itemIndex >= sorted.size) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
                    continue
                }
                val game = sorted[itemIndex]
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        if (isDarkMode()) intArrayOf(Color.rgb(20, 34, 57), Color.rgb(11, 20, 36)) else intArrayOf(Color.WHITE, Color.rgb(235, 241, 250))).apply {
                        cornerRadius = dp(18).toFloat()
                        setStroke(dp(1), if (isDarkMode()) Color.rgb(37, 75, 119) else Color.rgb(215, 225, 239))
                    }
                    setPadding(dp(6), dp(6), dp(6), dp(8)); elevation = dp(2).toFloat(); isClickable = true; isFocusable = true
                    setOnClickListener { showDetails(game) }
                }
                val imageFrame = FrameLayout(this).apply { clipToOutline = true; background = rounded(bg, 12) }
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = game.name }
                imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
                if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) loadCover(image, game.coverLocalPath, game.coverUrl)
                else { image.setImageResource(android.R.drawable.ic_menu_gallery); image.scaleType = ImageView.ScaleType.CENTER_INSIDE }
                imageFrame.background = rounded(if (isDarkMode()) Color.rgb(8, 16, 29) else Color.rgb(224, 232, 243), 13)
                card.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(142)))
                card.addView(makeText(game.name, 12.5f, white, true).apply {
                    maxLines = 2; minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(7), dp(4), dp(2))
                })
                val meta = listOf(game.platform, game.releaseYear).filter { it.isNotBlank() }.joinToString(" • ")
                card.addView(makeText(meta, 9f, red, true).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(4), 0, dp(4), dp(2))
                })
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3); bottomMargin = dp(8) })
            }
            gameList.addView(row, LinearLayout.LayoutParams(-1, -2))
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
            setPadding(dp(16), dp(10), dp(12), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                if (isDarkMode()) intArrayOf(Color.rgb(17, 32, 54), Color.rgb(9, 17, 31))
                else intArrayOf(Color.WHITE, Color.rgb(235, 242, 251)))
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

        // RX6 game identity: cinematic blue surface with a clear cover-and-metadata hierarchy.
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(24, 67, 111), Color.rgb(13, 32, 55), Color.rgb(8, 20, 36))
                else intArrayOf(Color.rgb(250, 253, 255), Color.rgb(226, 239, 253), Color.rgb(239, 235, 255))).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(64, 124, 184) else Color.rgb(174, 204, 236))
            }
            elevation = dp(3).toFloat()
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(if (isDarkMode()) Color.rgb(48, 54, 72) else Color.rgb(235, 239, 247), 12)
            contentDescription = game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) loadCover(cover, game.coverLocalPath, game.coverUrl)
        else cover.setImageResource(android.R.drawable.ic_menu_gallery)
        identity.addView(cover, LinearLayout.LayoutParams(dp(112), dp(152)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        info.addView(makeText("GAME DETAILS  •  MARCO RX6", 8f, if (isDarkMode()) Color.rgb(142, 205, 255) else Color.rgb(37, 119, 190), true).apply { letterSpacing = 0.08f })
        info.addView(makeText(game.name, 19f, white, true).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(5), 0, 0) })
        info.addView(makeText(game.platform + "  •  MARCO RX6", 10f, if (isDarkMode()) Color.rgb(111, 194, 255) else Color.rgb(30, 139, 235), true).apply { setPadding(0, dp(7), 0, dp(5)) })
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
        root.addView(identity, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(12), dp(14), dp(12)) })

        val similar = games.filter { it.name != game.name && (it.platform == game.platform || it.blogImported == game.blogImported) }.take(5)
        if (similar.isNotEmpty()) {
            val similarBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(5), dp(14), dp(9)) }
            similarBox.addView(makeText("اكتشف المزيد  •  قد يعجبك أيضاً", 12f, if (isDarkMode()) Color.rgb(111, 194, 255) else Color.rgb(30, 139, 235), true).apply { setPadding(0, 0, 0, dp(9)) })
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
            // Article content is remote Blogger HTML; keep local file/content access
            // disabled unless the user explicitly opens a saved offline archive.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
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
                body h1,body h2,body h3,body h4{color:${if (isDarkMode()) "#59b7ff" else "#238de8"} !important;line-height:1.4;margin:25px 0 12px}
                p{margin:0 0 18px}
                img{display:block;max-width:100%!important;height:auto!important;width:auto;margin:18px auto;border-radius:12px;object-fit:contain}
                body a{color:${if (isDarkMode()) "#e2c17c" else "#238de8"} !important;text-decoration:none;font-weight:700}
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
        val archiveFile = File(archiveFolder, coverRepository.stableCacheName(game.url.ifBlank { game.name }) + ".mht")

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
                    postToUi {
                        if (!savedPath.isNullOrBlank() && File(savedPath).isFile) {
                            Toast.makeText(this, "تم حفظ المقال للقراءة دون اتصال", Toast.LENGTH_LONG).show()                        } else {
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
        reader.window?.setBackgroundDrawable(rounded(if (isDarkMode()) Color.rgb(7, 13, 25) else Color.rgb(245, 247, 251), 0))
        reader.setOnDismissListener { articleWebView.stopLoading(); articleWebView.destroy() }
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
                    ioExecutor.execute {
                        try {
                            val latest = bloggerPostRepository.fetchLabels()
                            postToUi {
                                availableLabels.clear(); availableLabels.addAll(latest)
                                selectedLabels.retainAll(availableLabels.toSet()); refreshLabelButton()
                                Toast.makeText(this, "تم تحديث ${latest.size} تسمية من المدونة", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) { postToUi { Toast.makeText(this, e.message ?: "تعذر تحديث التسميات", Toast.LENGTH_LONG).show() } }
                    }
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
        ioExecutor.execute {
            try {
                val labels = bloggerPostRepository.fetchLabels()
                postToUi {
                    availableLabels.clear(); availableLabels.addAll(labels)
                    if (existing == null && selectedLabels.isEmpty() && labels.isNotEmpty()) selectedLabels.add(labels.first())
                    refreshLabelButton()
                    categoryButton.isEnabled = labels.isNotEmpty()
                    if (labels.isEmpty()) categoryButton.text = "لا توجد Labels في المقالات المنشورة"
                }
            } catch (e: Exception) {
                postToUi { categoryButton.text = "تعذر تحميل Labels • حاول التحديث"; categoryButton.isEnabled = true }
            }
        }
        box.addView(genre); box.addView(releaseYear); box.addView(title)
        box.addView(makeText("03 • محتوى المقال", 12f, red, true).apply { setPadding(0, dp(12), 0, dp(5)) })
        box.addView(body, LinearLayout.LayoutParams(-1, dp(190))); box.addView(coverUrl); box.addView(link)
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false; setPadding(0, dp(4), 0, dp(8)); addView(box) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "إنشاء مقال جديد" else "تعديل المقال").setView(scroll).setNegativeButton("إغلاق", null).setNeutralButton("معاينة", null).setPositiveButton(if (existing == null) "نشر المقال" else "حفظ التعديلات", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val previewTitle = title.text.toString().trim().ifBlank { "معاينة المقال" }
                val previewHtml = "<html><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><body style=\"background:${if (isDarkMode()) "#0a0b11" else "#f5f7fb"};color:${if (isDarkMode()) "#f4f4f8" else "#202638"};padding:12px;\">" +
                    BloggerArticleBuilder.build(previewTitle, selectedLabels.joinToString("، "), genre.text.toString(), releaseYear.text.toString(), body.text.toString(), coverUrl.text.toString(), link.text.toString()) +
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
                val publishedCoverLink = BloggerFeedParser.normalizeBloggerImageUrl(coverLink)
                val htmlContent = BloggerArticleBuilder.build(postTitle, selectedLabels.joinToString("، "), genre.text.toString().trim(), releaseYear.text.toString().trim(), contentText, publishedCoverLink, relatedLink)
                val post = Game(name = postTitle, platform = categoryName, description = contentText, url = relatedLink.ifBlank { existing?.url ?: "" }, coverUrl = publishedCoverLink, coverLocalPath = if (coverLink.isBlank() || coverLink == pendingCoverPublicUrl || (existing != null && coverLink == existing.coverUrl)) pendingCoverLocalPath else "", sourcePostUrl = existing?.sourcePostUrl ?: "", downloadLinks = existing?.downloadLinks ?: "", blogImported = true, isFavorite = existing?.isFavorite ?: false, bloggerPostId = existing?.bloggerPostId ?: "", bloggerHtml = htmlContent, playStatus = existing?.playStatus ?: "", genre = genre.text.toString().trim(), releaseYear = releaseYear.text.toString().trim())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                ioExecutor.execute {
                    try {
                        val result = bloggerPostRepository.savePost(
                            post,
                            selectedLabels.toList(),
                            existing?.bloggerPostId
                        )
                        postToUi {
                            val saved = post.copy(bloggerPostId = result.optString("id", post.bloggerPostId), bloggerHtml = result.optString("content", htmlContent), url = result.optString("url", post.url), sourcePostUrl = result.optString("url", post.sourcePostUrl), blogImported = true)
                            val previousCoverPath = existing?.coverLocalPath.orEmpty()
                            gameLibraryStore.upsert(saved, existing)
                            deleteCoverFileIfUnused(previousCoverPath)
                            pendingCoverLocalPath = ""; pendingCoverPublicUrl = ""; coverUploadFailed = false
                            saveGames(); renderGames(); dialog.dismiss(); Toast.makeText(this, "تم نشر المقال على Blogger", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) { postToUi { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true; Toast.makeText(this, e.message ?: "فشل نشر المقال", Toast.LENGTH_LONG).show() } }
                }
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
                ioExecutor.execute { try {
                    bloggerPostRepository.deletePost(game.bloggerPostId)
                    postToUi {
                        if (gameLibraryStore.remove(game)) {
                            deleteCoverFileIfUnused(game.coverLocalPath)
                        }
                        renderGames()
                        Toast.makeText(this, "تم الحذف من Blogger", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { postToUi { Toast.makeText(this, e.message ?: "تعذر حذف المقال من Blogger", Toast.LENGTH_LONG).show() } } }
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

    private fun enqueueGameDownload(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = ""
    ) {
        downloadUiController.enqueueGameDownload(
            link, title, userAgent, contentDisposition, hintedMimeType, referrer
        )
    }

    private fun showDownloadManager() {
        downloadUiController.showDownloadManager()
    }

    private fun openGameLink(link: String) {
        downloadUiController.openGameLink(link)
    }

    /**
     * Deletes an app-managed cover only after no library entry references it.
     * Restrict deletion to the private covers directory to avoid touching
     * arbitrary paths restored from older or external backup files.
     */
    private fun deleteCoverFileIfUnused(path: String) {
        if (path.isBlank()) return

        val coversDirectory = File(filesDir, "covers").canonicalFile
        val coverFile = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        if (coverFile.parentFile != coversDirectory || !coverFile.isFile) return

        val stillReferenced = games.any { game ->
            game.coverLocalPath.isNotBlank() &&
                runCatching { File(game.coverLocalPath).canonicalFile == coverFile }.getOrDefault(false)
        }
        if (!stillReferenced) coverFile.delete()
    }

    private fun saveGames() {
        gameRepository.save(games)
    }

    private fun loadGames() {
        games.clear()
        try {
            val savedGames = gameRepository.load()
            if (savedGames != null) {
                games.addAll(savedGames)
                return
            }
            loadOfflineCatalog()
        } catch (_: Exception) {
            // Recover the bundled starter catalog if persisted data is malformed.
            games.clear()
            try {
                loadOfflineCatalog()
            } catch (_: Exception) {
                games.clear()
            }
        }
    }

    private fun loadOfflineCatalog() {
        val offlineCatalog = assets.open("offline_catalog.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val entries = JSONArray(offlineCatalog)
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            BloggerFeedParser.parseBlogEntry(entry)?.let { games.add(it) }
        }
        if (games.isNotEmpty()) saveGames()
    }


}