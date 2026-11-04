package com.marcorx6.library.ui.auth

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.marcorx6.library.data.auth.FirebaseAuthRepository
import com.google.firebase.auth.FirebaseUser

/**
 * Email account UI for the optional Firebase account.
 * It does not use or sign out the separate Google OAuth session for Blogger and Drive.
 */
internal class FirebaseAccountController(
    private val activity: Activity,
    private val authRepository: FirebaseAuthRepository,
    private val onAccountChanged: () -> Unit,
    private val styleDialog: (AlertDialog) -> Unit
) {
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
            .setTitle("حساب MARCO RX6")
            .setMessage("أنت تستعمل التطبيق كزائر. تقدر تنشئ حساباً أو تدخل لحسابك في أي وقت.")
            .setItems(arrayOf("إنشاء حساب بالبريد الإلكتروني", "تسجيل الدخول بالبريد الإلكتروني", "نسيت كلمة المرور؟", "إغلاق")) { _, which ->
                when (which) {
                    0 -> showEmailForm(createAccount = true)
                    1 -> showEmailForm(createAccount = false)
                    2 -> showPasswordResetForm()
                }
            }
            .show()
            .also(styleDialog)
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
        actions += "تسجيل الخروج" to {
            authRepository.signOut()
            onAccountChanged()
            toast("تم تسجيل الخروج")
        }
        actions += "إغلاق" to {}

        AlertDialog.Builder(activity)
            .setTitle("حساب MARCO RX6")
            .setMessage(user.email ?: "حساب مرتبط")
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.second?.invoke()
            }
            .show()
            .also(styleDialog)
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
