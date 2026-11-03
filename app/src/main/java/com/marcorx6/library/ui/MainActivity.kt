
package com.marcorx6.library.ui

import android.Manifest
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.marcorx6.library.R
import com.marcorx6.library.notification.BlogNotificationWorker
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
        mainLibraryInterfaceController.dispose()
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

    private fun tr(value: String): String =
        LanguageTranslator.translate(value, currentLanguage())

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

    private fun showFirstRunSetup() = firstRunSetupController.show()


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

    private val firstRunSetupCompletionController by lazy {
        FirstRunSetupCompletionController(
            settings = getSharedPreferences("app_settings", MODE_PRIVATE),
            appearance = appearancePreferences(),
            setNotificationsEnabled = notificationScheduler::setEnabled,
            scheduleNotifications = notificationScheduler::schedule,
            cancelNotifications = notificationScheduler::cancel,
            requestNotificationPermission = ::requestNotificationPermissionIfNeeded,
            onPlatformSelected = { selected = it }
        )
    }

    private val firstRunSetupController by lazy {
        FirstRunSetupController(
            activity = this,
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            appearancePreferences = ::appearancePreferences,
            currentLanguage = ::currentLanguage,
            isDarkMode = ::isDarkMode,
            isNotificationsEnabled = notificationScheduler::isEnabled,
            viewFactory = firstRunSetupViewFactory,
            completionController = firstRunSetupCompletionController,
            applyUnifiedSkin = ::applyUnifiedSkin,
            getSetupDialog = { setupDialog },
            setSetupDialog = { setupDialog = it },
            recreateActivity = ::recreate
        )
    }

    private val firstRunSetupViewFactory by lazy {
        FirstRunSetupViewFactory(
            activity = this,
            dp = ::dp,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) }
        )
    }

    private val bloggerPostFormFieldsFactory by lazy {
        BloggerPostFormFieldsFactory(
            activity = this,
            dp = ::dp,
            makeInput = ::makeInput,
            rounded = ::rounded,
            panel = { panel }
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

    private val gameDetailsBottomBarFactory by lazy {
        GameDetailsBottomBarFactory(
            activity = this,
            dp = ::dp,
            rounded = ::rounded,
            isDarkMode = ::isDarkMode,
            white = { white },
            panel = { panel },
            translate = ::tr
        )
    }

    private val gameDetailsTopBarFactory by lazy {
        GameDetailsTopBarFactory(
            activity = this,
            dp = ::dp,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            isDarkMode = ::isDarkMode,
            white = { white },
            red = { red },
            favoriteToggleFactory = gameFavoriteToggleFactory
        )
    }

    private val gameDetailsIdentityViewFactory by lazy {
        GameDetailsIdentityViewFactory(
            activity = this,
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            isDarkMode = ::isDarkMode,
            white = { white },
            gray = { gray },
            loadCover = ::loadCover,
            watchlistViewFactory = gameWatchlistViewFactory
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

    private val gameDetailsController by lazy {
        GameDetailsController(
            activity = this,
            games = { games },
            bg = { bg },
            dp = ::dp,
            isDarkMode = ::isDarkMode,
            fontFamilyName = ::fontFamilyName,
            fontScale = ::fontScale,
            enqueueGameDownload = ::enqueueGameDownload,
            topBarFactory = gameDetailsTopBarFactory,
            identityViewFactory = gameDetailsIdentityViewFactory,
            similarGamesViewFactory = gameSimilarGamesViewFactory,
            articleWebViewFactory = gameArticleWebViewFactory,
            downloadLinksViewFactory = gameDownloadLinksViewFactory,
            bottomBarFactory = gameDetailsBottomBarFactory,
            offlineArchiveController = gameOfflineArchiveController,
            filesDir = filesDir,
            stableCacheName = coverRepository::stableCacheName,
            isAdmin = { isAdminSession },
            postToUi = ::postToUi,
            openGameLink = ::openGameLink,
            showGameDialog = { showGameDialog(it) },
            deleteGame = ::deleteGame,
            applyMotionPreferences = ::applyMotionPreferences,
            applyUnifiedSkin = ::applyUnifiedSkin,
            rounded = ::rounded
        )
    }

    private fun showDetails(game: Game) = gameDetailsController.show(game)


    // BLOG POST COMPOSER: reusable article template published directly to Blogger
    private fun showGameDialog(existing: Game? = null) {
        if (!isAdminSession) { Toast.makeText(this, "يجب تسجيل الدخول بصلاحية المسؤول للنشر.", Toast.LENGTH_SHORT).show(); return }
        pendingCoverLocalPath = existing?.coverLocalPath ?: pendingCoverLocalPath
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(16)); background = rounded(bg, 18) }
        val intro = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(panel, 15) }
        intro.addView(makeText(if (existing == null) "استوديو النشر • MARCO RX6" else "استوديو التحرير • MARCO RX6", 17f, white, true))
        intro.addView(makeText("جهّز الغلاف والتفاصيل والمحتوى، ثم عاين المقال قبل إرساله إلى Blogger.", 11f, gray).apply { setPadding(0, dp(5), 0, 0) })
        box.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val labelsUi = gamePostLabelsController.create(
            initialLabel = existing?.platform,
            isNewPost = existing == null
        )
        val categoryButton = labelsUi.button
        val selectedLabels = labelsUi.selectedLabels
        val formFields = bloggerPostFormFieldsFactory.create()
        val title = formFields.title
        val body = formFields.body
        val coverUrl = formFields.coverUrl
        val link = formFields.relatedLink
        val genre = formFields.genre
        val releaseYear = formFields.releaseYear
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
        val preview = formFields.coverPreview
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
                val postTitle = title.text.toString().trim(); val coverLink = coverUrl.text.toString().trim(); val relatedLink = link.text.toString().trim()
                val contentText = body.text.toString().trim()
                val validationFailure = BloggerPostInputValidator.validate(
                    coverUploadInProgress = coverUploadInProgress,
                    pendingCoverLocalPath = pendingCoverLocalPath,
                    selectedLabels = selectedLabels,
                    title = postTitle,
                    coverUrl = coverLink,
                    relatedLink = relatedLink,
                    content = contentText
                )
                if (validationFailure != null) {
                    when (validationFailure.field) {
                        BloggerPostInputValidator.Field.TITLE -> title.error = validationFailure.message
                        BloggerPostInputValidator.Field.COVER_URL -> coverUrl.error = validationFailure.message
                        BloggerPostInputValidator.Field.RELATED_LINK -> link.error = validationFailure.message
                        BloggerPostInputValidator.Field.BODY -> body.error = validationFailure.message
                        null -> Toast.makeText(
                            this,
                            validationFailure.message,
                            if (validationFailure.longMessage) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@setOnClickListener
                }
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