
package com.marcorx6.library.ui.activity

import com.marcorx6.library.ui.ActivityServicesRuntimeFeature
import com.marcorx6.library.ui.AppearanceRuntimeFeature
import com.marcorx6.library.ui.AppearancePaletteRuntimeFeature
import com.marcorx6.library.ui.BloggerRuntimeFeature
import com.marcorx6.library.ui.CoverRuntimeFeature
import com.marcorx6.library.ui.CoverUploadRuntimeFeature
import com.marcorx6.library.ui.GameExperienceRuntimeFeature
import com.marcorx6.library.ui.GameImportMerger
import com.marcorx6.library.ui.GameDataRuntimeFeature
import com.marcorx6.library.ui.GoogleAccountRuntimeFeature
import com.marcorx6.library.ui.MainLibraryRuntimeFeature
import com.marcorx6.library.ui.UserExperienceRuntimeFeature
import androidx.activity.ComponentActivity
import com.marcorx6.library.data.Game
import android.app.AlertDialog
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : ComponentActivity() {

    private val runtimeState = MainActivityRuntimeState()

    private val activityServicesRuntimeFeature by lazy {
        ActivityServicesRuntimeFeature(this, ::styleAlertDialog, ::isDarkMode)
    }

    private val appearancePaletteFeature by lazy { AppearancePaletteRuntimeFeature(this) }

    private fun appearancePreferences() = appearancePaletteFeature.preferences()
    private fun isDarkMode() = appearancePaletteFeature.isDarkMode()
    private fun accentColor(): Int = appearancePaletteFeature.accentColor()
    private val bg: Int get() = appearancePaletteFeature.bg
    private val panel: Int get() = appearancePaletteFeature.panel
    private val red: Int get() = appearancePaletteFeature.red
    private val white: Int get() = appearancePaletteFeature.white
    private fun accentForeground(): Int = appearancePaletteFeature.accentForeground()
    private val gray: Int get() = appearancePaletteFeature.gray

    private val games get() = runtimeState.games
    private var coverRuntimeFeatureCreated = false
    private val coverRuntimeFeature by lazy {
        CoverRuntimeFeature(applicationContext, filesDir) { games }
            .also { coverRuntimeFeatureCreated = true }
    }

    private val libraryViewState get() = libraryRuntimeFeature.viewState

    private val PICK_IMAGE_REQUEST = 1001
    private val coverFeature by lazy {
        CoverUploadRuntimeFeature(
            context = this,
            filesDir = filesDir,
            contentResolver = contentResolver,
            accessToken = googleAccountRuntimeFeature::driveToken,
            executeIo = { task -> activityServicesRuntimeFeature.ioExecutor.execute { task() } },
            postToUi = activityServicesRuntimeFeature::postToUi,
            loadCover = coverRuntimeFeature::load,
            deleteCoverFileIfUnused = coverRuntimeFeature::deleteIfUnused,
            dp = ::dp,
            white = { white },
            styleDialog = ::styleAlertDialog
        )
    }

    private var isSyncing: Boolean
        get() = runtimeState.isSyncing
        set(value) { runtimeState.isSyncing = value }
    private val googleAccountRuntimeFeature by lazy {
        GoogleAccountRuntimeFeature(applicationContext)
    }
    private var bloggerBlogId: String
        get() = googleAccountRuntimeFeature.blogId
        set(value) { googleAccountRuntimeFeature.blogId = value }
    private var isAdminSession: Boolean
        get() = googleAccountRuntimeFeature.isAdminSession
        set(value) { googleAccountRuntimeFeature.isAdminSession = value }
    private val bloggerServices get() = googleAccountRuntimeFeature.bloggerServices
    private val gameDataRuntimeFeature by lazy { GameDataRuntimeFeature(this, games) }
    private val userExperienceRuntimeFeature by lazy {
        UserExperienceRuntimeFeature(
            activity = this,
            contentResolver = contentResolver,
            filesDir = filesDir,
            executor = activityServicesRuntimeFeature.ioExecutor,
            postToUi = activityServicesRuntimeFeature::postToUi,
            settings = getSharedPreferences("app_settings", MODE_PRIVATE),
            appearancePreferences = ::appearancePreferences,
            games = { games },
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            makeInput = ::makeInput,
            translate = ::tr,
            currentLanguage = ::currentLanguage,
            isDarkMode = ::isDarkMode,
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray },
            showLanguagePicker = ::showLanguagePicker,
            showNotificationSettings = activityServicesRuntimeFeature::showNotificationSettings,
            chooseAvatar = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                activityResultRuntimeFeature.launchProfileAvatar(intent)
            },
            styleAlertDialog = ::styleAlertDialog,
            applyUnifiedSkin = ::applyUnifiedSkin,
            onAvatarSaved = {
                Toast.makeText(this, "تم تحديث صورة الملف الشخصي", Toast.LENGTH_SHORT).show()
                showProfile()
            },
            onAvatarError = { message ->
                Toast.makeText(this, "تعذر حفظ الصورة: $message", Toast.LENGTH_LONG).show()
            },
            isNotificationsEnabled = activityServicesRuntimeFeature::areNotificationsEnabled,
            setNotificationsEnabled = activityServicesRuntimeFeature::setNotificationsEnabled,
            scheduleNotifications = activityServicesRuntimeFeature::scheduleNotifications,
            cancelNotifications = activityServicesRuntimeFeature::cancelNotifications,
            requestNotificationPermission = activityServicesRuntimeFeature::requestNotificationPermissionIfNeeded,
            onPlatformSelected = { libraryViewState.selected = it },
            recreateActivity = ::recreate,
            showFirstRunSetup = ::showFirstRunSetup,
            showProfileForStartup = ::showProfile
        )
    }

    private val bloggerRuntimeFeature by lazy {
        BloggerRuntimeFeature(
            activity = this,
            services = bloggerServices,
            executor = activityServicesRuntimeFeature.ioExecutor,
            currentAccount = { GoogleSignIn.getLastSignedInAccount(this) },
            currentBlogId = { bloggerBlogId },
            setAdmin = { isAdminSession = it },
            isAdminSession = { isAdminSession },
            isSyncing = { isSyncing },
            setSyncing = { isSyncing = it },
            feedUrl = bloggerFeedUrl,
            postToUi = activityServicesRuntimeFeature::postToUi,
            refreshUi = ::createInterface,
            merge = { imported -> GameImportMerger.merge(gameDataRuntimeFeature.libraryStore, imported) },
            setBlogId = { bloggerBlogId = it },
            refreshAccess = ::refreshBloggerAccess,
            renderGames = ::renderGames,
            gameLibraryStore = gameDataRuntimeFeature.libraryStore,
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
            executeIo = { task -> activityServicesRuntimeFeature.ioExecutor.execute { task() } },
            styleAlertDialog = ::styleAlertDialog,
            applyUnifiedSkin = ::applyUnifiedSkin,
            setCoverFields = { url, preview ->
                coverFeature.state.urlField = url
                coverFeature.state.preview = preview
            },
            pendingCoverLocalPath = { coverFeature.state.pendingLocalPath },
            setPendingCoverLocalPath = { coverUploadState.pendingLocalPath = it },
            pendingCoverPublicUrl = { coverFeature.state.pendingPublicUrl },
            setPendingCoverPublicUrl = { coverUploadState.pendingPublicUrl = it },
            coverUploadInProgress = { coverFeature.state.inProgress },
            loadCover = coverRuntimeFeature::load,
            chooseCover = ::chooseCover,
            deleteCoverFileIfUnused = coverRuntimeFeature::deleteIfUnused,
            resetCoverUploadFailed = { coverFeature.state.resetFailure() },
            saveGames = gameDataRuntimeFeature::saveGames
        )
    }

    private val SIGN_IN_REQUEST = 1002

    private val activityResultRuntimeFeature = ActivityResultRuntimeFeature(
        activity = this,
        signInRequestCode = SIGN_IN_REQUEST,
        avatarRequestCode = 1005,
        coverRequestCode = PICK_IMAGE_REQUEST,
        onGoogleSignInResult = { data ->
            googleAccountRuntimeFeature.accountController.handleSignInResult(
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
        onAvatarSelected = { uri -> userExperienceRuntimeFeature.avatarController.handle(uri) },
        onCoverSelected = { uri -> coverFeature.selectionController.handle(uri) }
    )

    private val bloggerFeedUrl =
        "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100"

    private val lifecycleCoordinator by lazy {
        MainActivityLifecycleCoordinator(
            activity = this,
            initializePreferredPlatform = {
                libraryViewState.selected = getSharedPreferences("app_settings", MODE_PRIVATE)
                    .getString("preferred_platform", "ALL")
                    ?.takeIf { it in listOf("PS2", "PS3", "PS4") } ?: "ALL"
            },
            applySystemBarAppearance = activityServicesRuntimeFeature::applySystemBarAppearance,
            createNotificationChannel = activityServicesRuntimeFeature::createNotificationChannel,
            loadGames = gameDataRuntimeFeature::loadGames,
            restoreAdminSession = ::restoreAdminSession,
            createInterface = ::createInterface,
            showStartupExperience = ::showStartupExperience,
            setupCompleted = {
                getSharedPreferences("app_settings", MODE_PRIVATE)
                    .getBoolean("setup_completed", false)
            },
            notificationsEnabled = activityServicesRuntimeFeature::areNotificationsEnabled,
            scheduleNotifications = activityServicesRuntimeFeature::scheduleNotifications,
            requestNotificationPermission = activityServicesRuntimeFeature::requestNotificationPermissionIfNeeded,
            syncFromBlog = { syncFromBlog(showResult = false) },
            dismissUserExperience = { userExperienceRuntimeFeature.dismiss() },
            disposeLibrary = { libraryRuntimeFeature.dispose() },
            shutdownServices = { activityServicesRuntimeFeature.shutdown() },
            closeCoverRuntime = {
                if (coverRuntimeFeatureCreated) coverRuntimeFeature.close()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleCoordinator.onCreate()
    }

    override fun onResume() {
        super.onResume()
        lifecycleCoordinator.onResume()
    }

    override fun onDestroy() {
        lifecycleCoordinator.onDestroy()
        super.onDestroy()
    }


    private fun restoreAdminSession() {
        isAdminSession = false
        refreshBloggerAccess()
    }

    private fun refreshBloggerAccess() = bloggerRuntimeFeature.refreshAccess()
    private fun startGoogleSignIn() = activityResultRuntimeFeature.launchGoogleSignIn(googleAccountRuntimeFeature.accountController.signInIntent())
    private fun signOutAdmin() {
        googleAccountRuntimeFeature.accountController.signOut {
            isAdminSession = false
            createInterface()
        }
    }


    private val appearanceRuntimeFeature by lazy {
        AppearanceRuntimeFeature(
            activity = this,
            appearancePreferences = ::appearancePreferences,
            isDarkMode = ::isDarkMode,
            bg = { bg },
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray },
            accentForeground = ::accentForeground,
            isSyncing = { isSyncing },
            isAdminSession = { isAdminSession },
            sync = { syncFromBlog(showResult = true) },
            showDownloads = activityServicesRuntimeFeature::showDownloadManager,
            showNotifications = activityServicesRuntimeFeature::showNotificationSettings,
            showProfile = ::showProfile,
            signOut = ::signOutAdmin,
            signIn = ::startGoogleSignIn,
            showGameDialog = { showGameDialog() },
            showGameOfDay = ::showGameOfTheDay,
            openLink = activityServicesRuntimeFeature::openGameLink,
            applyUnifiedSkin = ::applyUnifiedSkin,
            recreateActivity = ::recreate,
            recreateInterface = ::createInterface
        )
    }

    private fun dp(v: Int): Int = appearanceRuntimeFeature.dp(v)
    private fun rounded(color: Int, radius: Int) = appearanceRuntimeFeature.rounded(color, radius)
    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        appearanceRuntimeFeature.makeText(value, size, color, bold)
    private fun applyUnifiedSkin(view: View) = appearanceRuntimeFeature.applyUnifiedSkin(view)
    private fun styleAlertDialog(dialog: AlertDialog) = appearanceRuntimeFeature.styleAlertDialog(dialog)
    private fun makeInput(hintText: String): EditText = appearanceRuntimeFeature.makeInput(hintText)

    private fun currentLanguage(): String = appearanceRuntimeFeature.currentLanguage()
    private fun tr(value: String): String = appearanceRuntimeFeature.translate(value)
    private fun showLanguagePicker() = appearanceRuntimeFeature.showLanguagePicker()
    private fun showSideMenu() = appearanceRuntimeFeature.showSideMenu()
    private fun applyMotionPreferences(view: View) = appearanceRuntimeFeature.applyMotionPreferences(view)
    private fun applyTypography(view: View) =
        appearanceRuntimeFeature.applyTypography(view, resources.displayMetrics.scaledDensity)
    private fun fontScale(): Float = appearanceRuntimeFeature.fontScale()
    private fun fontFamilyName(): String = appearanceRuntimeFeature.fontFamilyName()
    private fun showAppearanceSettings() = appearanceRuntimeFeature.showAppearanceSettings()
    private fun showTypographySettings() = appearanceRuntimeFeature.showTypographySettings()

    private val libraryRuntimeFeature by lazy {
        MainLibraryRuntimeFeature(
            activity = this,
            context = this,
            setContentView = { view -> setContentView(view) },
            games = games,
            darkMode = ::isDarkMode,
            currentLanguage = ::currentLanguage,
            dp = ::dp,
            translate = ::tr,
            rounded = ::rounded,
            createText = { value, size, color, bold -> makeText(value, size, color, bold) },
            bg = bg,
            panel = panel,
            red = red,
            white = white,
            gray = gray,
            adminSession = { isAdminSession },
            accentForeground = ::accentForeground,
            loadCover = coverRuntimeFeature::load,
            showDetails = ::showDetails,
            styleDialog = ::styleAlertDialog,
            showNotificationSettings = activityServicesRuntimeFeature::showNotificationSettings,
            showAppearanceSettings = ::showAppearanceSettings,
            showGameDialog = { showGameDialog() },
            showProfile = ::showProfile,
            showSideMenu = ::showSideMenu,
            applyTypography = ::applyTypography,
            applyUnifiedSkin = ::applyUnifiedSkin,
            applyMotionPreferences = ::applyMotionPreferences
        )
    }

    private val libraryViewState get() = libraryRuntimeFeature.viewState

    private fun showAdvancedFilters() = libraryRuntimeFeature.showAdvancedFilters()
    private fun createInterface() = libraryRuntimeFeature.create()
    private fun renderGames() = libraryRuntimeFeature.renderGames()

    private fun syncFromBlog(showResult: Boolean) = bloggerRuntimeFeature.sync(showResult)

    // COVER RUNTIME
    private fun chooseCover() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        activityResultRuntimeFeature.launchCoverPicker(intent)
    }

    private fun showProfile(): Unit = userExperienceRuntimeFeature.showProfile()
    private val gameExperienceFeature by lazy {
        GameExperienceRuntimeFeature(
            activity = this,
            games = games,
            bg = { bg },
            dp = ::dp,
            rounded = ::rounded,
            makeText = { value, size, color, bold -> makeText(value, size, color, bold) },
            isDarkMode = ::isDarkMode,
            fontFamilyName = ::fontFamilyName,
            fontScale = ::fontScale,
            enqueueGameDownload = activityServicesRuntimeFeature::enqueueGameDownload,
            white = { white },
            red = { red },
            gray = { gray },
            panel = { panel },
            translate = ::tr,
            loadCover = coverRuntimeFeature::load,
            saveGames = gameDataRuntimeFeature::saveGames,
            renderGames = ::renderGames,
            styleAlertDialog = ::styleAlertDialog,
            filesDir = filesDir,
            stableCacheName = coverRuntimeFeature::stableCacheName,
            isAdmin = { isAdminSession },
            postToUi = activityServicesRuntimeFeature::postToUi,
            openGameLink = activityServicesRuntimeFeature::openGameLink,
            showGameDialog = { showGameDialog(it) },
            deleteGame = ::deleteGame,
            applyMotionPreferences = ::applyMotionPreferences,
            applyUnifiedSkin = ::applyUnifiedSkin,
            showDetailsForDay = ::showDetails
        )
    }

    private fun showDetails(game: Game) = gameExperienceFeature.showDetails(game)

    private fun showGameDialog(existing: Game? = null) = bloggerRuntimeFeature.showComposer(existing)

    private fun deleteGame(game: Game) = bloggerRuntimeFeature.delete(game)

    private fun showGameOfTheDay() = gameExperienceFeature.showGameOfTheDay()



}