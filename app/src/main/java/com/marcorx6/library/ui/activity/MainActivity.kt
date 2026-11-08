
package com.marcorx6.library.ui.activity

import com.marcorx6.library.BuildConfig

import com.marcorx6.library.ui.appearance.AppearanceRuntimeFeature
import com.marcorx6.library.ui.appearance.AppearancePaletteRuntimeFeature
import com.marcorx6.library.ui.blog.BloggerRuntimeFeature
import com.marcorx6.library.ui.covers.CoverRuntimeFeature
import com.marcorx6.library.ui.covers.CoverUploadRuntimeFeature
import com.marcorx6.library.ui.data.GameDataRuntimeFeature
import com.marcorx6.library.ui.experience.UserExperienceRuntimeFeature
import com.marcorx6.library.ui.game.GameExperienceRuntimeFeature
import com.marcorx6.library.ui.library.MainLibraryRuntimeFeature
import com.marcorx6.library.ui.GameImportMerger
import com.marcorx6.library.ui.LanguageTranslator
import com.marcorx6.library.ui.library.MainLibraryViewState
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameEngagementRepository
import com.marcorx6.library.data.DownloadStoragePreferences
import com.marcorx6.library.data.auth.FirebaseSessionCoordinator
import com.marcorx6.library.data.auth.FirebaseAuthRepository
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.marcorx6.library.data.auth.FirebaseUserDocumentRepository
import com.marcorx6.library.ui.auth.FirebaseAccountController
import com.marcorx6.library.ui.auth.GoogleAccountRuntimeFeature
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : ComponentActivity() {

    private companion object {
        var startupExperienceShown = false
    }

    private val secondaryScreenBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            createInterface()
        }
    }

    private fun cancelScreenMotion(view: View) {
        view.animate().cancel()
        view.clearAnimation()
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        if (view is android.view.ViewGroup) {
            view.layoutTransition = null
            for (index in 0 until view.childCount) cancelScreenMotion(view.getChildAt(index))
        }
    }

    private fun showRootScreen(view: View) {
        secondaryScreenBackCallback.isEnabled = true
        // Finish outgoing transitions before replacing the screen, then normalize the incoming root.
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        for (index in 0 until content.childCount) cancelScreenMotion(content.getChildAt(index))
        cancelScreenMotion(view)
        // Translate once before the first draw. A permanent global-layout listener
        // repeatedly traversed the entire view tree and could trigger needless text
        // updates during scrolling/resizing, contributing to visible flicker.
        LanguageTranslator.translateViewTree(view, appearanceRuntimeFeature.currentLanguage())
        setContentView(view)
        applyMotionPreferences(view)
        applyUnifiedSkin(view)
    }

    private val downloadStoragePreferences by lazy { DownloadStoragePreferences(applicationContext) }

    private val gameEngagementRepository by lazy { GameEngagementRepository() }

    private val firebaseSessionCoordinator: FirebaseSessionCoordinator by lazy {
        FirebaseSessionCoordinator()
    }
    private val firebaseAuthRepository: FirebaseAuthRepository by lazy {
        FirebaseAuthRepository()
    }
    private val firebaseUserDocumentRepository: FirebaseUserDocumentRepository by lazy {
        FirebaseUserDocumentRepository()
    }

    private fun saveFirebaseProfile(nickname: String) {
        val user = firebaseAuthRepository.currentUser ?: return
        firebaseUserDocumentRepository.saveProfile(
            uid = user.uid,
            displayName = nickname,
            bio = null,
            photoUrl = user.photoUrl?.toString()
        ).addOnFailureListener { error ->
            android.util.Log.w("MarcoFirebaseProfile", "Cloud profile save failed; local profile was saved", error)
        }
    }
    private val firebaseAccountController: FirebaseAccountController by lazy {
        FirebaseAccountController(
            activity = this,
            authRepository = firebaseAuthRepository,
            onAccountChanged = {
                // Keep anonymous visitor sessions available after sign-out or deletion.
                firebaseSessionCoordinator.ensureGuestSession()
                createInterface()
            },
            onSignedIn = {
                // After authentication, open the editable account profile directly.
                showFirebaseAccount()
            },
            launchGoogleSignIn = { intent -> activityResultRuntimeFeature.launchFirebaseGoogleSignIn(intent) },
            launchAdminGoogleSignIn = { intent -> activityResultRuntimeFeature.launchGoogleSignIn(intent) },
            signOutAdmin = {
                // Clear the admin UI state immediately. FirebaseAccountController invokes
                // onAccountChanged once after this, so avoid a second competing screen rebuild.
                googleAccountRuntimeFeature.isAdminSession = false
                googleAccountRuntimeFeature.signOut { }
            },
            styleDialog = ::styleAlertDialog,
            setContentView = { view -> secondaryScreenBackCallback.isEnabled = true; setContentView(view) },
            onBack = ::createInterface,
            dp = ::dp,
            isDarkMode = ::isDarkMode,
            applyUnifiedSkin = ::applyUnifiedSkin,
            launchProfilePhotoPicker = { intent -> activityResultRuntimeFeature.launchProfileAvatar(intent) },
            translate = ::tr,
            games = { games },
            loadCover = coverRuntimeFeature::load,
            openGameDetails = { game -> gameExperienceFeature.showDetails(game) },
            gameEngagementRepository = gameEngagementRepository
        )
    }

    private fun showFirebaseAccount() = firebaseAccountController.show()

    private val activityServicesRuntimeFeature: ActivityServicesRuntimeFeature by lazy {
        ActivityServicesRuntimeFeature(this, ::styleAlertDialog, ::isDarkMode, ::tr, ::showRootScreen, ::createInterface) { source ->
            bloggerRuntimeFeature.refreshGame(source) { refreshed ->
                if (refreshed != null) gameExperienceFeature.showDetails(refreshed)
                else Toast.makeText(this, tr("تعذر تحديث معلومات اللعبة من المصدر؛ تحقق من الاتصال بالإنترنت"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private val appearancePaletteFeature: AppearancePaletteRuntimeFeature by lazy { AppearancePaletteRuntimeFeature(this) }

    private fun appearancePreferences() = appearancePaletteFeature.preferences()
    private fun isDarkMode() = appearancePaletteFeature.isDarkMode()
    private val bg: Int get() = appearancePaletteFeature.bg
    private val panel: Int get() = appearancePaletteFeature.panel
    private val red: Int get() = appearancePaletteFeature.red
    private val white: Int get() = appearancePaletteFeature.white
    private fun accentForeground(): Int = appearancePaletteFeature.accentForeground()
    private val gray: Int get() = appearancePaletteFeature.gray

    private val games: MutableList<Game> get() = gameDataRuntimeFeature.games
    private val coverRuntimeFeatureDelegate: Lazy<CoverRuntimeFeature> = lazy {
        CoverRuntimeFeature(applicationContext, filesDir) { games }
    }
    private val coverRuntimeFeature: CoverRuntimeFeature by coverRuntimeFeatureDelegate

    private val libraryViewState: MainLibraryViewState get() = libraryRuntimeFeature.viewState

    private val coverFeature: CoverUploadRuntimeFeature by lazy {
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
            translate = ::tr,
            launchCoverPicker = activityResultRuntimeFeature::launchCoverPicker
        )
    }

    private val googleAccountRuntimeFeature: GoogleAccountRuntimeFeature by lazy {
        GoogleAccountRuntimeFeature(applicationContext, ::tr)
    }
    private val gameDataRuntimeFeature: GameDataRuntimeFeature by lazy { GameDataRuntimeFeature(this) }
    private val userExperienceRuntimeFeature: UserExperienceRuntimeFeature by lazy {
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
            showAccount = ::showFirebaseAccount,
            saveCloudProfile = ::saveFirebaseProfile,
            launchAvatarPicker = activityResultRuntimeFeature::launchProfileAvatar,
            styleAlertDialog = ::styleAlertDialog,
            applyUnifiedSkin = ::applyUnifiedSkin,
            applyMotionPreferences = ::applyMotionPreferences,
            onAvatarSaved = {
                Toast.makeText(this, tr("تم تحديث صورة الملف الشخصي"), Toast.LENGTH_SHORT).show()
                userExperienceRuntimeFeature.showProfile()
            },
            onAvatarError = { message ->
                Toast.makeText(this, "${tr("تعذر حفظ الصورة")}: $message", Toast.LENGTH_LONG).show()
            },
            isNotificationsEnabled = activityServicesRuntimeFeature::areNotificationsEnabled,
            setNotificationsEnabled = activityServicesRuntimeFeature::setNotificationsEnabled,
            scheduleNotifications = activityServicesRuntimeFeature::scheduleNotifications,
            cancelNotifications = activityServicesRuntimeFeature::cancelNotifications,
            requestNotificationPermission = activityServicesRuntimeFeature::requestNotificationPermissionIfNeeded,
            launchDownloadFolderPicker = activityResultRuntimeFeature::launchDownloadFolderPicker,
            downloadFolderName = downloadStoragePreferences::displayName,
            onPlatformSelected = { libraryViewState.selected = it },
            recreateActivity = ::recreate,
            showFirstRunSetup = ::showFirstRunSetup,
            showProfileForStartup = { userExperienceRuntimeFeature.showProfile() },
            setContentView = ::showRootScreen,
            onBack = ::createInterface
        )
    }

    private val bloggerRuntimeFeature: BloggerRuntimeFeature by lazy {
        BloggerRuntimeFeature(
            activity = this,
            services = googleAccountRuntimeFeature.bloggerServices,
            executor = activityServicesRuntimeFeature.ioExecutor,
            currentAccount = googleAccountRuntimeFeature::currentAccount,
            currentBlogId = { googleAccountRuntimeFeature.blogId },
            setAdmin = { googleAccountRuntimeFeature.isAdminSession = it },
            isAdminSession = { googleAccountRuntimeFeature.isAdminSession },
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
            setContentView = ::showRootScreen,
            onBack = ::createInterface,
            translate = ::tr,
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

    private val activityResultRuntimeFeature: ActivityResultRuntimeFeature = ActivityResultRuntimeFeature(
        activity = this,
        onGoogleSignInResult = { data ->
            googleAccountRuntimeFeature.handleSignInResult(
                data = data,
                onSuccess = {
                    googleAccountRuntimeFeature.isAdminSession = false
                    val firebaseEmail = firebaseAuthRepository.currentUser?.email?.trim()?.lowercase()
                    val bloggerEmail = googleAccountRuntimeFeature.currentAccount()?.email?.trim()?.lowercase()
                    if (firebaseEmail.isNullOrBlank() || bloggerEmail.isNullOrBlank() || firebaseEmail != bloggerEmail) {
                        googleAccountRuntimeFeature.signOut {
                            googleAccountRuntimeFeature.isAdminSession = false
                            createInterface()
                            Toast.makeText(this, tr("حساب Google المختار مختلف عن حساب MARCO. سجّل بنفس البريد الإلكتروني."), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        createInterface()
                        bloggerRuntimeFeature.refreshAccess()
                        Toast.makeText(this, tr("تم ربط نفس حساب Google؛ جارٍ التحقق من صلاحيات Blogger…"), Toast.LENGTH_LONG).show()
                    }
                },
                onFailure = { message ->
                    googleAccountRuntimeFeature.isAdminSession = false
                    createInterface()
                    Toast.makeText(this, "${tr("فشل تسجيل الدخول إلى Google")}: $message", Toast.LENGTH_LONG).show()
                }
            )
        },
        onFirebaseGoogleSignInResult = { data -> firebaseAccountController.handleGoogleSignInResult(data) },
        onAvatarSelected = { uri -> firebaseAccountController.handleProfilePhotoSelected(uri) },
        onCoverSelected = { uri -> coverFeature.selectionController.handle(uri) },
        onDownloadFolderSelected = { uri ->
            try {
                downloadStoragePreferences.saveTree(uri)
                Toast.makeText(this, "${tr("تم حفظ مجلد التنزيلات")}: ${downloadStoragePreferences.displayName()}", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(this, "${tr("تعذر حفظ صلاحية المجلد")}: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    )

    private val lifecycleCoordinator: MainActivityLifecycleCoordinator by lazy {
        MainActivityLifecycleCoordinator(
            activity = this,
            initializePreferredPlatform = { libraryRuntimeFeature.initializePreferredPlatform() },
            applySystemBarAppearance = activityServicesRuntimeFeature::applySystemBarAppearance,
            createNotificationChannel = activityServicesRuntimeFeature::createNotificationChannel,
            loadGames = gameDataRuntimeFeature::loadGames,
            restoreAdminSession = { googleAccountRuntimeFeature.isAdminSession = false; bloggerRuntimeFeature.refreshAccess() },
            createInterface = ::createInterface,
            showStartupExperience = {
                if (!startupExperienceShown) {
                    startupExperienceShown = true
                    showStartupExperience()
                }
            },
            setupCompleted = userExperienceRuntimeFeature::isSetupCompleted,
            notificationsEnabled = activityServicesRuntimeFeature::areNotificationsEnabled,
            scheduleNotifications = activityServicesRuntimeFeature::scheduleNotifications,
            requestNotificationPermission = activityServicesRuntimeFeature::requestNotificationPermissionIfNeeded,
            syncFromBlog = { bloggerRuntimeFeature.sync(showResult = false) },
            dismissUserExperience = { userExperienceRuntimeFeature.dismiss() },
            disposeLibrary = { libraryRuntimeFeature.dispose() },
            shutdownServices = { activityServicesRuntimeFeature.shutdown() },
            closeCoverRuntime = {
                if (coverRuntimeFeatureDelegate.isInitialized()) coverRuntimeFeature.close()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appCheckProvider: AppCheckProviderFactory? = when {
            BuildConfig.DEBUG -> DebugAppCheckProviderFactory.getInstance()
            BuildConfig.PLAY_DISTRIBUTION -> PlayIntegrityAppCheckProviderFactory.getInstance()
            else -> null // GitHub/Telegram sideload builds must not require Play Integrity.
        }
        appCheckProvider?.let {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(it)
        }
        onBackPressedDispatcher.addCallback(this, secondaryScreenBackCallback)
        firebaseSessionCoordinator.start()
        lifecycleCoordinator.onCreate()
    }

    override fun onResume() {
        super.onResume()
        firebaseSessionCoordinator.ensureGuestSession()
        lifecycleCoordinator.onResume()
    }

    override fun onDestroy() {
        lifecycleCoordinator.onDestroy()
        firebaseSessionCoordinator.stop()
        super.onDestroy()
    }



    private val appearanceRuntimeFeature: AppearanceRuntimeFeature by lazy {
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
            showNotifications = activityServicesRuntimeFeature::showNotificationSettings,
            launchDownloadFolderPicker = activityResultRuntimeFeature::launchDownloadFolderPicker,
            downloadFolderName = downloadStoragePreferences::displayName,
            adminSessionConnected = { googleAccountRuntimeFeature.isAdminSession },
            onAdminSessionAction = {
                if (googleAccountRuntimeFeature.isAdminSession) {
                    googleAccountRuntimeFeature.signOut {
                        googleAccountRuntimeFeature.isAdminSession = false
                        createInterface()
                    }
                } else if (firebaseAuthRepository.currentUser?.email.isNullOrBlank()) {
                    Toast.makeText(this, tr("سجّل الدخول إلى حساب MARCO أولاً"), Toast.LENGTH_LONG).show()
                } else {
                    firebaseAccountController.connectAdminAccount()
                }
            },
            recreateActivity = ::recreate,
            recreateInterface = ::createInterface,
            setContentView = ::showRootScreen,
            onBack = ::createInterface
        )
    }

    private fun dp(v: Int): Int = appearanceRuntimeFeature.dp(v)
    private fun rounded(color: Int, radius: Int) = appearanceRuntimeFeature.rounded(color, radius)
    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        appearanceRuntimeFeature.makeText(value, size, color, bold)
    private fun applyUnifiedSkin(view: View) = appearanceRuntimeFeature.applyUnifiedSkin(view)
    private fun styleAlertDialog(dialog: AlertDialog) {
        appearanceRuntimeFeature.styleAlertDialog(dialog)
        dialog.window?.decorView?.let(::applyMotionPreferences)
    }
    private fun makeInput(hintText: String): EditText = appearanceRuntimeFeature.makeInput(hintText)

    private fun tr(value: String): String = appearanceRuntimeFeature.translate(value)
    private fun applyMotionPreferences(view: View) = appearanceRuntimeFeature.applyMotionPreferences(view)
    private fun applyTypography(view: View) =
        appearanceRuntimeFeature.applyTypography(view, resources.displayMetrics.scaledDensity)
    private fun fontScale(): Float = appearanceRuntimeFeature.fontScale()
    private fun fontFamilyName(): String = appearanceRuntimeFeature.fontFamilyName()

    private val libraryRuntimeFeature: MainLibraryRuntimeFeature by lazy {
        MainLibraryRuntimeFeature(
            activity = this,
            context = this,
            setContentView = ::showRootScreen,
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
            toggleDarkMode = {
                appearancePreferences().edit()
                    .putBoolean("dark_mode", !isDarkMode())
                    .apply()
                recreate()
            },
            showAppearanceSettings = { appearanceRuntimeFeature.showAppearanceSettings() },
            showGameDialog = { bloggerRuntimeFeature.showComposer() },
            showProfile = { userExperienceRuntimeFeature.showProfile() },
            showDownloads = activityServicesRuntimeFeature::showDownloadManager,
            applyTypography = ::applyTypography,
            applyUnifiedSkin = ::applyUnifiedSkin,
            applyMotionPreferences = ::applyMotionPreferences,
            loadEngagementSummary = { game, callback ->
                gameEngagementRepository.loadSummary(game, { summary ->
                    callback(summary.views, summary.ratingAverage, summary.ratingCount)
                })
            },
        )
    }



    private fun showFirstRunSetup() = userExperienceRuntimeFeature.showFirstRunSetup()
    private fun showStartupExperience() = userExperienceRuntimeFeature.showStartupExperience()

    private fun createInterface() {
        secondaryScreenBackCallback.isEnabled = false
        libraryRuntimeFeature.create()
    }
    private fun renderGames() = libraryRuntimeFeature.renderGames()

    private val gameExperienceFeature: GameExperienceRuntimeFeature by lazy {
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
            enqueueGameDownload = { url, title, agent, disposition, mime, referrer, source ->
                activityServicesRuntimeFeature.enqueueGameDownload(url, title, agent, disposition, mime, referrer, source)
            },
            white = { white },
            red = { red },
            gray = { gray },
            panel = { panel },
            translate = ::tr,
            targetLanguage = appearanceRuntimeFeature::currentLanguage,
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
            showDetailsForDay = { game -> gameExperienceFeature.showDetails(game) },
            isRegisteredUser = gameEngagementRepository::isRegisteredUser,
            loadEngagementSummary = { game, callback ->
                gameEngagementRepository.loadSummary(
                    game,
                    callback = { summary ->
                        callback(summary.views, summary.ratingAverage, summary.ratingCount, summary.downloads)
                    }
                )
            },
            recordDownload = { game ->
                gameEngagementRepository.recordDownload(game).addOnFailureListener { error ->
                    android.util.Log.w("MarcoGameDownloads", "Could not record game download", error)
                }
            },
            loadMyRating = gameEngagementRepository::loadMyRating,
            submitRating = { game, rating, callback ->
                gameEngagementRepository.submitRating(game, rating)
                    .addOnSuccessListener { callback(true, null) }
                    .addOnFailureListener { callback(false, it.message) }
            },
            recordViewOnce = { game ->
                gameEngagementRepository.recordViewOnce(game)
                    .addOnSuccessListener { recorded ->
                        if (recorded) renderGames()
                    }
                    .addOnFailureListener { error ->
                        android.util.Log.w("MarcoGameViews", "Could not record game view", error)
                    }
            }
        )
    }







}