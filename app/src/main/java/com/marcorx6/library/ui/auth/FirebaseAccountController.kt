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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
        val dark = isDarkMode()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            setBackgroundColor(if (dark) Color.rgb(6, 16, 30) else Color.rgb(240, 247, 255))
        }
        val user = authRepository.currentUser
        page.addView(TextView(activity).apply {
            text = if (user == null || user.isAnonymous) translate("حساب MARCO RX6")
                else translate("حسابك في MARCO RX6")
            textSize = 25f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.WHITE else Color.rgb(18, 42, 68))
            setPadding(0, 0, 0, dp(8))
        })
        if (user == null || user.isAnonymous) {
            page.addView(TextView(activity).apply {
                text = translate("استعمل التطبيق كزائر، أو سجّل الدخول لحفظ حسابك ومزامنة بياناتك.")
                textSize = 13f
                setTextColor(if (dark) Color.rgb(183, 207, 231) else Color.rgb(75, 105, 136))
                setPadding(0, 0, 0, dp(18))
            })
            action(page, translate("المتابعة كزائر"), translate("العودة إلى المكتبة دون إنشاء ملف شخصي"), dark, run = onBack)
            action(page, translate("إنشاء حساب باستخدام Google"), translate("إنشاء حساب جديد أو استخدام حساب Google موجود"), dark, primary = true, run = ::startFreshGoogleAccountSelection)
            action(page, translate("إنشاء حساب بالبريد الإلكتروني"), translate("استعمل بريدك الإلكتروني وكلمة مرور"), dark, run = { showEmailForm(createAccount = true) })
            action(page, translate("تسجيل الدخول بالبريد الإلكتروني"), translate("لديك حساب MARCO RX6 بالفعل؟"), dark, run = { showEmailForm(createAccount = false) })
            action(page, translate("نسيت كلمة المرور؟"), translate("استرجاع الوصول إلى حسابك"), dark, run = ::showPasswordResetForm)
        } else {
            val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty()
            page.addView(TextView(activity).apply {
                text = displayName
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(18, 42, 68))
                setPadding(0, dp(4), 0, dp(4))
            })
            user.email?.let { email ->
                page.addView(TextView(activity).apply {
                    text = email
                    textSize = 13f
                    setTextColor(if (dark) Color.rgb(160, 190, 218) else Color.rgb(75, 105, 136))
                    setPadding(0, 0, 0, dp(20))
                })
            }
            action(page, translate("تسجيل الخروج"), translate("الخروج من حساب MARCO RX6"), dark, run = {
                authRepository.signOut()
                onAccountChanged()
                toast("تم تسجيل الخروج بنجاح")
            })
            action(page, translate("مسح الحساب"), translate("حذف حسابك وبياناته السحابية نهائياً"), dark, run = { confirmAccountDeletion(user) })
        }
        page.addView(TextView(activity).apply {
            text = "MARCO RX6  •  SECURE ACCOUNT"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.rgb(119, 157, 194) else Color.rgb(103, 133, 163))
            setPadding(0, dp(18), 0, dp(8))
        })
        showAccountPage(page)
    }

    private fun action(
        page: LinearLayout,
        title: String,
        detail: String,
        dark: Boolean,
        primary: Boolean = false,
        run: () -> Unit
    ) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(17), dp(14), dp(17), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (primary) Color.rgb(24, 111, 205) else if (dark) Color.rgb(19, 39, 61) else Color.WHITE)
                setStroke(dp(1), if (primary) Color.rgb(87, 174, 255) else if (dark) Color.rgb(49, 79, 109) else Color.rgb(205, 224, 243))
            }
            setOnClickListener { run() }
        }
        card.addView(TextView(activity).apply {
            text = title
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (primary || dark) Color.WHITE else Color.rgb(18, 42, 68))
        })
        card.addView(TextView(activity).apply {
            text = detail
            textSize = 11f
            setTextColor(if (primary) Color.rgb(225, 240, 255) else if (dark) Color.rgb(160, 190, 218) else Color.rgb(87, 116, 145))
            setPadding(0, dp(5), 0, 0)
        })
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
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
                completeGoogleAccountSignIn("تم تسجيل الدخول بحساب Google بنجاح")
            }.addOnFailureListener { error ->
                // If this Google identity already belongs to a Firebase account,
                // sign into that existing account instead of leaving the visitor stuck.
                if (current?.isAnonymous == true && error is FirebaseAuthUserCollisionException) {
                    authRepository.signInWithGoogle(token)
                        .addOnSuccessListener {
                            completeGoogleAccountSignIn("تم الدخول إلى حساب Google الموجود. جارٍ التحقق من صلاحيات Blogger…")
                        }
                        .addOnFailureListener { signInError ->
                            toast(authErrorMessage(signInError, "تعذر تسجيل الدخول إلى حساب Google الموجود"))
                        }
                } else {
                    toast(authErrorMessage(error, "تعذر ربط حساب Google"))
                }
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
            minWidth = dp(48)
            contentDescription = translate("إظهار كلمة المرور")
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(72, 75, 165, 255)),
                null,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
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
                contentDescription = text
                sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED)
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
                minWidth = dp(48)
                contentDescription = translate("نسيت كلمة المرور؟")
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(72, 75, 165, 255)),
                    null,
                    android.graphics.drawable.ColorDrawable(Color.WHITE)
                )
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

    /** Complete the normal Firebase account flow without requesting Blogger or Drive scopes. */
    private fun completeGoogleAccountSignIn(message: String) {
        toast(message)
        onAccountChanged()
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
