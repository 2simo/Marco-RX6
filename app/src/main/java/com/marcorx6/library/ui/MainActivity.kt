
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
import com.marcorx6.library.data.DownloadRepository
import com.marcorx6.library.network.BloggerFeedClient
import com.marcorx6.library.network.BloggerArticleBuilder
import com.marcorx6.library.notification.BlogNotificationScheduler
import com.marcorx6.library.network.BloggerFeedParser
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
    private val driveServices by lazy {
        DriveServices(
            filesDir = filesDir,
            contentResolver = contentResolver,
            accessToken = ::driveToken,
            executeIo = { task -> ioExecutor.execute { task() } },
            postToUi = ::postToUi
        )
    }
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
    private val adminSessionState = AdminSessionState()
    private var bloggerBlogId: String
        get() = adminSessionState.blogId()
        set(value) = adminSessionState.setBlogId(value)
    private var isAdminSession: Boolean
        get() = adminSessionState.isAuthorized(
            accountId = GoogleSignIn.getLastSignedInAccount(this)?.id
        )
        set(value) {
            if (value) {
                adminSessionState.grant(
                    accountId = GoogleSignIn.getLastSignedInAccount(this)?.id,
                    blogId = bloggerBlogId
                )
            } else {
                adminSessionState.clear()
            }
        }
    private val googleAccountController by lazy { GoogleAccountController(this) }
    private val googleTokenProvider by lazy { GoogleTokenProvider(this, adminSessionState) }
    private val bloggerServices by lazy {
        BloggerServices(
            tokenProvider = googleTokenProvider,
            blogIdProvider = { bloggerBlogId },
            isAdminSession = { isAdminSession }
        )
    }
    private val gameRepository by lazy { GameRepository(applicationContext) }
    private val profileFeature by lazy {
        ProfileFeature(
            activity = this,
            contentResolver = contentResolver,
            filesDir = filesDir,
            executor = ioExecutor,
            postToUi = ::postToUi,
            preferences = ProfilePreferences.open(this),
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
            styleAlertDialog = ::styleAlertDialog,
            applyUnifiedSkin = ::applyUnifiedSkin,
            onAvatarSaved = {
                Toast.makeText(this, "تم تحديث صورة الملف الشخصي", Toast.LENGTH_SHORT).show()
                showProfile()
            },
            onAvatarError = { message ->
                Toast.makeText(this, "تعذر حفظ الصورة: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    private val bloggerAccessController by lazy {
        bloggerServices.createAccessController(
            executor = ioExecutor,
            currentAccountId = { GoogleSignIn.getLastSignedInAccount(this)?.id },
            currentBlogId = { bloggerBlogId },
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
    private fun startGoogleSignIn() = googleSignInLauncher.launch(googleAccountController.signInIntent())
    private fun signOutAdmin() {
        googleAccountController.signOut {
            isAdminSession = false
            createInterface()
        }
    }
    private fun driveToken(): String = googleTokenProvider.driveToken()


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
            showSideMenu = ::showSideMenu,
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

    private val coverUploadController by lazy {
        driveServices.createCoverUploadController()
    }

    private val coverSelectionController: CoverSelectionController by lazy {
        CoverSelectionController(
            context = this,
            uploadController = coverUploadController,
            pendingLocalPath = { pendingCoverLocalPath },
            setPendingLocalPath = { pendingCoverLocalPath = it },
            setPendingPublicUrl = { pendingCoverPublicUrl = it },
            setUploadInProgress = { coverUploadInProgress = it },
            setUploadFailed = { coverUploadFailed = it },
            coverUrlField = { coverUrlField },
            coverPreview = { coverPreview },
            loadCover = ::loadCover,
            deleteCoverFileIfUnused = ::deleteCoverFileIfUnused,
            dp = ::dp,
            white = { white },
            styleDialog = ::styleAlertDialog
        )
    }

    private val activityResultController: MainActivityResultController by lazy {
        MainActivityResultController(
            signInRequestCode = SIGN_IN_REQUEST,
            avatarRequestCode = 1005,
            coverRequestCode = PICK_IMAGE_REQUEST,
            onGoogleSignInResult = { data ->
                googleAccountController.handleSignInResult(
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
            },
            onAvatarSelected = profileFeature.avatarController::handle,
            onCoverSelected = coverSelectionController::handle
        )
    }

    private fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) = activityResultController.handle(requestCode, resultCode, data)

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


    private fun showProfile() = profileFeature.show()
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
            savePost = bloggerServices.posts::savePost
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
            fetchLabels = bloggerServices.posts::fetchLabels,
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
    private val bloggerPostComposerController by lazy {
        BloggerPostComposerController(
            activity = this,
            isAdminSession = { isAdminSession },
            dp = ::dp,
            bg = { bg },
            panel = { panel },
            white = { white },
            gray = { gray },
            red = { red },
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            rounded = ::rounded,
            accentForeground = ::accentForeground,
            labelsController = gamePostLabelsController,
            formFieldsFactory = bloggerPostFormFieldsFactory,
            setCoverFields = { url, preview ->
                coverUrlField = url
                coverPreview = preview
            },
            pendingCoverLocalPath = { pendingCoverLocalPath },
            setPendingCoverLocalPath = { pendingCoverLocalPath = it },
            pendingCoverPublicUrl = { pendingCoverPublicUrl },
            setPendingCoverPublicUrl = { pendingCoverPublicUrl = it },
            coverUploadInProgress = { coverUploadInProgress },
            loadCover = ::loadCover,
            chooseCover = ::chooseCover,
            previewFactory = bloggerArticlePreviewFactory,
            styleAlertDialog = ::styleAlertDialog,
            postFactory = bloggerGamePostFactory,
            saveController = bloggerPostSaveController,
            upsertGame = { saved, existing -> gameLibraryStore.upsert(saved, existing) },
            deleteCoverFileIfUnused = ::deleteCoverFileIfUnused,
            resetCoverUploadFailed = { coverUploadFailed = false },
            saveGames = ::saveGames,
            renderGames = ::renderGames,
            applyUnifiedSkin = ::applyUnifiedSkin
        )
    }

    private fun showGameDialog(existing: Game? = null) =
        bloggerPostComposerController.show(existing)

    private val gameDeletionController by lazy {
        GameDeletionController(
            activity = this,
            isAdminSession = { isAdminSession },
            bloggerBlogId = { bloggerBlogId },
            deletePost = bloggerServices.posts::deletePost,
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