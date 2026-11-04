package com.marcorx6.library.ui.auth

import android.app.Activity
import android.content.Intent
import android.app.AlertDialog
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.auth.FirebaseAuthRepository
import com.marcorx6.library.data.auth.FirebaseUserDocumentRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Email account UI for the optional Firebase account.
 * It does not use or sign out the separate Google OAuth session for Blogger and Drive.
 */
internal class FirebaseAccountController(
    private val activity: Activity,
    private val authRepository: FirebaseAuthRepository,
    private val onAccountChanged: () -> Unit,
    private val launchGoogleSignIn: (Intent) -> Unit,
    private val launchAdminGoogleSignIn: (Intent) -> Unit,
    private val isAdminSession: () -> Boolean,
    private val signOutAdmin: () -> Unit,
    private val styleDialog: (AlertDialog) -> Unit,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit,
    private val dp: (Int) -> Int,
    private val isDarkMode: () -> Boolean,
    private val applyUnifiedSkin: (View) -> Unit,
    private val translate: (String) -> String
) {
    private val userDocuments = FirebaseUserDocumentRepository()
    private var pendingDeletionUid: String? = null

    fun show() {
        val user = authRepository.currentUser
        if (user == null || user.isAnonymous) {
            showGuestActions()
        } else {
            showSignedInActions(user)
        }
    }

    private fun showGuestActions() = showAccountScreen(authRepository.currentUser)

    private fun showAccountScreen(user: FirebaseUser?) {
        val dark = isDarkMode()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (translate("حساب MARCO RX6") == "حساب MARCO RX6") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(if (dark) Color.rgb(6, 16, 30) else Color.rgb(240, 247, 255))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(activity).apply {
            text = translate("حساب MARCO RX6")
            textSize = 23f
            setTextColor(if (dark) Color.WHITE else Color.rgb(18, 42, 68))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(52), 1f))
        val back = TextView(activity).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(75, 165, 255))
            contentDescription = translate("رجوع")
            minWidth = dp(48)
            minHeight = dp(48)
            setOnClickListener { onBack() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header)

        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (dark) intArrayOf(Color.argb(218, 23, 105, 190), Color.argb(190, 9, 39, 76))
                else intArrayOf(Color.argb(225, 211, 235, 255), Color.argb(205, 239, 247, 255))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), if (dark) Color.argb(170, 91, 170, 242) else Color.argb(190, 123, 180, 231))
            }
        }
        val stateTitle = TextView(activity).apply {
            text = when {
                isAdminSession() -> translate("وضع المسؤول نشط")
                user != null && !user.isAnonymous -> translate("مرحباً بعودتك")
                user?.isAnonymous == true -> translate("أنت تستعمل التطبيق كزائر")
                else -> translate("اختَر طريقة الدخول")
            }
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.WHITE else Color.rgb(17, 54, 89))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val avatar = TextView(activity).apply {
            val identity = user?.displayName?.trim()?.firstOrNull()
                ?: user?.email?.trim()?.firstOrNull()
                ?: 'M'
            text = identity.uppercase()
            textSize = 27f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(75, 175, 255), Color.rgb(23, 91, 183))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.argb(190, 190, 225, 255))
            }
        }
        hero.addView(avatar, LinearLayout.LayoutParams(dp(68), dp(68)).apply { bottomMargin = dp(13) })
        hero.addView(stateTitle)
        val stateDetail = TextView(activity).apply {
            text = when {
                isAdminSession() && user == null -> translate("جلسة Blogger وGoogle Drive مستقلة عن حساب التطبيق")
                user != null && !user.isAnonymous -> listOfNotNull(user.displayName?.takeIf { it.isNotBlank() }, user.email).joinToString("  •  ") + if (isAdminSession()) "  •  " + translate("مسؤول نشط") else "  •  " + translate("مستخدم")
                user?.isAnonymous == true -> translate("يمكنك الاستمرار دون حساب أو ربط حسابك لاحقاً")
                else -> translate("احفظ ملفك الشخصي واستعمل مزايا الحساب على أجهزتك")
            }
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            setTextColor(if (dark) Color.rgb(214, 232, 250) else Color.rgb(54, 91, 126))
        }
        hero.addView(stateDetail)
        page.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })

        fun action(label: String, detail: String, primary: Boolean = false, run: () -> Unit) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    if (primary) intArrayOf(Color.argb(220, 31, 132, 235), Color.argb(195, 16, 82, 164))
                    else if (dark) intArrayOf(Color.argb(150, 21, 51, 81), Color.argb(125, 11, 32, 56))
                    else intArrayOf(Color.argb(205, 255, 255, 255), Color.argb(185, 228, 241, 255))
                ).apply {
                    cornerRadius = dp(17).toFloat()
                    setStroke(dp(1), if (primary) Color.argb(210, 112, 184, 255) else if (dark) Color.argb(105, 104, 163, 218) else Color.argb(150, 155, 194, 231))
                }
                minHeight = dp(64)
                contentDescription = "$label. $detail"
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    foreground = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.argb(78, 83, 177, 255)),
                        null,
                        android.graphics.drawable.ColorDrawable(Color.WHITE)
                    )
                }
                setOnClickListener { run() }
            }
            val title = TextView(activity).apply {
                text = label
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (primary || dark) Color.WHITE else Color.rgb(20, 48, 76))
            }
            val subtitle = TextView(activity).apply {
                text = detail
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
                setTextColor(if (primary) Color.rgb(225, 240, 255) else if (dark) Color.rgb(183, 207, 231) else Color.rgb(83, 111, 139))
            }
            item.addView(title)
            item.addView(subtitle)
            page.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }

        if (user == null || user.isAnonymous) {
            action(translate("المتابعة كزائر"), translate("استعمل المكتبة دون إنشاء حساب"), run = onBack)
            action(translate("الدخول باستخدام Google"), translate("ربط حساب Google بحساب MARCO RX6"), primary = true, run = ::startFreshGoogleAccountSelection)
            action(translate("إنشاء حساب بالبريد الإلكتروني"), translate("حساب جديد مع كلمة مرور"), run = { showEmailForm(createAccount = true) })
            action(translate("الدخول بالبريد الإلكتروني"), translate("لديك حساب بالفعل؟"), run = { showEmailForm(createAccount = false) })
            action(
                translate(if (isAdminSession()) "إيقاف وضع المسؤول" else "دخول المسؤول"),
                translate("صلاحيات Blogger وGoogle Drive"),
                primary = isAdminSession(),
                run = { if (isAdminSession()) signOutAdmin() else startAdminGoogleAccountSelection() }
            )
            action(translate("نسيت كلمة المرور؟"), translate("استرجاع الوصول إلى حسابك"), run = ::showPasswordResetForm)
            if (user?.isAnonymous == true) {
                action(translate("حذف بيانات جلسة الزائر"), translate("حذف ملف الزائر السحابي فقط"), run = { confirmAccountDeletion(user) })
            }
        } else {
            action(translate("حالة الحساب"), if (user.isEmailVerified) translate("البريد الإلكتروني مؤكد") else translate("البريد الإلكتروني غير مؤكد"), run = {
                if (!user.isEmailVerified) authRepository.sendEmailVerification()
                    ?.addOnSuccessListener { toast("تم إرسال رابط التأكيد إلى بريدك") }
                    ?.addOnFailureListener { toast("تعذر إرسال رابط التأكيد") }
            })
            if (user.providerData.any { it.providerId == "password" }) {
                action(translate("استرجاع كلمة المرور"), translate("إرسال رابط تغيير كلمة المرور"), run = {
                    user.email?.let { address -> authRepository.sendPasswordReset(address)
                        .addOnSuccessListener { toast("تم إرسال رابط استرجاع كلمة المرور") }
                        .addOnFailureListener { toast("تعذر إرسال رابط الاسترجاع") } }
                })
            }
            action(if (isAdminSession()) translate("إيقاف وضع المسؤول") else translate("دخول المسؤول"), translate("إدارة Blogger وGoogle Drive"), primary = true, run = {
                if (isAdminSession()) signOutAdmin() else startAdminGoogleAccountSelection()
            })
            action(translate("تسجيل الخروج"), translate("الخروج من حساب MARCO RX6"), run = {
                GoogleSignIn.getClient(activity, firebaseGoogleOptions()).signOut().addOnCompleteListener {
                    authRepository.signOut()
                    onAccountChanged()
                    toast("تم تسجيل الخروج بنجاح")
                }
            })
            action(translate("حذف الحساب"), translate("حذف الحساب وبياناته السحابية نهائياً"), run = { confirmAccountDeletion(user) })
        }
        val footer = TextView(activity).apply {
            text = "MARCO RX6  •  SECURE ACCOUNT"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.rgb(119, 157, 194) else Color.rgb(103, 133, 163))
            setPadding(0, dp(12), 0, dp(8))
        }
        page.addView(footer)
        showAccountPage(page)
    }

    /**
     * Clear the Firebase Google client's cached account before opening account selection.
     * This prevents a previous Google account from being silently reused after sign-out.
     */
    private fun startFreshGoogleAccountSelection() {
        GoogleSignIn.getClient(activity, firebaseGoogleOptions()).signOut()
            .addOnCompleteListener {
                launchGoogleSignIn(googleSignInIntent())
            }
    }

    private fun firebaseGoogleOptions() =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(activity.getString(com.marcorx6.library.R.string.default_web_client_id))
            .build()

    /** Uses the Firebase web OAuth client, separate from the Blogger/Drive OAuth client. */
    fun googleSignInIntent(): Intent {
        return GoogleSignIn.getClient(activity, firebaseGoogleOptions()).signInIntent
    }

    /**
     * Clear only the Blogger/Drive OAuth client selection before opening the account picker.
     * This client uses separate scopes/options from Firebase Google sign-in, so the Firebase
     * account session remains untouched. Google signOut clears the app selection, not device accounts.
     */
    private fun startAdminGoogleAccountSelection() {
        val adminClient = com.marcorx6.library.ui.GoogleSignInFactory.create(activity)
        adminClient.signOut().addOnCompleteListener {
            launchAdminGoogleSignIn(adminClient.signInIntent)
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        val accountTask = GoogleSignIn.getSignedInAccountFromIntent(data)
        accountTask.addOnSuccessListener { account ->
            val token = account.idToken
            if (token.isNullOrBlank()) {
                toast("تعذر الحصول على رمز Google. راجع إعداد OAuth في Firebase.")
                return@addOnSuccessListener
            }
            val current = authRepository.currentUser
            if (pendingDeletionUid != null && current != null && current.uid == pendingDeletionUid) {
                pendingDeletionUid = null
                current.reauthenticate(GoogleAuthProvider.getCredential(token, null))
                    .addOnSuccessListener { deleteAccountDataAndAuth(current) }
                    .addOnFailureListener { error -> toast(error.localizedMessage ?: "تعذرت إعادة التحقق من Google") }
                return@addOnSuccessListener
            }
            val authTask = if (current?.isAnonymous == true) {
                authRepository.linkGoogleToCurrentUser(token)
            } else {
                authRepository.signInWithGoogle(token)
            }
            authTask.addOnSuccessListener {
                toast("تم تسجيل الدخول بحساب Google بنجاح")
                onAccountChanged()
            }.addOnFailureListener { error ->
                toast(error.localizedMessage ?: "تعذر ربط حساب Google")
            }
        }.addOnFailureListener { error ->
            val apiError = error as? ApiException
            if (apiError?.statusCode == 12501) {
                pendingDeletionUid = null
            } else {
                pendingDeletionUid = null
                toast(error.localizedMessage ?: "فشل تسجيل الدخول باستخدام Google")
            }
        }
    }

    private fun createAccountFormPage(title: String, detail: String): LinearLayout {
        val dark = isDarkMode()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (translate("حساب MARCO RX6") == "حساب MARCO RX6") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(22), dp(20), dp(22), dp(28))
            setBackgroundColor(if (dark) Color.rgb(6, 16, 30) else Color.rgb(240, 247, 255))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(activity).apply {
            text = title
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.WHITE else Color.rgb(18, 42, 68))
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(TextView(activity).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(75, 165, 255))
            contentDescription = translate("رجوع")
            minWidth = dp(48)
            minHeight = dp(48)
            setOnClickListener { show() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header)
        page.addView(TextView(activity).apply {
            text = detail
            textSize = 12f
            setTextColor(if (dark) Color.rgb(183, 207, 231) else Color.rgb(75, 105, 136))
            setPadding(0, dp(4), 0, dp(22))
        })
        return page
    }

    private fun showAccountPage(page: LinearLayout) {
        val dark = isDarkMode()
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            addView(page)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val safe = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safe.top, 0, safe.bottom)
            insets
        }
        setContentView(scroll)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        androidx.core.view.ViewCompat.requestApplyInsets(scroll)
        applyUnifiedSkin(page)
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            page.alpha = 0f
            page.translationY = dp(8).toFloat()
            page.animate().alpha(1f).translationY(0f).setDuration(240L).start()
        }
    }

    private fun accountInput(hintText: String, inputType: Int): EditText {
        val dark = isDarkMode()
        return EditText(activity).apply {
            hint = hintText
            this.inputType = inputType
            setSingleLine(true)
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            textSize = 14f
            setTextColor(if (dark) Color.WHITE else Color.rgb(20, 45, 70))
            setHintTextColor(if (dark) Color.rgb(133, 164, 195) else Color.rgb(112, 139, 166))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (dark) Color.argb(155, 20, 45, 72) else Color.argb(220, 255, 255, 255))
                setStroke(dp(1), if (dark) Color.argb(120, 103, 164, 222) else Color.argb(160, 151, 190, 226))
            }
        }
    }

    private fun accountSubmitButton(label: String, onClick: (TextView) -> Unit): TextView {
        return TextView(activity).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            minHeight = dp(52)
            contentDescription = label
            setPadding(dp(18), dp(15), dp(18), dp(15))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(43, 151, 255), Color.rgb(22, 94, 190))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.argb(190, 135, 202, 255))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick(this) }
        }
    }

    private fun showPasswordResetForm() {
        val page = createAccountFormPage(
            translate("استرجاع كلمة المرور"),
            translate("دخل البريد الإلكتروني المرتبط بحسابك وسنرسل لك رابط إعادة التعيين.")
        )
        val email = accountInput(
            translate("البريد الإلكتروني"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        page.addView(email, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(14) })
        page.addView(accountSubmitButton(translate("إرسال رابط الاسترجاع")) { button ->
            val address = email.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                toast("صيغة البريد الإلكتروني غير صحيحة")
                return@accountSubmitButton
            }
            button.isEnabled = false
            authRepository.sendPasswordReset(address)
                .addOnSuccessListener {
                    toast("إلا كان الحساب موجود، غادي توصلك رسالة استرجاع كلمة المرور")
                    show()
                }
                .addOnFailureListener { error ->
                    button.isEnabled = true
                    toast(authErrorMessage(error, "تعذر إرسال رابط الاسترجاع"))
                }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        showAccountPage(page)
    }

    private fun showEmailForm(createAccount: Boolean) {
        val page = createAccountFormPage(
            if (createAccount) translate("إنشاء حساب جديد") else translate("تسجيل الدخول"),
            if (createAccount) translate("أنشئ حساب MARCO RX6 لحفظ ملفك ومزامنة بياناتك.")
            else translate("سجّل الدخول للعودة إلى حساب MARCO RX6.")
        )
        val email = accountInput(
            translate("البريد الإلكتروني"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val password = accountInput(
            translate("كلمة المرور"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        page.addView(email, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        page.addView(password, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        val confirmPassword = if (createAccount) {
            accountInput(
                translate("تأكيد كلمة المرور"),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ).also { field ->
                page.addView(field, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(14) })
            }
        } else null
        var passwordVisible = false
        val visibilityToggle = TextView(activity).apply {
            text = translate("إظهار كلمة المرور")
            textSize = 12f
            gravity = Gravity.END
            setTextColor(Color.rgb(75, 165, 255))
            minHeight = dp(48)
            contentDescription = translate("إظهار أو إخفاء كلمة المرور")
            setPadding(0, dp(3), 0, dp(12))
            setOnClickListener {
                passwordVisible = !passwordVisible
                val method = if (passwordVisible) HideReturnsTransformationMethod.getInstance()
                    else PasswordTransformationMethod.getInstance()
                password.transformationMethod = method
                confirmPassword?.transformationMethod = method
                password.setSelection(password.text.length)
                confirmPassword?.let { it.setSelection(it.text.length) }
                text = translate(if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور")
            }
        }
        page.addView(visibilityToggle)
        if (!createAccount) {
            val forgot = TextView(activity).apply {
                text = translate("نسيت كلمة المرور؟")
                textSize = 12f
                gravity = Gravity.END
                setTextColor(Color.rgb(75, 165, 255))
                minHeight = dp(48)
                contentDescription = translate("نسيت كلمة المرور؟")
                setPadding(0, dp(4), 0, dp(18))
                setOnClickListener { showPasswordResetForm() }
            }
            page.addView(forgot)
        }
        page.addView(accountSubmitButton(if (createAccount) translate("إنشاء الحساب") else translate("دخول")) { button ->
            val address = email.text.toString().trim()
            val secret = password.text.toString()
            if (!Patterns.EMAIL_ADDRESS.matcher(address).matches() || secret.isBlank()) {
                toast("دخل بريد إلكتروني صحيح وكلمة المرور")
                return@accountSubmitButton
            }
            if (createAccount && secret != confirmPassword?.text?.toString()) {
                toast("كلمتا المرور غير متطابقتين")
                return@accountSubmitButton
            }
            if (createAccount && secret.length < 6) {
                toast("كلمة المرور خاصها تكون 6 أحرف على الأقل")
                return@accountSubmitButton
            }
            button.isEnabled = false
            button.text = translate("جارٍ المعالجة...")
            val task = if (createAccount) {
                authRepository.createEmailAccount(address, secret)
            } else {
                authRepository.signInWithEmail(address, secret)
            }
            task.addOnSuccessListener { result ->
                if (createAccount) {
                    result.user?.sendEmailVerification()
                        ?.addOnSuccessListener { toast("تم إنشاء الحساب. تفقد بريدك لتأكيده.") }
                        ?.addOnFailureListener { toast("تم إنشاء الحساب، لكن تعذر إرسال رسالة التأكيد.") }
                } else {
                    toast("تم تسجيل الدخول بنجاح")
                }
                onAccountChanged()
            }.addOnFailureListener { error ->
                button.isEnabled = true
                button.text = if (createAccount) translate("إنشاء الحساب") else translate("دخول")
                toast(authErrorMessage(error, "تعذر إكمال العملية"))
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        showAccountPage(page)
    }

    private fun confirmAccountDeletion(user: FirebaseUser?) {
        if (user == null) {
            toast("ما كاين حتى حساب لحذفه دابا")
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(translate("حذف حساب MARCO RX6؟"))
            .setMessage(
                translate("هاد العملية نهائية: غادي نحذف ملف الحساب والإعدادات المحفوظة فـ Firebase، ومن بعد حساب Firebase نفسه. مكتبة الألعاب والملفات المحلية فالتيليفون، وحساب Google المسؤول ومحتوى Blogger وDrive، ما غاديش نمسوهم.")
            )
            .setNegativeButton(translate("إلغاء"), null)
            .setPositiveButton(translate("متابعة")) { _, _ ->
                when {
                    user.isAnonymous -> deleteAccountDataAndAuth(user)
                    user.providerData.any { it.providerId == "password" } -> showDeletePasswordPrompt(user)
                    user.providerData.any { it.providerId == "google.com" } -> reauthenticateGoogleForDeletion(user)
                    else -> toast("طريقة التحقق من هاد الحساب غير مدعومة حالياً. تواصل مع الدعم.")
                }
            }
            .show()
            .also(styleDialog)
    }

    private fun showDeletePasswordPrompt(user: FirebaseUser) {
        val password = EditText(activity).apply {
            hint = translate("كلمة المرور الحالية")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(translate("تأكيد هوية صاحب الحساب"))
            .setMessage(translate("دخل كلمة المرور الحالية باش نتحققو من هويتك قبل الحذف."))
            .setView(password)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton(translate("تحقق وحذف"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val secret = password.text.toString()
                val email = user.email
                if (secret.isBlank() || email.isNullOrBlank()) {
                    toast("دخل كلمة المرور الحالية")
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                user.reauthenticate(EmailAuthProvider.getCredential(email, secret))
                    .addOnSuccessListener {
                        dialog.dismiss()
                        deleteAccountDataAndAuth(user)
                    }
                    .addOnFailureListener { error ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        toast(error.localizedMessage ?: "فشل التحقق من كلمة المرور")
                    }
            }
        }
        dialog.show()
        styleDialog(dialog)
    }

    private fun reauthenticateGoogleForDeletion(user: FirebaseUser) {
        GoogleSignIn.getClient(activity, firebaseGoogleOptions()).silentSignIn()
            .addOnSuccessListener { account ->
                val token = account.idToken
                if (token.isNullOrBlank()) {
                    pendingDeletionUid = user.uid
                    launchGoogleSignIn(googleSignInIntent())
                    return@addOnSuccessListener
                }
                user.reauthenticate(GoogleAuthProvider.getCredential(token, null))
                    .addOnSuccessListener { deleteAccountDataAndAuth(user) }
                    .addOnFailureListener {
                        pendingDeletionUid = user.uid
                        launchGoogleSignIn(googleSignInIntent())
                    }
            }
            .addOnFailureListener {
                pendingDeletionUid = user.uid
                launchGoogleSignIn(googleSignInIntent())
            }
    }

    private fun deleteAccountDataAndAuth(user: FirebaseUser) {
        userDocuments.deleteUserData(user.uid)
            .addOnSuccessListener {
                authRepository.deleteCurrentUser()
                    .addOnSuccessListener {
                        // Do not sign out the shared GoogleSignIn account here: Blogger and
                        // Drive use GoogleSignIn.getLastSignedInAccount() for their own OAuth session.
                        // Deleting Firebase Authentication does not require clearing that Google session.
                        authRepository.signOut()
                        onAccountChanged()
                        toast("تم حذف حساب MARCO RX6 وبياناته السحابية بنجاح")
                    }
                    .addOnFailureListener { error ->
                        toast(error.localizedMessage ?: "تم حذف بيانات الحساب، لكن تعذر حذف حساب الدخول. تواصل مع الدعم.")
                    }
            }
            .addOnFailureListener { error ->
                toast(error.localizedMessage ?: "تعذر حذف بيانات الحساب؛ لم يتم حذف حساب الدخول")
            }
    }

    private fun authErrorMessage(error: Exception, fallback: String): String {
        val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "صيغة البريد الإلكتروني غير صحيحة"
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "البريد الإلكتروني أو كلمة المرور غير صحيحة"
            "ERROR_USER_NOT_FOUND" -> "لا يوجد حساب بهذا البريد الإلكتروني"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "هذا البريد مرتبط بحساب موجود. جرّب تسجيل الدخول"
            "ERROR_WEAK_PASSWORD" -> "كلمة المرور ضعيفة؛ استعمل 6 أحرف على الأقل"
            "ERROR_NETWORK_REQUEST_FAILED" -> "تحقق من اتصال الإنترنت وحاول مرة أخرى"
            "ERROR_TOO_MANY_REQUESTS" -> "محاولات كثيرة؛ انتظر قليلاً ثم أعد المحاولة"
            "ERROR_OPERATION_NOT_ALLOWED" -> "طريقة الدخول هذه غير مفعّلة حالياً"
            else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    private fun toast(message: String) {
        Toast.makeText(activity, translate(message), Toast.LENGTH_LONG).show()
    }
}
