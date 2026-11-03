
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : ComponentActivity() {

    private val activityServicesRuntimeFeature by lazy {
        ActivityServicesRuntimeFeature(this, ::styleAlertDialog, ::isDarkMode)
    }

    private val appearancePaletteFeature by lazy { AppearancePaletteRuntimeFeature(this) }

    private fun appearancePreferences() = appearancePaletteFeature.preferences()
    private fun isDarkMode() = appearancePaletteFeature.isDarkMode()
    private val bg: Int get() = appearancePaletteFeature.bg
    private val panel: Int get() = appearancePaletteFeature.panel
    private val red: Int get() = appearancePaletteFeature.red
    private val white: Int get() = appearancePaletteFeature.white
    private fun accentForeground(): Int = appearancePaletteFeature.accentForeground()
    private val gray: Int get() = appearancePaletteFeature.gray

    private val games get() = gameDataRuntimeFeature.games
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
            styleDialog = ::styleAlertDialog,
            launchCoverPicker = activityResultRuntimeFeature::launchCoverPicker
        )
    }

    private val googleAccountRuntimeFeature by lazy {
        GoogleAccountRuntimeFeature(applicationContext)
    }
    private val gameDataRuntimeFeature by lazy { GameDataRuntimeFeature(this) }
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
            currentLanguage = appearanceRuntimeFeature::currentLanguage,
            isDarkMode = ::isDarkMode,
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray },
            showLanguagePicker = appearanceRuntimeFeature::showLanguagePicker,
            showNotificationSettings = activityServicesRuntimeFeature::showNotificationSettings,
            launchAvatarPicker = activityResultRuntimeFeature::launchProfileAvatar,
            styleAlertDialog = ::styleAlertDialog,
            applyUnifiedSkin = ::applyUnifiedSkin,
            onAvatarSaved = {
                Toast.makeText(this, "تم تحديث صورة الملف الشخصي", Toast.LENGTH_SHORT).show()
                userExperienceRuntimeFeature.showProfile()
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
            showProfileForStartup = { userExperienceRuntimeFeature.showProfile() }
        )
    }

    private val bloggerRuntimeFeature by lazy {
        BloggerRuntimeFeature(
            activity = this,
            services = googleAccountRuntimeFeature.bloggerServices,
            executor = activityServicesRuntimeFeature.ioExecutor,
            currentAccount = { GoogleSignIn.getLastSignedInAccount(this) },
            currentBlogId = { googleAccountRuntimeFeature.blogId },
            setAdmin = { googleAccountRuntimeFeature.isAdminSession = it },
            isAdminSession = { googleAccountRuntimeFeature.isAdminSession },
            feedUrl = bloggerFeedUrl,
            postToUi = activityServicesRuntimeFeature::postToUi,
            refreshUi = ::createInterface,
            merge = { imported -> GameImportMerger.merge(gameDataRuntimeFeature.libraryStore, imported) },
            setBlogId = { googleAccountRuntimeFeature.blogId = it },
            refreshAccess = { bloggerRuntimeFeature.refreshAccess() },
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
            setPendingCoverLocalPath = { coverFeature.state.pendingLocalPath = it },
            pendingCoverPublicUrl = { coverFeature.state.pendingPublicUrl },
            setPendingCoverPublicUrl = { coverFeature.state.pendingPublicUrl = it },
            coverUploadInProgress = { coverFeature.state.inProgress },
            loadCover = coverRuntimeFeature::load,
            chooseCover = coverFeature::chooseCover,
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
            googleAccountRuntimeFeature.handleSignInResult(
                data = data,
                onSuccess = {
                    googleAccountRuntimeFeature.isAdminSession = false
                    createInterface()
                    bloggerRuntimeFeature.refreshAccess()
                    Toast.makeText(this, "تم ربط حساب Google؛ جارٍ التحقق من صلاحيات Blogger…", Toast.LENGTH_LONG).show()
                },
                onFailure = { message ->
                    googleAccountRuntimeFeature.isAdminSession = false
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
            restoreAdminSession = { googleAccountRuntimeFeature.isAdminSession = false; bloggerRuntimeFeature.refreshAccess() },
            createInterface = ::createInterface,
            showStartupExperience = ::showStartupExperience,
            setupCompleted = {
                getSharedPreferences("app_settings", MODE_PRIVATE)
                    .getBoolean("setup_completed", false)
            },
            notificationsEnabled = activityServicesRuntimeFeature::areNotificationsEnabled,
            scheduleNotifications = activityServicesRuntimeFeature::scheduleNotifications,
            requestNotificationPermission = activityServicesRuntimeFeature::requestNotificationPermissionIfNeeded,
            syncFromBlog = { bloggerRuntimeFeature.sync(showResult = false) },
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
            isSyncing = { bloggerRuntimeFeature.isSyncing },
            isAdminSession = { googleAccountRuntimeFeature.isAdminSession },
            sync = { bloggerRuntimeFeature.sync(showResult = true) },
            showDownloads = activityServicesRuntimeFeature::showDownloadManager,
            showNotifications = activityServicesRuntimeFeature::showNotificationSettings,
            showProfile = { userExperienceRuntimeFeature.showProfile() },
            signOut = { googleAccountRuntimeFeature.signOut { googleAccountRuntimeFeature.isAdminSession = false; createInterface() } },
            signIn = { activityResultRuntimeFeature.launchGoogleSignIn(googleAccountRuntimeFeature.signInIntent()) },
            showGameDialog = { bloggerRuntimeFeature.showComposer() },
            showGameOfDay = { gameExperienceFeature.showGameOfTheDay() },
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

    private fun tr(value: String): String = appearanceRuntimeFeature.translate(value)
    private fun applyMotionPreferences(view: View) = appearanceRuntimeFeature.applyMotionPreferences(view)
    private fun applyTypography(view: View) =
        appearanceRuntimeFeature.applyTypography(view, resources.displayMetrics.scaledDensity)
    private fun fontScale(): Float = appearanceRuntimeFeature.fontScale()
    private fun fontFamilyName(): String = appearanceRuntimeFeature.fontFamilyName()

    private val libraryRuntimeFeature by lazy {
        MainLibraryRuntimeFeature(
            activity = this,
            context = this,
            setContentView = { view -> setContentView(view) },
            games = games,
            darkMode = ::isDarkMode,
            currentLanguage = appearanceRuntimeFeature::currentLanguage,
            dp = ::dp,
            translate = ::tr,
            rounded = ::rounded,
            createText = { value, size, color, bold -> makeText(value, size, color, bold) },
            bg = bg,
            panel = panel,
            red = red,
            white = white,
            gray = gray,
            adminSession = { googleAccountRuntimeFeature.isAdminSession },
            accentForeground = ::accentForeground,
            loadCover = coverRuntimeFeature::load,
            showDetails = { game -> gameExperienceFeature.showDetails(game) },
            styleDialog = ::styleAlertDialog,
            showNotificationSettings = activityServicesRuntimeFeature::showNotificationSettings,
            showAppearanceSettings = { appearanceRuntimeFeature.showAppearanceSettings() },
            showGameDialog = { bloggerRuntimeFeature.showComposer() },
            showProfile = { userExperienceRuntimeFeature.showProfile() },
            showSideMenu = { appearanceRuntimeFeature.showSideMenu() },
            applyTypography = ::applyTypography,
            applyUnifiedSkin = ::applyUnifiedSkin,
            applyMotionPreferences = ::applyMotionPreferences
        )
    }



    private fun showFirstRunSetup() = userExperienceRuntimeFeature.showFirstRunSetup()
    private fun showStartupExperience() = userExperienceRuntimeFeature.showStartupExperience()

    private fun createInterface() = libraryRuntimeFeature.create()
    private fun renderGames() = libraryRuntimeFeature.renderGames()

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
            isAdmin = { googleAccountRuntimeFeature.isAdminSession },
            postToUi = activityServicesRuntimeFeature::postToUi,
            openGameLink = activityServicesRuntimeFeature::openGameLink,
            showGameDialog = { game -> bloggerRuntimeFeature.showComposer(game) },
            deleteGame = { game -> bloggerRuntimeFeature.delete(game) },
            applyMotionPreferences = ::applyMotionPreferences,
            applyUnifiedSkin = ::applyUnifiedSkin,
            showDetailsForDay = { game -> gameExperienceFeature.showDetails(game) }
        )
    }







}