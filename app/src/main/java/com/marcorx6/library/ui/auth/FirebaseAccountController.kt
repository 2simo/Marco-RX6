package com.marcorx6.library.ui.auth

import android.app.Activity
import android.content.Intent
import android.app.AlertDialog
import android.text.InputType
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.auth.FirebaseAuthRepository
import com.marcorx6.library.data.auth.FirebaseUserDocumentRepository
import com.google.firebase.auth.FirebaseUser
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
    private val applyUnifiedSkin: (View) -> Unit
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

    private fun showGuestActions() = showAccountScreen(null)

    private fun showAccountScreen(user: FirebaseUser?) {
        val dark = isDarkMode()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(if (dark) Color.rgb(6, 16, 30) else Color.rgb(240, 247, 255))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(activity).apply {
            text = "حساب MARCO RX6"
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
                user != null && !user.isAnonymous -> "مرحباً بعودتك"
                user?.isAnonymous == true -> "أنت تستعمل التطبيق كزائر"
                else -> "اختَر طريقة الدخول"
            }
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.WHITE else Color.rgb(17, 54, 89))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        hero.addView(stateTitle)
        val stateDetail = TextView(activity).apply {
            text = when {
                user != null && !user.isAnonymous -> user.email.orEmpty()
                user?.isAnonymous == true -> "يمكنك الاستمرار دون حساب أو ربط حسابك لاحقاً"
                else -> "احفظ ملفك الشخصي واستعمل مزايا الحساب على أجهزتك"
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
                isClickable = true
                isFocusable = true
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
            action("المتابعة كزائر", "استعمل المكتبة دون إنشاء حساب", run = onBack)
            action("الدخول باستخدام Google", "ربط حساب Google بحساب MARCO RX6", primary = true, run = ::startFreshGoogleAccountSelection)
            action("إنشاء حساب بالبريد الإلكتروني", "حساب جديد مع كلمة مرور", run = { showEmailForm(createAccount = true) })
            action("الدخول بالبريد الإلكتروني", "لديك حساب بالفعل؟", run = { showEmailForm(createAccount = false) })
            action("دخول المسؤول", "صلاحيات Blogger وGoogle Drive", run = ::startAdminGoogleAccountSelection)
            action("نسيت كلمة المرور؟", "استرجاع الوصول إلى حسابك", run = ::showPasswordResetForm)
            if (user?.isAnonymous == true) {
                action("حذف بيانات جلسة الزائر", "حذف ملف الزائر السحابي فقط", run = { confirmAccountDeletion(user) })
            }
        } else {
            action("حالة الحساب", if (user.isEmailVerified) "البريد الإلكتروني مؤكد" else "البريد الإلكتروني غير مؤكد", run = {
                if (!user.isEmailVerified) authRepository.sendEmailVerification()
                    ?.addOnSuccessListener { toast("تم إرسال رابط التأكيد إلى بريدك") }
                    ?.addOnFailureListener { toast("تعذر إرسال رابط التأكيد") }
            })
            if (user.providerData.any { it.providerId == "password" }) {
                action("استرجاع كلمة المرور", "إرسال رابط تغيير كلمة المرور", run = {
                    user.email?.let { address -> authRepository.sendPasswordReset(address)
                        .addOnSuccessListener { toast("تم إرسال رابط استرجاع كلمة المرور") }
                        .addOnFailureListener { toast("تعذر إرسال رابط الاسترجاع") } }
                })
            }
            action(if (isAdminSession()) "إيقاف وضع المسؤول" else "دخول المسؤول", "إدارة Blogger وGoogle Drive", primary = true, run = {
                if (isAdminSession()) signOutAdmin() else startAdminGoogleAccountSelection()
            })
            action("تسجيل الخروج", "الخروج من حساب MARCO RX6", run = {
                GoogleSignIn.getClient(activity, firebaseGoogleOptions()).signOut().addOnCompleteListener {
                    authRepository.signOut()
                    onAccountChanged()
                    toast("تم تسجيل الخروج بنجاح")
                }
            })
            action("حذف الحساب", "حذف الحساب وبياناته السحابية نهائياً", run = { confirmAccountDeletion(user) })
        }
        val footer = TextView(activity).apply {
            text = "MARCO RX6  •  SECURE ACCOUNT"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.rgb(119, 157, 194) else Color.rgb(103, 133, 163))
            setPadding(0, dp(12), 0, dp(8))
        }
        page.addView(footer)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            addView(page)
        }
        setContentView(scroll)
        applyUnifiedSkin(page)
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

    private fun showPasswordResetForm() {
        val email = EditText(activity).apply {
            hint = "البريد الإلكتروني"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("استرجاع كلمة المرور")
            .setMessage("غادي نصيفطو ليك رابط إعادة تعيين كلمة المرور.")
            .setView(email)
            .setPositiveButton("إرسال", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = email.text.toString().trim()
                if (address.isBlank()) {
                    toast("دخل البريد الإلكتروني")
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                authRepository.sendPasswordReset(address)
                    .addOnSuccessListener {
                        toast("إلا كان الحساب موجود، غادي توصلك رسالة استرجاع كلمة المرور")
                        dialog.dismiss()
                    }
                    .addOnFailureListener { error ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        toast(error.localizedMessage ?: "تعذر إرسال رابط الاسترجاع")
                    }
            }
        }
        dialog.show()
        styleDialog(dialog)
    }

    private fun showEmailForm(createAccount: Boolean) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
        }
        val email = EditText(activity).apply {
            hint = "البريد الإلكتروني"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val password = EditText(activity).apply {
            hint = "كلمة المرور"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val confirmPassword = if (createAccount) {
            EditText(activity).apply {
                hint = "تأكيد كلمة المرور"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
            }
        } else null

        container.addView(email, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(password, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        confirmPassword?.let {
            container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (createAccount) "إنشاء حساب جديد" else "تسجيل الدخول")
            .setView(container)
            .setPositiveButton(if (createAccount) "إنشاء الحساب" else "دخول", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = email.text.toString().trim()
                val secret = password.text.toString()
                if (address.isBlank() || secret.isBlank()) {
                    toast("دخل البريد الإلكتروني وكلمة المرور")
                    return@setOnClickListener
                }
                if (createAccount && secret != confirmPassword?.text?.toString()) {
                    toast("كلمتا المرور غير متطابقتين")
                    return@setOnClickListener
                }
                if (createAccount && secret.length < 6) {
                    toast("كلمة المرور خاصها تكون 6 أحرف على الأقل")
                    return@setOnClickListener
                }

                val task = if (createAccount) {
                    authRepository.createEmailAccount(address, secret)
                } else {
                    authRepository.signInWithEmail(address, secret)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                task.addOnSuccessListener { result ->
                    if (createAccount) {
                        result.user?.sendEmailVerification()
                            ?.addOnSuccessListener { toast("تم إنشاء الحساب. تفقد بريدك لتأكيده.") }
                            ?.addOnFailureListener { toast("تم إنشاء الحساب، لكن تعذر إرسال رسالة التأكيد.") }
                    } else {
                        toast("تم تسجيل الدخول بنجاح")
                    }
                    onAccountChanged()
                    dialog.dismiss()
                }.addOnFailureListener { error ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    toast(error.localizedMessage ?: "تعذر إكمال العملية")
                }
            }
        }
        dialog.show()
        styleDialog(dialog)
    }

    private fun showSignedInActions(user: FirebaseUser) = showAccountScreen(user)

    private fun confirmAccountDeletion(user: FirebaseUser?) {
        if (user == null) {
            toast("ما كاين حتى حساب لحذفه دابا")
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("حذف حساب MARCO RX6؟")
            .setMessage(
                "هاد العملية نهائية: غادي نحذف ملف الحساب والإعدادات المحفوظة فـ Firebase، ومن بعد حساب Firebase نفسه. " +
                    "مكتبة الألعاب والملفات المحلية فالتيليفون، وحساب Google المسؤول ومحتوى Blogger وDrive، ما غاديش نمسوهم."
            )
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("متابعة") { _, _ ->
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
            hint = "كلمة المرور الحالية"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("تأكيد هوية صاحب الحساب")
            .setMessage("دخل كلمة المرور الحالية باش نتحققو من هويتك قبل الحذف.")
            .setView(password)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تحقق وحذف", null)
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

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
