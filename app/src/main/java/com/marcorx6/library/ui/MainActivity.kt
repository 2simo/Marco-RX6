
package com.marcorx6.library.ui

import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameRepository
import com.marcorx6.library.data.GameLibraryStore
import com.marcorx6.library.network.BloggerFeedParser
import android.app.AlertDialog
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import java.io.File
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private val downloadFeature by lazy {
        DownloadFeature(this, ::styleAlertDialog)
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
    private val coverRepository by lazy { com.marcorx6.library.data.CoverRepository(applicationContext).also { coverRepositoryCreated = true } }
    private var coverRepositoryCreated = false

    private lateinit var gameList: LinearLayout
    private lateinit var search: EditText
    private lateinit var filters: LinearLayout
    private lateinit var countText: TextView

    private var selected = "ALL"
    private var sortMode = "الأحدث"
    private val libraryFilterState = LibraryFilterState()
    private val uiThreadDispatcher by lazy { UiThreadDispatcher(this) }
    private fun postToUi(action: () -> Unit) = uiThreadDispatcher.post(action)

    private val ioExecutor = Executors.newCachedThreadPool()
    private var searchRenderRunnable: Runnable? = null
    private val PICK_IMAGE_REQUEST = 1001
    private val coverFeature by lazy {
        CoverFeature(
            context = this,
            filesDir = filesDir,
            contentResolver = contentResolver,
            accessToken = ::driveToken,
            executeIo = { task -> ioExecutor.execute { task() } },
            postToUi = ::postToUi,
            coverUrlField = { coverFeature.state.urlField },
            coverPreview = { coverFeature.state.preview },
            loadCover = ::loadCover,
            deleteCoverFileIfUnused = ::deleteCoverFileIfUnused,
            dp = ::dp,
            white = { white },
            styleDialog = ::styleAlertDialog
        )
    }
    private var isSyncing = false
    private val googleAdminFeature by lazy { GoogleAdminFeature(this) }
    private var bloggerBlogId: String
        get() = googleAdminFeature.blogId
        set(value) { googleAdminFeature.blogId = value }
    private var isAdminSession: Boolean
        get() = googleAdminFeature.isAdminSession
        set(value) { googleAdminFeature.isAdminSession = value }
    private val bloggerServices by lazy {
        BloggerServices(
            tokenProvider = googleAdminFeature.tokenProvider,
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

    private val bloggerAccessFeature by lazy {
        BloggerAccessFeature(
            context = this,
            services = bloggerServices,
            executor = ioExecutor,
            currentAccount = { GoogleSignIn.getLastSignedInAccount(this) },
            currentBlogId = { bloggerBlogId },
            setAdmin = { isAdminSession = it },
            postToUi = ::postToUi,
            refreshUi = ::createInterface
        )
    }

    private val blogSyncFeature by lazy {
        BlogSyncFeature(
            context = this,
            executor = ioExecutor,
            isSyncing = { isSyncing },
            setSyncing = { isSyncing = it },
            feedUrl = bloggerFeedUrl,
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

        notificationFeature.createChannel()
        loadGames()
        restoreAdminSession()
        createInterface()
        showStartupExperience()
        // Keep background prompts and sync away from the full-screen first-run setup.
        // They resume on the next activity creation after setup has been completed.
        val setupCompleted = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("setup_completed", false)
        if (setupCompleted) {
            if (notificationFeature.isEnabled()) {
                notificationFeature.schedule()
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
        mainLibraryFeature.dispose()
        firstRunSetupFeature.dismiss()
        ioExecutor.shutdownNow()
        if (coverRepositoryCreated) coverRepository.close()
        super.onDestroy()
    }

    private fun restoreAdminSession() {
        isAdminSession = false
        refreshBloggerAccess()
    }

    private fun refreshBloggerAccess() = bloggerAccessFeature.refresh()
    private fun startGoogleSignIn() = googleSignInLauncher.launch(googleAdminFeature.accountController.signInIntent())
    private fun signOutAdmin() {
        googleAdminFeature.accountController.signOut {
            isAdminSession = false
            createInterface()
        }
    }
    private fun driveToken(): String = googleAdminFeature.driveToken()


    private fun bloggerHtml(game: Game): String = BloggerHtmlBuilder.build(game)

    private val notificationFeature by lazy {
        NotificationsFeature(this, ::styleAlertDialog)
    }

    private fun requestNotificationPermissionIfNeeded() =
        notificationFeature.requestPermissionIfNeeded()

    private fun showNotificationSettings() = notificationFeature.showSettings()

    private val uiToolkitFeature by lazy {
        UiToolkitFeature(
            activity = this,
            isDarkMode = ::isDarkMode,
            bg = { bg },
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray },
            accentForeground = ::accentForeground
        )
    }

    private fun dp(v: Int): Int = uiToolkitFeature.dp(v)
    private fun rounded(color: Int, radius: Int) = uiToolkitFeature.rounded(color, radius)
    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        uiToolkitFeature.makeText(value, size, color, bold)
    private fun stylePrimaryButton(button: Button, primary: Boolean = false) =
        uiToolkitFeature.stylePrimaryButton(button, primary)
    private fun applyUnifiedSkin(view: View) = uiToolkitFeature.applyUnifiedSkin(view)
    private fun styleAlertDialog(dialog: AlertDialog) = uiToolkitFeature.styleAlertDialog(dialog)
    private fun themedSpinner(items: List<String>, selectedIndex: Int = 0): Spinner =
        uiToolkitFeature.themedSpinner(items, selectedIndex)
    private fun makeInput(hintText: String): EditText = uiToolkitFeature.makeInput(hintText)


    private val languageFeature by lazy {
        LanguageFeature(
            activity = this,
            preferences = getSharedPreferences("app_settings", MODE_PRIVATE),
            styleDialog = ::styleAlertDialog,
            onLanguageChanged = ::createInterface
        )
    }

    private fun currentLanguage(): String = languageFeature.currentLanguage()
    private fun tr(value: String): String = languageFeature.translate(value)
    private fun showLanguagePicker() = languageFeature.showPicker()


    private val sideMenuFeature by lazy {
        SideMenuFeature(
            activity = this,
            dp = ::dp,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            translate = ::tr,
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
            showGameDialog = { showGameDialog() },
            showGameOfDay = ::showGameOfTheDay,
            showLanguage = ::showLanguagePicker,
            showAppearance = ::showAppearanceSettings,
            showTypography = ::showTypographySettings,
            openLink = ::openGameLink,
            applyMotion = ::applyMotionPreferences,
            applySkin = ::applyUnifiedSkin
        )
    }

    private fun showSideMenu() = sideMenuFeature.show()

    private val motionPreferencesApplier by lazy {
        MotionPreferencesApplier(::appearancePreferences)
    }

    private fun applyMotionPreferences(view: View) =
        motionPreferencesApplier.apply(view)

    private val appearanceFeature by lazy {
        AppearanceFeature(
            activity = this,
            appearancePreferences = ::appearancePreferences,
            dp = ::dp,
            makeText = ::makeText,
            panelColor = { panel },
            redColor = { red },
            whiteColor = { white },
            grayColor = { gray },
            roundedBackground = ::rounded,
            themedSpinner = ::themedSpinner,
            applyMotionPreferences = ::applyMotionPreferences,
            styleDialog = ::styleAlertDialog,
            recreateActivity = ::recreate,
            recreateInterface = ::createInterface
        )
    }

    private fun applyTypography(view: View) =
        appearanceFeature.applyTypography(view, resources.displayMetrics.scaledDensity)

    private fun fontScale(): Float = appearanceFeature.fontScale()
    private fun fontFamilyName(): String = appearanceFeature.fontFamilyName()
    private fun showAppearanceSettings() = appearanceFeature.showAppearanceSettings()
    private fun showTypographySettings() = appearanceFeature.showTypographySettings()

    private val advancedFiltersFeature by lazy {
        AdvancedFiltersFeature(
            activity = this,
            state = libraryFilterState,
            games = { games },
            dp = ::dp,
            red = { red },
            makeText = ::makeText,
            styleDialog = ::styleAlertDialog,
            renderGames = ::renderGames
        )
    }

    private fun showAdvancedFilters() = advancedFiltersFeature.show()

    private val mainLibraryFeature by lazy {
        MainLibraryFeature(
            activity = this,
            darkMode = ::isDarkMode,
            currentLanguage = ::currentLanguage,
            dp = ::dp,
            translate = ::tr,
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
            showGameDialog = { showGameDialog() },
            showProfile = ::showProfile,
            showSideMenu = ::showSideMenu,
            applyTypography = ::applyTypography,
            applyUnifiedSkin = ::applyUnifiedSkin,
            applyMotionPreferences = ::applyMotionPreferences
        )
    }

    private fun createInterface() = mainLibraryFeature.create()

    private fun syncFromBlog(showResult: Boolean) = blogSyncFeature.sync(showResult)

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

    private val activityResultController: MainActivityResultController by lazy {
        MainActivityResultController(
            signInRequestCode = SIGN_IN_REQUEST,
            avatarRequestCode = 1005,
            coverRequestCode = PICK_IMAGE_REQUEST,
            onGoogleSignInResult = { data ->
                googleAdminFeature.accountController.handleSignInResult(
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
            onCoverSelected = coverFeature.selectionController::handle
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

    private fun showFirstRunSetup() = firstRunSetupFeature.show()


    private fun showProfile() = profileFeature.show()
    private val gameLibraryFeature by lazy {
        GameLibraryFeature(
            activity = this,
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            isDarkMode = ::isDarkMode,
            accentForeground = ::accentForeground,
            translate = ::tr,
            loadCover = ::loadCover,
            showDetails = ::showDetails,
            gameList = { gameList },
            filters = { filters },
            countText = { countText },
            search = { search },
            games = { games },
            selected = { selected },
            selectedGenre = { libraryFilterState.genre },
            selectedYear = { libraryFilterState.year },
            sortMode = { sortMode },
            bg = { bg },
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray }
        )
    }

    private fun renderGames() = gameLibraryFeature.render()

    private val firstRunSetupFeature by lazy {
        FirstRunSetupFeature(
            activity = this,
            settings = getSharedPreferences("app_settings", MODE_PRIVATE),
            appearancePreferences = ::appearancePreferences,
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            currentLanguage = ::currentLanguage,
            isDarkMode = ::isDarkMode,
            isNotificationsEnabled = notificationFeature::isEnabled,
            setNotificationsEnabled = notificationFeature::setEnabled,
            scheduleNotifications = notificationFeature::schedule,
            cancelNotifications = notificationFeature::cancel,
            requestNotificationPermission = ::requestNotificationPermissionIfNeeded,
            onPlatformSelected = { selected = it },
            applyUnifiedSkin = ::applyUnifiedSkin,
            recreateActivity = ::recreate
        )
    }

    private val bloggerPostFeature by lazy {
        BloggerPostFeature(
            activity = this,
            services = bloggerServices,
            gameLibraryStore = gameLibraryStore,
            isAdminSession = { isAdminSession },
            bloggerBlogId = { bloggerBlogId },
            dp = ::dp,
            bg = { bg },
            isDarkMode = ::isDarkMode,
            panel = { panel },
            white = { white },
            gray = { gray },
            red = { red },
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            makeInput = ::makeInput,
            rounded = ::rounded,
            accentForeground = ::accentForeground,
            executeIo = { task -> ioExecutor.execute { task() } },
            postToUi = ::postToUi,
            styleAlertDialog = ::styleAlertDialog,
            applyUnifiedSkin = ::applyUnifiedSkin,
            setCoverFields = { url, preview ->
                coverFeature.state.urlField = url
                coverFeature.state.preview = preview
            },
            pendingCoverLocalPath = { coverFeature.state.pendingLocalPath },
            setPendingCoverLocalPath = { coverFeature.state.pendingLocalPath = it },
            pendingCoverPublicUrl = { coverFeature.state.pendingPublicUrl },
            setPendingCoverPublicUrl = { coverFeature.state.pendingPublicUrl = it },
            coverUploadInProgress = { coverFeature.state.inProgress },
            loadCover = ::loadCover,
            chooseCover = ::chooseCover,
            deleteCoverFileIfUnused = ::deleteCoverFileIfUnused,
            resetCoverUploadFailed = { coverFeature.state.resetFailure() },
            saveGames = ::saveGames,
            renderGames = ::renderGames
        )
    }

    private val gameDetailsFeature by lazy {
        GameDetailsFeature(
            activity = this,
            games = games,
            bg = { bg },
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            isDarkMode = ::isDarkMode,
            fontFamilyName = ::fontFamilyName,
            fontScale = ::fontScale,
            enqueueGameDownload = ::enqueueGameDownload,
            white = { white },
            red = { red },
            gray = { gray },
            panel = { panel },
            tr = ::tr,
            loadCover = ::loadCover,
            saveGames = ::saveGames,
            renderGames = ::renderGames,
            styleAlertDialog = ::styleAlertDialog,
            filesDir = filesDir,
            stableCacheName = coverRepository::stableCacheName,
            isAdmin = { isAdminSession },
            postToUi = ::postToUi,
            openGameLink = ::openGameLink,
            showGameDialog = { showGameDialog(it) },
            deleteGame = ::deleteGame,
            applyMotionPreferences = ::applyMotionPreferences,
            applyUnifiedSkin = ::applyUnifiedSkin
        )
    }

    private fun showDetails(game: Game) = gameDetailsFeature.show(game)


    private fun showGameDialog(existing: Game? = null) =
        bloggerPostFeature.showComposer(existing)

    private fun deleteGame(game: Game) = bloggerPostFeature.delete(game)

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
        downloadFeature.enqueueGameDownload(
            link, title, userAgent, contentDisposition, hintedMimeType, referrer
        )
    }

    private fun showDownloadManager() {
        downloadFeature.showManager()
    }

    private fun openGameLink(link: String) {
        downloadFeature.openGameLink(link)
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