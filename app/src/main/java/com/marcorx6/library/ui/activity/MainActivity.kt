
package com.marcorx6.library.ui.activity

import com.marcorx6.library.BuildConfig
import com.marcorx6.library.R

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
import com.marcorx6.library.data.auth.FirebasePushTokenRepository
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
        // The outgoing root is detached immediately after this call. Cancel its own
        // transition without recursively visiting every game card; card reveal work
        // cancels itself from its detach listener.
        view.animate().cancel()
        view.clearAnimation()
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        if (view is android.view.ViewGroup) view.layoutTransition = null
    }

    private fun showRootScreen(view: View) {
        secondaryScreenBackCallback.isEnabled = true
        // Finish outgoing transitions before replacing the screen, then normalize the incoming root.
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        for (index in 0 until content.childCount) cancelScreenMotion(content.getChildAt(index))
        // Incoming screens are freshly built; avoid a redundant full-tree animation reset.
        val animationsEnabled = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("animations", true)
        if (animationsEnabled) {
            view.alpha = 0f
            view.translationY = dp(12).toFloat()
            view.scaleX = 0.995f
            view.scaleY = 0.995f
        }
        // Apply one direction policy to every full-screen page. This prevents mixed
        // RTL/LTR roots from reversing tab order or pushing icons into adjacent labels.
        val pageLanguage = appearanceRuntimeFeature.currentLanguage()
        val isRtlPage = pageLanguage.equals("ar", ignoreCase = true)
        view.layoutDirection = if (isRtlPage) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        view.textDirection = if (isRtlPage) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        // Translate once before the first draw. A permanent global-layout listener
        // repeatedly traversed the entire view tree and could trigger needless text
        // updates during scrolling/resizing, contributing to visible flicker.
        LanguageTranslator.translateViewTree(view, pageLanguage)
        com.marcorx6.library.AppDiagnostics.setScreen(
            (view.tag as? String)?.takeIf { it.startsWith("marco_screen:") }
                ?: view.javaClass.simpleName
        )
        com.marcorx6.library.AppDiagnostics.log("INFO", "Navigation", "Root view rendered: ${view.javaClass.simpleName}")

        // Use one stable app-owned top bar on every Activity screen. The Android
        // status bar remains visible; the app bar receives the status-bar inset
        // so page content always begins below both bars instead of drawing behind them.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screenHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isDarkMode()) android.graphics.Color.rgb(8, 9, 12)
                else android.graphics.Color.rgb(245, 247, 250))
        }
        screenHost.addView(createApplicationTopBar())

        // The shared app bar is the single owner of the status-bar/cutout inset.
        // Several legacy pages also install their own system-bar listeners; without
        // isolating the content subtree they apply the same top inset a second time,
        // exposing the host's dark backing surface as a temporary black gap.
        val pageHost = android.widget.FrameLayout(this).apply {
            clipToPadding = false
            clipChildren = false
            addView(view, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(pageHost) { _, insets ->
            val withoutTopBars = androidx.core.view.WindowInsetsCompat.Builder(insets)
                .setInsets(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
                    androidx.core.graphics.Insets.NONE
                )
                .build()
            withoutTopBars
        }
        screenHost.addView(pageHost, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(screenHost)
        androidx.core.view.ViewCompat.requestApplyInsets(screenHost)
        com.marcorx6.library.ui.KeyboardLayoutController.install(this, view)
        activityServicesRuntimeFeature.applySystemBarAppearance()
        applyMotionPreferences(screenHost)
        // Skin the actual Activity content root, not only the page body. This
        // includes the shared app bar and every screen child in one pass.
        applyUnifiedSkin(screenHost)
        if (animationsEnabled) {
            view.animate().cancel()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.35f))
                .start()
        }
    }

    /**
     * The top bar is intentionally excluded from generic skin traversal because it
     * owns explicit palette colors. Rebuild it when the palette changes so it never
     * keeps the previous accent while the rest of the page updates live.
     */
    private fun refreshApplicationTopBar() {
        val current = window.decorView.findViewWithTag<View>("marco_primary_topbar") ?: return
        val parent = current.parent as? android.view.ViewGroup ?: return
        val index = parent.indexOfChild(current)
        if (index < 0) return
        val layoutParams = current.layoutParams
        parent.removeViewAt(index)
        parent.addView(createApplicationTopBar(), index, layoutParams)
    }

    private fun createApplicationTopBar(): View {
        val dark = isDarkMode()
        val surfaceBase = if (dark) android.graphics.Color.rgb(8, 9, 12)
            else android.graphics.Color.rgb(245, 247, 250)
        // The top bar is a visible primary-brand surface, not a neutral strip.
        val surface = com.marcorx6.library.ui.AccentPalette.surface(surfaceBase, red, 0.32f)
        val foreground = if (dark) android.graphics.Color.rgb(238, 241, 246)
            else android.graphics.Color.rgb(25, 29, 36)
        val silver = red
        val stroke = if (dark) android.graphics.Color.rgb(105, 113, 126)
            else android.graphics.Color.rgb(174, 181, 191)

        val topBar = LinearLayout(this).apply {
            tag = "marco_primary_topbar"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(58))
        }
        val title = TextView(this).apply {
            text = "MARCO"
            textSize = 19f
            setTextColor(foreground)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = 0.10f
            gravity = android.view.Gravity.CENTER_VERTICAL
            maxLines = 1
            contentDescription = "MARCO"
        }
        row.addView(title, LinearLayout.LayoutParams(0, -1, 1f))

        fun action(icon: Int, label: String, click: () -> Unit): ImageView =
            ImageView(this).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList = android.content.res.ColorStateList.valueOf(silver)
                background = android.graphics.drawable.GradientDrawable().apply {
                    val actionBase = if (dark) android.graphics.Color.rgb(25, 28, 34)
                        else android.graphics.Color.rgb(232, 235, 240)
                    setColor(com.marcorx6.library.ui.AccentPalette.surface(actionBase, red, 0.30f))
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), android.graphics.Color.argb(190,
                        android.graphics.Color.red(red), android.graphics.Color.green(red), android.graphics.Color.blue(red)))
                }
                contentDescription = label
                isClickable = true
                isFocusable = true
                setPadding(dp(8), dp(8), dp(8), dp(8))
                minimumWidth = dp(36)
                minimumHeight = dp(36)
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.argb(72, android.graphics.Color.red(red),
                            android.graphics.Color.green(red), android.graphics.Color.blue(red))
                        ),
                        android.graphics.drawable.GradientDrawable().apply {
                            val actionBase = if (dark) android.graphics.Color.rgb(25, 28, 34)
                                else android.graphics.Color.rgb(232, 235, 240)
                            setColor(com.marcorx6.library.ui.AccentPalette.surface(actionBase, red, 0.30f))
                            cornerRadius = dp(13).toFloat()
                            setStroke(dp(1), stroke)
                        },
                        null
                    )
                }
                setOnClickListener {
                    com.marcorx6.library.AppDiagnostics.recordAction("Top bar: $label")
                    click()
                }
            }

        row.addView(action(
            R.drawable.ic_action_notifications, tr("الإشعارات")
        ) { firebaseAccountController.openNotificationsFromLibrary() },
            LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(6) })
        row.addView(action(
            R.drawable.ic_action_settings, tr("الإعدادات")
        ) { appearanceRuntimeFeature.showAppearanceSettings() },
            LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(6) })
        row.addView(action(
            if (dark) R.drawable.ic_action_sun else R.drawable.ic_action_moon,
            tr(if (dark) "الوضع الفاتح" else "الوضع الداكن")
        ) {
            com.marcorx6.library.AppDiagnostics.recordAction("Toggle appearance mode")
            // Use the same preference store read by AppearancePaletteRuntimeFeature.
            appearancePreferences().edit()
                .putBoolean("dark_mode", !isDarkMode())
                .apply()
            recreate()
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(6) })

        topBar.addView(row)
        // The separator is a primary brand line, not a fixed neutral gray.
        topBar.addView(View(this).apply { setBackgroundColor(red) },
            LinearLayout.LayoutParams(-1, dp(1)))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topBar) { target, insets ->
            val statusTop = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            ).top
            if (target.paddingTop != statusTop) target.setPadding(0, statusTop, 0, 0)
            insets
        }
        return topBar
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
    private val firebaseAccountControllerDelegate: Lazy<FirebaseAccountController> = lazy {
        FirebaseAccountController(
            activity = this,
            authRepository = firebaseAuthRepository,
            onAccountChanged = {
                // Keep anonymous visitor sessions available after sign-out or deletion.
                firebaseSessionCoordinator.ensureGuestSession()
                createInterface()
            },
            onSignedIn = {
                FirebasePushTokenRepository().syncCurrentToken()
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
            setContentView = ::showRootScreen,
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

    private val firebaseAccountController: FirebaseAccountController by firebaseAccountControllerDelegate

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

    // A palette selection can happen while a page, RecyclerView or dialog is
    // already alive. Re-skin the current window immediately instead of waiting
    // for navigation or an Activity restart.
    private val appearancePreferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == com.marcorx6.library.ui.AccentPalette.PREFERENCE_KEY || key == "dark_mode") {
                window.decorView.post {
                    if (!isFinishing && !isDestroyed) {
                        refreshApplicationTopBar()
                        applyUnifiedSkin(window.decorView)
                        // Main-library renderers keep cached palette values for fast list updates.
                        // Refresh those values too, so the next card/filter render uses the new accent.
                        if (libraryRuntimeFeatureDelegate.isInitialized()) {
                            libraryRuntimeFeature.refreshPalette(bg, panel, red, white, gray)
                        }
                        activityServicesRuntimeFeature.applySystemBarAppearance()
                    }
                }
            }
        }

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
            // Permission checks finish asynchronously. Refresh only the visible game list;
            // rebuilding the Activity root here would eject users from whichever section
            // they opened while Blogger access was being verified.
            refreshUi = ::renderGames,
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
                    // Blogger/Drive OAuth is intentionally a separate Google session
                    // from the optional Firebase MARCO profile. Do not reject a valid
                    // Blogger owner account just because its email differs from Firebase.
                    googleAccountRuntimeFeature.isAdminSession = false
                    createInterface()
                    Toast.makeText(
                        this,
                        tr("تم ربط حساب Google؛ جارٍ تحميل مدونة MARCO والتحقق من صلاحيات النشر…"),
                        Toast.LENGTH_LONG
                    ).show()
                    // Resolve the active blog ID before checking write access. This also
                    // handles fresh installs where no Blogger blog ID has been cached yet.
                    bloggerRuntimeFeature.sync(showResult = false)
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
            disposeAccount = { if (firebaseAccountControllerDelegate.isInitialized()) firebaseAccountController.dispose() },
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
        com.marcorx6.library.ui.UiDiagnosticsMonitor.install(this)
        com.marcorx6.library.AppDiagnostics.recordAction("MainActivity onCreate")
        com.marcorx6.library.ui.UiDiagnosticsMonitor.logLifecycle(this, "onCreate")
        com.marcorx6.library.AppDiagnostics.log(
            "INFO",
            "ACTIVITY_STATE",
            "savedInstanceStatePresent=${savedInstanceState != null} sdk=${android.os.Build.VERSION.SDK_INT} " +
                "density=${resources.displayMetrics.density} scaledDensity=${resources.displayMetrics.scaledDensity} " +
                "screen=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}"
        )
        val appCheckProvider: AppCheckProviderFactory? = when {
            BuildConfig.DEBUG -> DebugAppCheckProviderFactory.getInstance()
            BuildConfig.PLAY_DISTRIBUTION -> PlayIntegrityAppCheckProviderFactory.getInstance()
            else -> null // GitHub/Telegram sideload builds must not require Play Integrity.
        }
        appCheckProvider?.let {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(it)
        }
        onBackPressedDispatcher.addCallback(this, secondaryScreenBackCallback)
        appearancePreferences().registerOnSharedPreferenceChangeListener(appearancePreferenceListener)
        firebaseSessionCoordinator.start()
        lifecycleCoordinator.onCreate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        com.marcorx6.library.ui.UiDiagnosticsMonitor.logWindowFocus(this, hasFocus)
        if (hasFocus && !isFinishing && !isDestroyed) {
            // Dialogs and external activities can temporarily change system-bar visibility.
            // Reapply the immersive policy only after this Activity regains focus.
            activityServicesRuntimeFeature.applySystemBarAppearance()
        }
    }

    override fun onResume() {
        super.onResume()
        com.marcorx6.library.ui.UiDiagnosticsMonitor.logLifecycle(this, "onResume")
        FirebasePushTokenRepository().syncCurrentToken()
        firebaseSessionCoordinator.ensureGuestSession()
        lifecycleCoordinator.onResume()
    }

    override fun onPause() {
        com.marcorx6.library.ui.UiDiagnosticsMonitor.logLifecycle(this, "onPause")
        super.onPause()
    }

    override fun onStop() {
        com.marcorx6.library.ui.UiDiagnosticsMonitor.logLifecycle(this, "onStop")
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (coverRuntimeFeatureDelegate.isInitialized()) {
            coverRuntimeFeature.trimMemory(level)
        }
    }

    override fun onDestroy() {
        com.marcorx6.library.ui.UiDiagnosticsMonitor.logLifecycle(this, "onDestroy")
        appearancePreferences().unregisterOnSharedPreferenceChangeListener(appearancePreferenceListener)
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
            accentId = appearancePaletteFeature::accentId,
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
        dialog.window?.let { window ->
            com.marcorx6.library.ui.UiDiagnosticsMonitor.install(window, "AlertDialog")
            LanguageTranslator.translateViewTree(window.decorView, appearanceRuntimeFeature.currentLanguage())
            applyMotionPreferences(window.decorView)
        }
    }
    private fun makeInput(hintText: String): EditText = appearanceRuntimeFeature.makeInput(hintText)

    private fun tr(value: String): String = appearanceRuntimeFeature.translate(value)
    private fun applyMotionPreferences(view: View) = appearanceRuntimeFeature.applyMotionPreferences(view)
    private fun applyTypography(view: View) =
        appearanceRuntimeFeature.applyTypography(view, resources.displayMetrics.scaledDensity)
    private fun fontScale(): Float = appearanceRuntimeFeature.fontScale()
    private fun fontFamilyName(): String = appearanceRuntimeFeature.fontFamilyName()

    private val libraryRuntimeFeatureDelegate = lazy {
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
            showNotifications = firebaseAccountController::openNotificationsFromLibrary,
            showMessages = firebaseAccountController::openMessagesFromLibrary,
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
    private val libraryRuntimeFeature: MainLibraryRuntimeFeature by libraryRuntimeFeatureDelegate

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