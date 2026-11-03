
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

    private fun appearancePreferences() = AppearancePreferencesStore.appearance(this)
    private fun isDarkMode() = AppearancePreferencesStore.isDarkMode(appearancePreferences())

    private val systemBarAppearanceController by lazy {
        SystemBarAppearanceController(this, ::isDarkMode)
    }

    private fun applySystemBarAppearance() = systemBarAppearanceController.apply()
    private fun accentColor(): Int = AccentPalette.color(appearancePreferences().getString("accent", "blue"))
    private val bg: Int get() = if (isDarkMode()) Color.rgb(7, 13, 25) else Color.rgb(245, 247, 251)
    private val panel: Int get() = if (isDarkMode()) Color.rgb(16, 27, 45) else Color.rgb(229, 234, 243)
    private val red: Int get() = accentColor()
    private val white: Int get() = if (isDarkMode()) Color.WHITE else Color.rgb(24, 29, 42)
    private fun accentForeground(): Int = AccentPalette.foreground()
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
    private val uiThreadDispatcher by lazy { UiThreadDispatcher(this) }
    private fun postToUi(action: () -> Unit) = uiThreadDispatcher.post(action)

    private val ioExecutor = Executors.newCachedThreadPool()
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
    private val profileAvatarController by lazy {
        ProfileAvatarController(
            executor = ioExecutor,
            saveAvatar = { uri -> profileAvatarRepository.save(uri).absolutePath },
            persistPath = { path -> profilePreferences().edit().putString("avatar_path", path).apply() },
            postToUi = ::postToUi,
            onSaved = {
                Toast.makeText(this, "تم تحديث صورة الملف الشخصي", Toast.LENGTH_SHORT).show()
                showProfile()
            },
            onError = { message ->
                Toast.makeText(this, "تعذر حفظ الصورة: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    private val bloggerAccessController by lazy {
        BloggerAccessController(
            executor = ioExecutor,
            checkAccess = bloggerApiClient::hasWriteAccess,
            currentAccountId = { GoogleSignIn.getLastSignedInAccount(this)?.id },
            setAdmin = { isAdminSession = it },
            postToUi = ::postToUi,
            refreshUi = ::createInterface,
            onDenied = {
                Toast.makeText(
                    this,
                    "هذا الحساب لا يملك صلاحية النشر أو التعديل في مدونة Blogger",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private val blogSyncController by lazy {
        BlogSyncController(
            context = this,
            executor = ioExecutor,
            isSyncing = { isSyncing },
            setSyncing = { isSyncing = it },
            fetch = {
                val result = BloggerFeedClient(bloggerFeedUrl).fetch()
                result.blogId to result.games
            },
            postToUi = ::postToUi,
            merge = ::mergeImportedGames,
            setBlogId = { bloggerBlogId = it },
            refreshAccess = ::refreshBloggerAccess,
            renderGames = ::renderGames
        )
    }

    private val gamePersistenceController by lazy {
        GamePersistenceController(gameRepository, games) { loadOfflineCatalog() }
    }
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
        startupExperienceController.dismiss()
        mainHandler.removeCallbacksAndMessages(null)
        setupDialog?.let { if (it.isShowing) it.dismiss() }
        setupDialog = null
        ioExecutor.shutdownNow()
        if (coverRepositoryCreated) coverRepository.close()
        super.onDestroy()
    }

    private fun configureGoogleSignIn() {
        googleSignInClient = GoogleSignInFactory.create(this)
    }
    private fun restoreAdminSession() {
        isAdminSession = false
        refreshBloggerAccess()
    }

    private fun refreshBloggerAccess() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        bloggerAccessController.refresh(
            blogId = bloggerBlogId,
            accountId = account?.id,
            hasAccount = account?.account != null
        )
    }
    private fun startGoogleSignIn() { googleSignInClient?.let { googleSignInLauncher.launch(it.signInIntent) } }
    private fun signOutAdmin() {
        googleSignInClient?.signOut()?.addOnCompleteListener { isAdminSession = false; createInterface() }
    }
    private fun googleBloggerToken(): String = GoogleTokenProvider(this).bloggerToken()

    private fun driveToken(): String = GoogleTokenProvider(this) { isAdminSession }.driveToken()


    private fun bloggerHtml(game: Game): String = BloggerHtmlBuilder.build(game)

    private val notificationPermissionController by lazy {
        NotificationPermissionController(this)
    }

    private fun requestNotificationPermissionIfNeeded() =
        notificationPermissionController.requestIfNeeded()

    private val notificationSettingsController by lazy {
        NotificationSettingsController(this, notificationScheduler, ::styleAlertDialog) {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun showNotificationSettings() = notificationSettingsController.show()

    private val basicUiFactory by lazy { BasicUiFactory(this) }

    private fun dp(v: Int): Int = basicUiFactory.dp(v)

    private fun rounded(color: Int, radius: Int) =
        basicUiFactory.rounded(color, radius)

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = basicUiFactory.makeText(value, size, color, bold)


    private val appearanceUiFactory by lazy {
        AppearanceUiFactory(
            activity = this,
            dp = ::dp,
            isDarkMode = ::isDarkMode,
            bg = { bg },
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray },
            accentForeground = ::accentForeground
        )
    }

    private fun stylePrimaryButton(button: Button, primary: Boolean = false) =
        appearanceUiFactory.stylePrimaryButton(button, primary)

    private fun applyUnifiedSkin(view: View) =
        appearanceUiFactory.applyUnifiedSkin(view)

    private fun styleAlertDialog(dialog: AlertDialog) =
        appearanceUiFactory.styleAlertDialog(dialog)

    private fun themedSpinner(items: List<String>, selectedIndex: Int = 0): Spinner =
        appearanceUiFactory.themedSpinner(items, selectedIndex)

    private fun makeInput(hintText: String): EditText =
        appearanceUiFactory.makeInput(hintText)


    private fun currentLanguage(): String =
        getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", "ar") ?: "ar"


    private val sideMenuController by lazy {
        SideMenuController(
            activity = this,
            dp = ::dp,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            tr = ::tr,
            isDark = ::isDarkMode,
            language = ::currentLanguage,
            gold = red,
            ink = bg,
            surface = panel,
            white = white,
            gray = gray,
            syncing = { isSyncing },
            isAdmin = { isAdminSession },
            sync = { syncFromBlog(showResult = true) },
            showDownloads = ::showDownloadManager,
            showNotifications = ::showNotificationSettings,
            showProfile = ::showProfile,
            signOut = ::signOutAdmin,
            signIn = ::startGoogleSignIn,
            showGameDialog = ::showGameDialog,
            showGameOfDay = ::showGameOfTheDay,
            showLanguage = ::showLanguagePicker,
            showAppearance = ::showAppearanceSettings,
            showTypography = ::showTypographySettings,
            openLink = ::openGameLink,
            applyMotion = ::applyMotionPreferences,
            applySkin = ::applyUnifiedSkin
        )
    }

    private fun showSideMenu() = sideMenuController.show()

    private fun fontPreferences() = AppearancePreferencesStore.typography(this)
    private fun fontScale(): Float = TypographyPreferences.scale(fontPreferences())
    private fun fontFamilyName(): String = TypographyPreferences.family(fontPreferences())
    private fun applyTypography(view: View) {
        TypographyApplier.apply(
            view = view,
            fontFamily = fontFamilyName(),
            fontScale = fontScale(),
            scaledDensity = resources.displayMetrics.scaledDensity
        )
    }
    private val motionPreferencesApplier by lazy {
        MotionPreferencesApplier(::appearancePreferences)
    }

    private fun applyMotionPreferences(view: View) =
        motionPreferencesApplier.apply(view)


    private fun showAppearanceSettings() {
        AppearanceSettingsController(
            activity = this,
            preferences = appearancePreferences(),
            applyMotionPreferences = ::applyMotionPreferences,
            styleDialog = ::styleAlertDialog,
            recreateActivity = ::recreate
        ).show()
    }

    private fun showTypographySettings() {
        TypographySettingsController(
            activity = this,
            preferences = fontPreferences(),
            dp = ::dp,
            makeText = ::makeText,
            panelColor = { panel },
            redColor = { red },
            whiteColor = { white },
            grayColor = { gray },
            roundedBackground = ::rounded,
            themedSpinner = ::themedSpinner,
            recreateInterface = ::createInterface,
            styleDialog = ::styleAlertDialog
        ).show()
    }

    private fun showAdvancedFilters() {
        AdvancedFiltersController(
            activity = this,
            dp = ::dp,
            red = red,
            makeText = ::makeText,
            styleDialog = ::styleAlertDialog,
            onApply = { genre, year ->
                selectedGenre = genre
                selectedYear = year
                renderGames()
            },
            onClear = {
                selectedGenre = ""
                selectedYear = ""
                renderGames()
            }
        ).show(
            genres = games.map { it.genre.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
            years = games.map { it.releaseYear.trim() }.filter { it.isNotBlank() }.distinct().sortedDescending(),
            selectedGenre = selectedGenre,
            selectedYear = selectedYear
        )
    }

    private val mainLibraryInterfaceController by lazy {
        MainLibraryInterfaceController(
            activity = this,
            darkMode = ::isDarkMode,
            currentLanguage = ::currentLanguage,
            dp = ::dp,
            tr = ::tr,
            rounded = ::rounded,
            createText = { value, size, color, bold -> makeText(value, size, color, bold) },
            games = { games },
            bg = bg,
            panel = panel,
            red = red,
            white = white,
            gray = gray,
            adminSession = { isAdminSession },
            getSelected = { selected },
            setSelected = { selected = it },
            getSortMode = { sortMode },
            setSortMode = { sortMode = it },
            setSearch = { search = it },
            setFilters = { filters = it },
            setCountText = { countText = it },
            setGameList = { gameList = it },
            renderGames = ::renderGames,
            showNotificationSettings = ::showNotificationSettings,
            showAppearanceSettings = ::showAppearanceSettings,
            showAdvancedFilters = ::showAdvancedFilters,
            showGameDialog = ::showGameDialog,
            showProfile = ::showProfile,
            applyTypography = ::applyTypography,
            applyUnifiedSkin = ::applyUnifiedSkin,
            applyMotionPreferences = ::applyMotionPreferences
        )
    }

    private fun createInterface() = mainLibraryInterfaceController.create()

    private fun syncFromBlog(showResult: Boolean) = blogSyncController.sync(showResult)

    private fun mergeImportedGames(imported: List<Game>): Pair<Int, Int> =
        GameImportMerger.merge(gameLibraryStore, imported)

    private fun showPlatformCorrection(game: Game) {
        PlatformCorrectionController(this, gameLibraryStore, ::renderGames, ::styleAlertDialog)
            .show(game)
    }

    // IMAGE LOADER
    private val coverDisplayController by lazy {
        CoverDisplayController(
            normalizeUrl = BloggerFeedParser::normalizeBloggerImageUrl,
            resolveKey = ::keyForCover,
            loadBitmap = { localPath, imageUrl, callback ->
                coverRepository.load(localPath, imageUrl, callback)
            }
        )
    }

    private fun loadCover(image: ImageView, localPath: String, imageUrl: String) =
        coverDisplayController.load(image, localPath, imageUrl)

    private fun keyForCover(localPath: String, imageUrl: String): String =
        CoverKeyResolver.resolve(localPath, imageUrl, coverRepository::stableCacheName) { asset ->
            try { assets.open(asset).use { true } } catch (_: Exception) { false }
        }

    private fun chooseCover() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        coverPickerLauncher.launch(intent)
    }

    private val googleSignInResultHandler by lazy { GoogleSignInResultHandler() }

    private val coverUploadController by lazy {
        CoverUploadController(
            repository = coverUploadRepository,
            accessToken = ::driveToken,
            executeIo = { task -> ioExecutor.execute { task() } },
            postToUi = ::postToUi
        )
    }

    private fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        if (requestCode == SIGN_IN_REQUEST && resultCode == RESULT_OK) {
            googleSignInResultHandler.handle(
                data = data,
                onSuccess = {
                    isAdminSession = false
                    createInterface()
                    refreshBloggerAccess()
                    Toast.makeText(this, "تم ربط حساب Google؛ جارٍ التحقق من صلاحيات Blogger…", Toast.LENGTH_LONG).show()
                },
                onFailure = { message ->
                    isAdminSession = false
                    createInterface()
                    Toast.makeText(this, "فشل تسجيل الدخول إلى Google: $message", Toast.LENGTH_LONG).show()
                }
            )
            return
        }
        if (requestCode == 1005 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            profileAvatarController.handle(uri)
            return
        }
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != RESULT_OK) return

        val uri: Uri = data?.data ?: return
        coverUploadInProgress = true
        coverUploadFailed = false
        pendingCoverPublicUrl = ""
        coverUrlField?.setText("")

        coverUploadController.upload(
            uri = uri,
            onLocalFileSaved = { localFile ->
                val previousPendingPath = pendingCoverLocalPath
                pendingCoverLocalPath = localFile.absolutePath
                deleteCoverFileIfUnused(previousPendingPath)
                coverUrlField?.setText("")
                coverPreview?.let { loadCover(it, pendingCoverLocalPath, "") }
                Toast.makeText(this, "تم اختيار الصورة، جارٍ رفعها للموقع...", Toast.LENGTH_SHORT).show()
            },
            onSuccess = { uploadResult ->
                pendingCoverLocalPath = uploadResult.localFile.absolutePath
                pendingCoverPublicUrl = uploadResult.publicUrl
                coverUploadInProgress = false
                coverUploadFailed = false
                coverUrlField?.setText(uploadResult.publicUrl)
                coverPreview?.let { loadCover(it, pendingCoverLocalPath, uploadResult.publicUrl) }
                Toast.makeText(this, "تم رفع الغلاف بنجاح وأصبح جاهزاً للنشر", Toast.LENGTH_LONG).show()
            },
            onFailure = { error ->
                coverUploadInProgress = false
                coverUploadFailed = true
                val details = (error.message ?: error.javaClass.simpleName).ifBlank { "خطأ غير معروف" }
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
        )
    }


    private val startupExperienceController by lazy {
        StartupExperienceController(
            activity = this,
            dp = ::dp,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            showFirstRunSetup = ::showFirstRunSetup,
            showProfile = ::showProfile
        )
    }

    private fun showStartupExperience() = startupExperienceController.show()

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
                val card = firstRunSetupViewFactory.optionCard(label, selectedNow, detail)
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
                    val card = firstRunSetupViewFactory.optionCard(label, accentKey == key)
                    val dot = View(this).apply { background = rounded(swatchColor, 20) }
                    card.addView(dot, LinearLayout.LayoutParams(dp(24), dp(24)).apply { topMargin = dp(9) })
                    choices.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
                    card.setOnClickListener { accentKey = key; renderStep(step, false) }
                    if (animate) firstRunSetupViewFactory.animateIn(card)
                }
                4 -> {
                    addChoice("تفعيل الإشعارات", "تنبيه عند وصول مقال جديد", notificationsEnabled) { notificationsEnabled = true }
                    addChoice("إيقاف الإشعارات", "يمكنك تفعيلها لاحقاً من الإعدادات", !notificationsEnabled) { notificationsEnabled = false }
                }
                5 -> {
                    val readyCard = firstRunSetupViewFactory.optionCard("مكتبتك جاهزة!", true, "تم حفظ المنصة واللغة والمظهر واللون والإشعارات.")
                    choices.addView(readyCard, LinearLayout.LayoutParams(-1, -2))
                    if (animate) firstRunSetupViewFactory.animateIn(readyCard)
                }
            }
            val scroll = ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(choices)
            }
            page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(page, FrameLayout.LayoutParams(-1, -1))
            if (animate) firstRunSetupViewFactory.animateIn(page)
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
    private fun profilePreferences() = ProfilePreferences.open(this)
    private val profileController by lazy {
        ProfileController(
            activity = this,
            preferences = profilePreferences(),
            games = { games },
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            makeInput = ::makeInput,
            tr = ::tr,
            isDarkMode = ::isDarkMode,
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray },
            showLanguagePicker = ::showLanguagePicker,
            showNotificationSettings = ::showNotificationSettings,
            chooseAvatar = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                profileAvatarLauncher.launch(intent)
            },
            getActiveDialog = { activeProfileDialog },
            setActiveDialog = { activeProfileDialog = it },
            styleAlertDialog = ::styleAlertDialog,
            applyUnifiedSkin = ::applyUnifiedSkin
        )
    }

    private fun showProfile() = profileController.show()
    private val gameLibraryRenderer by lazy {
        GameLibraryRenderer(
            activity = this,
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            isDarkMode = ::isDarkMode,
            accentForeground = ::accentForeground,
            tr = ::tr,
            loadCover = ::loadCover,
            showDetails = ::showDetails
        )
    }

    private fun renderGames() {
        gameLibraryRenderer.render(
            gameList = gameList,
            filters = filters,
            countText = countText,
            search = search,
            games = games,
            selected = selected,
            selectedGenre = selectedGenre,
            selectedYear = selectedYear,
            sortMode = sortMode,
            bg = bg,
            panel = panel,
            red = red,
            white = white,
            gray = gray
        )
    }

    // Dedicated immersive article reader; the WebView owns its scroll instead of nesting inside ScrollView.
    private val gameArticleWebViewFactory by lazy { GameArticleWebViewFactory(this) }

    private val gameOfflineArchiveController by lazy { GameOfflineArchiveController(this) }

    private val bloggerGamePostFactory by lazy { BloggerGamePostFactory() }

    private val firstRunSetupViewFactory by lazy {
        FirstRunSetupViewFactory(
            activity = this,
            dp = ::dp,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) }
        )
    }

    private val bloggerPostSaveController by lazy {
        BloggerPostSaveController(
            executeIo = { task -> ioExecutor.execute { task() } },
            postToUi = ::postToUi,
            savePost = bloggerPostRepository::savePost
        )
    }

    private val bloggerArticlePreviewFactory by lazy {
        BloggerArticlePreviewFactory(
            activity = this,
            backgroundColor = { bg },
            isDarkMode = ::isDarkMode
        )
    }

    private val gameFavoriteToggleFactory by lazy {
        GameFavoriteToggleFactory(
            activity = this,
            games = games,
            dp = ::dp,
            white = { white },
            red = { red },
            saveGames = ::saveGames,
            renderGames = ::renderGames
        )
    }

    private val gameWatchlistViewFactory by lazy {
        GameWatchlistViewFactory(
            activity = this,
            games = games,
            dp = ::dp,
            rounded = ::rounded,
            white = { white },
            panel = { panel },
            saveGames = ::saveGames,
            renderGames = ::renderGames,
            styleAlertDialog = ::styleAlertDialog
        )
    }

    private val gamePostLabelsController by lazy {
        GamePostLabelsController(
            activity = this,
            white = { white },
            panel = { panel },
            rounded = ::rounded,
            fetchLabels = bloggerPostRepository::fetchLabels,
            executeIo = { task -> ioExecutor.execute { task() } },
            postToUi = ::postToUi,
            styleAlertDialog = ::styleAlertDialog
        )
    }

    private val gameDownloadLinksViewFactory by lazy {
        GameDownloadLinksViewFactory(
            activity = this,
            dp = ::dp,
            rounded = ::rounded,
            panel = { panel },
            red = { red },
            white = { white },
            enqueueDownload = { url, label -> enqueueGameDownload(url, label) }
        )
    }

    private val gameSimilarGamesViewFactory by lazy {
        GameSimilarGamesViewFactory(
            activity = this,
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            isDarkMode = ::isDarkMode,
            white = { white }
        )
    }

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
        val favorite = gameFavoriteToggleFactory.create(game)
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
        val watchlistButton = gameWatchlistViewFactory.create(game)
        info.addView(watchlistButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        identity.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(identity, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(12), dp(14), dp(12)) })

        gameSimilarGamesViewFactory.create(game, games) { item ->
            reader.dismiss()
            showDetails(item)
        }?.let(root::addView)

        val articleWebView = gameArticleWebViewFactory.create(
            backgroundColor = bg,
            gameUrl = game.url,
            gameName = game.name,
            bloggerHtml = game.bloggerHtml.ifBlank { "<h2>${game.name}</h2><p>${game.description}</p>" },
            darkMode = isDarkMode(),
            fontFamily = fontFamilyName(),
            fontSize = (17 * fontScale()).toInt(),
            onDownload = { url, userAgent, contentDisposition, mimeType, pageUrl ->
                enqueueGameDownload(url, game.name, userAgent, contentDisposition, mimeType, pageUrl)
            }
        )
        root.addView(articleWebView, LinearLayout.LayoutParams(-1, 0, 1f))
        gameDownloadLinksViewFactory.create(game.downloadLinks, game.name)?.let(root::addView)

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
        val offline = readerAction(gameOfflineArchiveController.buttonLabel(archiveFile)) {
            gameOfflineArchiveController.openOrSave(
                webView = articleWebView,
                archiveFile = archiveFile,
                postToUi = ::postToUi
            )
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
        val labelsUi = gamePostLabelsController.create(
            initialLabel = existing?.platform,
            isNewPost = existing == null
        )
        val categoryButton = labelsUi.button
        val selectedLabels = labelsUi.selectedLabels
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
        box.addView(genre); box.addView(releaseYear); box.addView(title)
        box.addView(makeText("03 • محتوى المقال", 12f, red, true).apply { setPadding(0, dp(12), 0, dp(5)) })
        box.addView(body, LinearLayout.LayoutParams(-1, dp(190))); box.addView(coverUrl); box.addView(link)
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false; setPadding(0, dp(4), 0, dp(8)); addView(box) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "إنشاء مقال جديد" else "تعديل المقال").setView(scroll).setNegativeButton("إغلاق", null).setNeutralButton("معاينة", null).setPositiveButton(if (existing == null) "نشر المقال" else "حفظ التعديلات", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val web = bloggerArticlePreviewFactory.create(
                    title = title.text.toString(),
                    labels = selectedLabels.joinToString("، "),
                    genre = genre.text.toString(),
                    releaseYear = releaseYear.text.toString(),
                    body = body.text.toString(),
                    coverUrl = coverUrl.text.toString(),
                    relatedLink = link.text.toString()
                )
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
                val post = bloggerGamePostFactory.create(
                    existing = existing,
                    title = postTitle,
                    labels = selectedLabels.toList(),
                    description = contentText,
                    coverLink = coverLink,
                    relatedLink = relatedLink,
                    genre = genre.text.toString(),
                    releaseYear = releaseYear.text.toString(),
                    pendingCoverLocalPath = pendingCoverLocalPath,
                    pendingCoverPublicUrl = pendingCoverPublicUrl
                )
                val htmlContent = post.bloggerHtml
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                bloggerPostSaveController.save(
                    post = post,
                    labels = selectedLabels.toList(),
                    existing = existing,
                    onSuccess = { saved ->
                        val previousCoverPath = existing?.coverLocalPath.orEmpty()
                        gameLibraryStore.upsert(saved, existing)
                        deleteCoverFileIfUnused(previousCoverPath)
                        pendingCoverLocalPath = ""; pendingCoverPublicUrl = ""; coverUploadFailed = false
                        saveGames(); renderGames(); dialog.dismiss()
                        Toast.makeText(this, "تم نشر المقال على Blogger", Toast.LENGTH_LONG).show()
                    },
                    onError = { message ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                )
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
    private val gameDeletionController by lazy {
        GameDeletionController(
            activity = this,
            isAdminSession = { isAdminSession },
            bloggerBlogId = { bloggerBlogId },
            deletePost = bloggerPostRepository::deletePost,
            removeGame = gameLibraryStore::remove,
            deleteCoverFileIfUnused = ::deleteCoverFileIfUnused,
            renderGames = ::renderGames,
            executeIo = { task -> ioExecutor.execute { task() } },
            postToUi = ::postToUi,
            styleAlertDialog = ::styleAlertDialog
        )
    }

    private fun deleteGame(game: Game) = gameDeletionController.delete(game)

    private val gameOfTheDayController by lazy {
        GameOfTheDayController(
            activity = this,
            games = { games },
            dp = ::dp,
            rounded = ::rounded,
            panel = { panel },
            white = { white },
            red = { red },
            gray = { gray },
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            loadCover = ::loadCover,
            showDetails = ::showDetails,
            openGameLink = ::openGameLink,
            styleAlertDialog = ::styleAlertDialog
        )
    }

    private fun showGameOfTheDay() = gameOfTheDayController.show()

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
        CoverFileCleanup.deleteIfUnused(File(filesDir, "covers"), path, games.map { it.coverLocalPath })
    }

    private fun saveGames() = gamePersistenceController.save()

    private fun loadGames() = gamePersistenceController.load()

    private fun loadOfflineCatalog() {
        OfflineCatalogLoader.load(this, games) { saveGames() }
    }


}