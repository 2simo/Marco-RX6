package com.marcorx6.library.ui.auth

import android.app.Activity
import android.content.Intent
import android.app.AlertDialog
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
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
    private val styleDialog: (AlertDialog) -> Unit
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

    private fun showGuestActions() {
        AlertDialog.Builder(activity)
            .setTitle("أنت الآن تستعمل MARCO RX6 كزائر")
            .setItems(arrayOf(
                "إنشاء حساب جديد بالبريد الإلكتروني",
                "تسجيل الدخول بالبريد الإلكتروني",
                "إنشاء / اختيار حساب Google",
                "تسجيل دخول المسؤول (Blogger و Drive)",
                "نسيت كلمة المرور؟",
                "حذف بيانات حساب الزائر",
                "إغلاق"
            )) { _, which ->
                when (which) {
                    0 -> showEmailForm(createAccount = true)
                    1 -> showEmailForm(createAccount = false)
                    2 -> startFreshGoogleAccountSelection()
                    3 -> launchAdminGoogleSignIn(googleSignInIntentForAdmin())
                    4 -> showPasswordResetForm()
                    5 -> confirmAccountDeletion(authRepository.currentUser)
                }
            }
            .show()
            .also(styleDialog)
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

    /** Existing admin OAuth requests Blogger/Drive scopes and remains separate from Firebase auth. */
    private fun googleSignInIntentForAdmin(): Intent {
        return com.marcorx6.library.ui.GoogleSignInFactory.create(activity).signInIntent
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
            if (pendingDeletionUid != null && current?.uid == pendingDeletionUid) {
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

    private fun showSignedInActions(user: FirebaseUser) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (!user.isEmailVerified && !user.email.isNullOrBlank()) {
            actions += "إرسال رابط تأكيد البريد" to {
                authRepository.sendEmailVerification()
                    ?.addOnSuccessListener { toast("تم إرسال رابط التأكيد إلى بريدك") }
                    ?.addOnFailureListener { toast("تعذر إرسال رابط التأكيد") }
            }
        }
        if (user.providerData.any { it.providerId == "password" }) {
            actions += "استرجاع كلمة المرور" to {
                user.email?.let { address ->
                    authRepository.sendPasswordReset(address)
                        .addOnSuccessListener { toast("تم إرسال رابط استرجاع كلمة المرور") }
                        .addOnFailureListener { toast("تعذر إرسال رابط الاسترجاع") }
                }
            }
        }
        actions += "حذف حساب MARCO RX6 وبياناته" to { confirmAccountDeletion(user) }
        actions += (if (isAdminSession()) "تسجيل الخروج من وضع المسؤول" else "تسجيل دخول المسؤول (Blogger و Drive)") to {
            if (isAdminSession()) signOutAdmin() else launchAdminGoogleSignIn(googleSignInIntentForAdmin())
        }
        // Keep one clear, prominent Firebase sign-out action in the signed-in account menu.
        actions.add(0, "تسجيل الخروج من حساب MARCO RX6" to {
            GoogleSignIn.getClient(activity, firebaseGoogleOptions()).signOut()
                .addOnCompleteListener {
                    authRepository.signOut()
                    onAccountChanged()
                    toast("تم تسجيل الخروج بنجاح")
                }
        })
        actions += "إغلاق" to {}

        AlertDialog.Builder(activity)
            .setTitle((user.email ?: "حساب MARCO RX6") + if (isAdminSession()) " • مسؤول" else " • مستخدم")
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.second?.invoke()
            }
            .show()
            .also(styleDialog)
    }

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
                        GoogleSignIn.getClient(activity, firebaseGoogleOptions()).signOut()
                            .addOnCompleteListener {
                                authRepository.signOut()
                                onAccountChanged()
                                toast("تم حذف حساب MARCO RX6 وبياناته السحابية بنجاح")
                            }
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
