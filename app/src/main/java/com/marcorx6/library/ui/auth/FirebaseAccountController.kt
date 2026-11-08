package com.marcorx6.library.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.auth.FirebaseAuthRepository
import com.marcorx6.library.data.auth.FirebaseUserDocumentRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
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
    private val onSignedIn: () -> Unit,
    private val launchGoogleSignIn: (Intent) -> Unit,
    private val launchAdminGoogleSignIn: (Intent) -> Unit,
    private val signOutAdmin: () -> Unit,
    private val styleDialog: (AlertDialog) -> Unit,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit,
    private val dp: (Int) -> Int,
    private val isDarkMode: () -> Boolean,
    private val applyUnifiedSkin: (View) -> Unit,
    private val launchProfilePhotoPicker: (Intent) -> Unit,
    private val translate: (String) -> String,
    private val games: () -> List<Game>
) {
    private val userDocuments = FirebaseUserDocumentRepository()
    private var pendingDeletionUid: String? = null

    fun show() {
        val dark = isDarkMode()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            setBackgroundColor(if (dark) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247))
        }
        val user = authRepository.currentUser
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = translate("رجوع")
            isClickable = true
            isFocusable = true
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(activity).apply {
            text = if (user == null || user.isAnonymous) translate("حساب MARCO")
                else translate("حسابك في MARCO")
            textSize = 25f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
            setPadding(dp(8), 0, 0, dp(8))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(header)
        page.addView(TextView(activity).apply {
            text = translate("ملفك الشخصي • MARCO RX6")
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
            setTextColor(if (dark) Color.rgb(135, 145, 160) else Color.rgb(105, 114, 128))
            setPadding(dp(56), 0, 0, dp(8))
        })

        // Keep the avatar and account identity together as one profile header.
        val photoView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) Color.rgb(35, 38, 45) else Color.rgb(222, 226, 232))
                setStroke(dp(2), Color.rgb(190, 198, 210))
            }
            clipToOutline = true
            contentDescription = translate("صورة الحساب")
            setImageResource(com.marcorx6.library.R.drawable.ic_nav_profile)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
        }
        // A compact identity panel makes the account state immediately clear.
        val identityCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (dark) intArrayOf(Color.rgb(34, 38, 46), Color.rgb(15, 17, 22))
                else intArrayOf(Color.WHITE, Color.rgb(229, 233, 239))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(105, 115, 130) else Color.rgb(180, 189, 202))
            }
        }
        if (user != null && !user.isAnonymous) {
            identityCard.addView(photoView, LinearLayout.LayoutParams(dp(100), dp(100)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            })
        }
        if (user == null || user.isAnonymous) {
            identityCard.addView(TextView(activity).apply {
                text = translate("MARCO RX6")
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.16f
                setTextColor(Color.rgb(190, 198, 210))
            })
            identityCard.addView(TextView(activity).apply {
                text = translate("مرحباً بك في مجتمع MARCO")
                textSize = 19f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                setPadding(0, dp(7), 0, dp(4))
            })
            identityCard.addView(TextView(activity).apply {
                text = translate("أنت تستخدم التطبيق كزائر. أنشئ حساباً لحفظ ملفك الشخصي والوصول إليه لاحقاً.")
                textSize = 12f
                setTextColor(if (dark) Color.rgb(175, 182, 193) else Color.rgb(91, 98, 110))
            })
        } else {
            val providerLabel = when {
                user.providerData.any { it.providerId == "google.com" } -> translate("حساب Google")
                user.providerData.any { it.providerId == "password" } -> translate("حساب بالبريد الإلكتروني")
                else -> translate("حساب MARCO")
            }
            identityCard.addView(TextView(activity).apply {
                text = translate("ملف MARCO الشخصي")
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.12f
                setTextColor(Color.rgb(190, 198, 210))
            })
            identityCard.addView(TextView(activity).apply {
                text = user.displayName?.takeIf { it.isNotBlank() } ?: translate("مستخدم MARCO")
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                setPadding(0, dp(7), 0, dp(5))
            })
            identityCard.addView(TextView(activity).apply {
                text = user.email.orEmpty()
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (dark) Color.rgb(175, 182, 193) else Color.rgb(91, 98, 110))
            })
            identityCard.addView(TextView(activity).apply {
                text = providerLabel + "  •  " + translate(
                    if (user.isEmailVerified) "البريد مؤكد" else "البريد غير مؤكد"
                )
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(190, 198, 210))
                setPadding(0, dp(9), 0, 0)
            })

            val createdAt = user.metadata?.creationTimestamp ?: 0L
            if (createdAt > 0L) {
                val memberDate = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    .format(java.util.Date(createdAt))
                identityCard.addView(TextView(activity).apply {
                    text = translate("عضو منذ") + "  " + memberDate
                    textSize = 11f
                    setTextColor(if (dark) Color.rgb(150, 158, 171) else Color.rgb(105, 112, 124))
                    setPadding(0, dp(7), 0, 0)
                })
            }
        }
        page.addView(identityCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
            bottomMargin = dp(16)
        })

        if (user != null && !user.isAnonymous) {
            // These figures describe the locally available library, not cloud-account-wide activity.
            val libraryGames = games()
            val favoriteCount = libraryGames.count { it.isFavorite }
            page.addView(sectionCaption("نظرة عامة على مكتبتك", dark))
            val statsCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(17), dp(16), dp(17), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(if (dark) Color.rgb(21, 24, 30) else Color.WHITE)
                    setStroke(dp(1), if (dark) Color.rgb(82, 91, 105) else Color.rgb(199, 207, 218))
                }
                elevation = dp(2).toFloat()
            }
            statsCard.addView(TextView(activity).apply {
                text = translate("مكتبتك على هذا الجهاز")
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.rgb(190, 198, 210) else Color.rgb(75, 83, 96))
                setPadding(0, 0, 0, dp(10))
            })
            val metrics = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            fun addMetric(value: Int, label: String) {
                val metric = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                metric.addView(TextView(activity).apply {
                    text = value.toString()
                    textSize = 23f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                })
                metric.addView(TextView(activity).apply {
                    text = translate(label)
                    textSize = 11f
                    setTextColor(if (dark) Color.rgb(150, 158, 171) else Color.rgb(105, 112, 124))
                })
                metrics.addView(metric, LinearLayout.LayoutParams(0, -2, 1f))
            }
            addMetric(libraryGames.size, "الألعاب")
            addMetric(favoriteCount, "المفضلة")
            statsCard.addView(metrics)
            val favoritePreview = libraryGames.filter { it.isFavorite }.take(3)
            statsCard.addView(TextView(activity).apply {
                text = translate("ألعابك المفضلة")
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.rgb(190, 198, 210) else Color.rgb(75, 83, 96))
                setPadding(0, dp(8), 0, dp(5))
            })
            val favoritesText = when {
                favoritePreview.isEmpty() -> translate("لم تضف ألعاباً إلى المفضلة بعد.")
                else -> favoritePreview.joinToString("\n") { "★  ${it.name}" } +
                    if (favoriteCount > favoritePreview.size) {
                        "\n" + translate("و") + " ${favoriteCount - favoritePreview.size} " + translate("ألعاب أخرى")
                    } else ""
            }
            statsCard.addView(TextView(activity).apply {
                text = favoritesText
                textSize = 12f
                setLineSpacing(dp(3).toFloat(), 1f)
                setTextColor(if (dark) Color.rgb(160, 168, 181) else Color.rgb(91, 98, 110))
            })
            page.addView(statsCard, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            })
            val profileBioPreview = TextView(activity).apply {
                textSize = 13f
                setTextColor(if (dark) Color.rgb(190, 198, 210) else Color.rgb(75, 83, 96))
                setPadding(dp(15), dp(12), dp(15), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (dark) Color.rgb(22, 24, 29) else Color.WHITE)
                    setStroke(dp(1), if (dark) Color.rgb(76, 83, 94) else Color.rgb(205, 210, 218))
                }
                visibility = View.GONE
                contentDescription = translate("نبذة الحساب")
                tag = "marco-profile-bio-preview"
            }
            page.addView(profileBioPreview, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            })
            page.addView(sectionCaption("ملخص المكتبة", dark))
        }

        if (user == null || user.isAnonymous) {
            page.addView(sectionCaption("اختر طريقة المتابعة", dark))
            page.addView(TextView(activity).apply {
                text = translate("استعمل التطبيق كزائر، أو سجّل الدخول لحفظ ملفك الشخصي والعودة إليه من أجهزتك.")
                textSize = 13f
                setTextColor(if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110))
                setPadding(0, 0, 0, dp(18))
            })
            action(page, translate("المتابعة كزائر"), translate("العودة إلى المكتبة دون إنشاء ملف شخصي"), dark, run = onBack)
            action(page, translate("إنشاء حساب باستخدام Google"), translate("إنشاء حساب جديد أو استخدام حساب Google موجود"), dark, primary = true, run = ::startFreshGoogleAccountSelection)
            action(page, translate("إنشاء حساب بالبريد الإلكتروني"), translate("استعمل بريدك الإلكتروني وكلمة مرور"), dark, run = { showEmailForm(createAccount = true) })
            action(page, translate("تسجيل الدخول بالبريد الإلكتروني"), translate("لديك حساب MARCO بالفعل؟"), dark, run = { showEmailForm(createAccount = false) })
            action(page, translate("نسيت كلمة المرور؟"), translate("استرجاع الوصول إلى حسابك"), dark, run = ::showPasswordResetForm)
        } else {
            page.addView(sectionCaption("الأمان وإدارة الحساب", dark))
            action(page, translate("تغيير صورة الحساب"), translate("اختيار صورة من هاتفك وحفظها في ملفك السحابي"), dark, run = ::chooseProfilePhoto)
            if (!user.isEmailVerified && user.email != null) {
                action(
                    page,
                    translate("تأكيد البريد الإلكتروني"),
                    translate("إرسال رسالة تحقق جديدة إلى بريدك"),
                    dark,
                    run = {
                        user.sendEmailVerification()
                            .addOnSuccessListener { toast("تم إرسال رسالة تأكيد البريد الإلكتروني") }
                            .addOnFailureListener { toast("تعذر إرسال رسالة التأكيد") }
                    }
                )
            }
            if (user.email != null && user.providerData.any { it.providerId == "password" }) {
                action(
                    page,
                    translate("إعادة تعيين كلمة المرور"),
                    translate("إرسال رابط آمن لتغيير كلمة مرور الحساب"),
                    dark,
                    run = {
                        authRepository.sendPasswordReset(user.email!!)
                            .addOnSuccessListener { toast("إذا كان الحساب موجوداً، فستصلك رسالة إعادة التعيين.") }
                            .addOnFailureListener { toast("تعذر إرسال رابط إعادة تعيين كلمة المرور") }
                    }
                )
            }

            page.addView(sectionCaption("بيانات الملف الشخصي", dark))
            page.addView(TextView(activity).apply {
                text = translate("الاسم")
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                setPadding(0, dp(8), 0, dp(6))
            })
            val nameInput = accountInput(
                translate("اسم المستخدم"),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            ).apply {
                filters = arrayOf(android.text.InputFilter.LengthFilter(40))
                setText(user.displayName.orEmpty())
                setSelection(text.length)
            }
            page.addView(nameInput, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(14) })

            page.addView(TextView(activity).apply {
                text = translate("نبذة عنك")
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                setPadding(0, dp(4), 0, dp(6))
            })
            val bioInput = EditText(activity).apply {
                hint = translate("اكتب نبذة قصيرة عنك...")
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine(false)
                filters = arrayOf(android.text.InputFilter.LengthFilter(160))
                minLines = 3
                maxLines = 5
                gravity = Gravity.TOP or Gravity.START
                textSize = 14f
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                setHintTextColor(if (dark) Color.rgb(170, 176, 186) else Color.rgb(91, 98, 110))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (dark) Color.argb(155, 25, 28, 34) else Color.argb(220, 255, 255, 255))
                    setStroke(dp(1), if (dark) Color.argb(120, 130, 138, 151) else Color.argb(160, 160, 168, 181))
                }
            }
            page.addView(bioInput, LinearLayout.LayoutParams(-1, dp(124)).apply { bottomMargin = dp(2) })
            val bioCounter = TextView(activity).apply {
                text = "0/160"
                textSize = 10f
                gravity = Gravity.END
                setTextColor(if (dark) Color.rgb(135, 143, 156) else Color.rgb(112, 120, 132))
                setPadding(0, dp(2), dp(4), dp(8))
                contentDescription = translate("عدد أحرف النبذة")
            }
            page.addView(bioCounter, LinearLayout.LayoutParams(-1, -2))
            bioInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    bioCounter.text = "${s?.length ?: 0}/160"
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })

            var profilePhotoData: String? = null
            var profileLoaded = false
            val saveButton = accountSubmitButton(translate("حفظ التغييرات")) { button ->
                val name = nameInput.text.toString().trim()
                val bio = bioInput.text.toString().trim()
                if (name.isBlank() || name.length > 40) {
                    toast("الاسم مطلوب ويجب ألا يتجاوز 40 حرفاً.")
                    return@accountSubmitButton
                }
                if (bio.length > 160) {
                    toast("يجب ألا تتجاوز النبذة 160 حرفاً.")
                    return@accountSubmitButton
                }
                if (!profileLoaded) {
                    toast("جارٍ تحميل معلومات الملف الشخصي، يُرجى المحاولة مجدداً بعد لحظة.")
                    return@accountSubmitButton
                }
                button.isEnabled = false
                button.text = translate("جارٍ حفظ التغييرات...")
                val previousDisplayName = user.displayName
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(name).build()
                ).addOnSuccessListener {
                    userDocuments.saveProfile(
                        uid = user.uid,
                        displayName = name,
                        bio = bio,
                        photoUrl = user.photoUrl?.toString(),
                        photoData = profilePhotoData
                    ).addOnSuccessListener {
                        toast("تم حفظ معلومات الملف الشخصي")
                        show()
                    }.addOnFailureListener { error ->
                        // Keep the edited fields on screen and restore the Firebase Auth name
                        // so a cloud-write failure does not leave two conflicting profiles.
                        user.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(previousDisplayName)
                                .build()
                        ).addOnCompleteListener {
                            button.isEnabled = true
                            button.text = translate("حفظ التغييرات")
                            toast("تعذر حفظ الملف الشخصي في السحابة. بقيت تعديلاتك في الصفحة؛ حاول مجدداً.")
                        }
                    }
                }.addOnFailureListener { error ->
                    button.isEnabled = true
                    button.text = translate("حفظ التغييرات")
                    toast("تعذر تحديث اسم الحساب. لم تُحذف تعديلاتك.")
                }
            }
            saveButton.isEnabled = false
            page.addView(saveButton, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(20) })

            userDocuments.loadUser(user.uid)
                .addOnSuccessListener { snapshot ->
                    profilePhotoData = snapshot.getString("photoData")
                    val savedBio = snapshot.getString("bio").orEmpty().trim()
                    bioInput.setText(savedBio)
                    if (savedBio.isNotBlank()) {
                        // Reflect the saved profile introduction in the account overview.
                        page.findViewWithTag<TextView>("marco-profile-bio-preview")?.let { preview ->
                            preview.text = savedBio
                            preview.visibility = View.VISIBLE
                        }
                    }
                    profilePhotoData?.let { encoded ->
                        runCatching {
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()?.let { bitmap ->
                            photoView.setImageBitmap(bitmap)
                            photoView.imageTintList = null
                            photoView.clearColorFilter()
                        }
                    }
                    if (profilePhotoData.isNullOrBlank()) {
                        // Load the provider avatar only when no user-selected cloud photo exists.
                        user.photoUrl?.toString()?.takeIf { it.startsWith("https://") }?.let { photoUrl ->
                            Thread {
                                val bitmap = runCatching {
                                    val connection = java.net.URL(photoUrl).openConnection().apply {
                                        connectTimeout = 6000
                                        readTimeout = 6000
                                        useCaches = true
                                    }
                                    connection.getInputStream().use { stream -> BitmapFactory.decodeStream(stream) }
                                }.getOrNull()
                                if (bitmap != null && !activity.isFinishing && !activity.isDestroyed) {
                                    photoView.post {
                                        photoView.setImageBitmap(bitmap)
                                        photoView.imageTintList = null
                                        photoView.clearColorFilter()
                                    }
                                }
                            }.apply { name = "marco-profile-avatar-loader" }.start()
                        }
                    }
                    profileLoaded = true
                    saveButton.isEnabled = true
                }
                .addOnFailureListener { error ->
                    toast("تعذر تحميل الملف الشخصي")
                }

            user.email?.let { email ->
                page.addView(sectionCaption("معلومات الاتصال", dark))
                val emailRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(if (dark) Color.rgb(22, 24, 29) else Color.WHITE)
                        setStroke(dp(1), if (dark) Color.rgb(76, 83, 94) else Color.rgb(205, 210, 218))
                    }
                }
                emailRow.addView(TextView(activity).apply {
                    text = email
                    textSize = 12f
                    setTextColor(if (dark) Color.rgb(190, 198, 210) else Color.rgb(65, 72, 84))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val copyEmail = TextView(activity).apply {
                    text = translate("نسخ")
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.rgb(190, 198, 210))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MARCO account email", email))
                        toast("تم نسخ البريد الإلكتروني")
                    }
                }
                emailRow.addView(copyEmail)
                page.addView(emailRow, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(16)
                })
            }
            action(
                page,
                translate("تسجيل الخروج"),
                translate("الخروج من حساب MARCO مع إبقاء جلسة Blogger وGoogle Drive منفصلة"),
                dark,
                run = {
                    val logoutDialog = AlertDialog.Builder(activity)
                        .setTitle(translate("تسجيل الخروج من MARCO؟"))
                        .setMessage(
                            translate("سيتم تسجيل الخروج من حساب MARCO فقط. ستبقى جلسة Google المستعملة في Blogger وGoogle Drive كما هي، ولن تُحذف مكتبتك أو ملفات التنزيل على هذا الهاتف.")
                        )
                        .setNegativeButton(translate("إلغاء"), null)
                        .setPositiveButton(translate("تسجيل الخروج")) { _, _ ->
                            authRepository.signOut()
                            signOutAdmin()
                            onAccountChanged()
                            toast("تم تسجيل الخروج بنجاح")
                        }
                        .create()
                    logoutDialog.show()
                    styleDialog(logoutDialog)
                }
            )
            action(page, translate("مسح الحساب"), translate("حذف حسابك وبياناته السحابية نهائياً"), dark, run = { confirmAccountDeletion(user) })
        }
        page.addView(TextView(activity).apply {
            text = "MARCO  •  SECURE ACCOUNT"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.rgb(145, 153, 166) else Color.rgb(91, 98, 110))
            setPadding(0, dp(18), 0, dp(8))
        })
        showAccountPage(page)
    }

    private fun chooseProfilePhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        launchProfilePhotoPicker(intent)
    }

    private var profilePhotoUploadInProgress = false

    fun handleProfilePhotoSelected(uri: Uri) {
        val user = authRepository.currentUser
        if (user == null || user.isAnonymous) {
            toast("سجّل الدخول أولاً لإضافة صورة الحساب")
            return
        }
        if (profilePhotoUploadInProgress) {
            toast("جارٍ حفظ صورة الحساب بالفعل، انتظر قليلاً.")
            return
        }
        profilePhotoUploadInProgress = true
        try {
            val encoded = compressProfilePhoto(uri)
            userDocuments.saveProfile(
                uid = user.uid,
                displayName = user.displayName,
                bio = null,
                photoUrl = user.photoUrl?.toString(),
                photoData = encoded
            ).addOnSuccessListener {
                profilePhotoUploadInProgress = false
                toast("تم تحديث صورة الحساب")
                show()
            }.addOnFailureListener { error ->
                profilePhotoUploadInProgress = false
                toast("تعذر حفظ صورة الحساب. بقيت صورتك السابقة كما هي؛ حاول مجدداً.")
            }
        } catch (error: Exception) {
            profilePhotoUploadInProgress = false
            toast("تعذرت قراءة الصورة المختارة؛ لم تتغير صورة حسابك.")
        }
    }

    private fun compressProfilePhoto(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        activity.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "تعذرت قراءة الصورة" }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "الملف المختار ليس صورة صالحة" }
        var sample = 1
        while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = activity.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "تعذرت قراءة الصورة" }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("تعذر فك ترميز الصورة")
        val scaled = if (bitmap.width > 384 || bitmap.height > 384) {
            val ratio = minOf(384f / bitmap.width, 384f / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else bitmap
        if (scaled !== bitmap) bitmap.recycle()
        val output = java.io.ByteArrayOutputStream()
        var quality = 82
        do {
            output.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            quality -= 8
        } while (output.size() > 420_000 && quality >= 42)
        scaled.recycle()
        require(output.size() <= 450_000) { "الصورة كبيرة جداً، اختر صورة أصغر." }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
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
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable(
                if (primary) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.TL_BR,
                if (primary) intArrayOf(Color.rgb(218, 224, 233), Color.rgb(155, 166, 182))
                else if (dark) intArrayOf(Color.rgb(31, 35, 43), Color.rgb(20, 23, 29))
                else intArrayOf(Color.WHITE, Color.rgb(239, 242, 247))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), if (primary) Color.rgb(145, 156, 173) else if (dark) Color.rgb(83, 92, 106) else Color.rgb(194, 202, 214))
            }
            elevation = dp(1).toFloat()
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(55, 190, 198, 210)),
                null,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
            setOnClickListener { run() }
        }
        card.addView(TextView(activity).apply {
            text = title
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (primary) Color.rgb(20, 23, 29) else if (dark) Color.WHITE else Color.rgb(25, 29, 36))
        })
        card.addView(TextView(activity).apply {
            text = detail
            textSize = 11f
            setTextColor(if (primary) Color.rgb(20, 23, 29) else if (dark) Color.rgb(160, 168, 181) else Color.rgb(91, 98, 110))
            setPadding(0, dp(5), 0, 0)
        })
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun sectionCaption(label: String, dark: Boolean): TextView =
        TextView(activity).apply {
            text = translate(label)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.rgb(205, 212, 222) else Color.rgb(65, 73, 86))
            setPadding(dp(2), dp(4), dp(2), dp(10))
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
    fun connectAdminAccount() {
        startAdminGoogleAccountSelection()
    }

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
            layoutDirection = if (translate("حساب MARCO") == "حساب MARCO") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(22), dp(20), dp(22), dp(28))
            setBackgroundColor(if (dark) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(activity).apply {
            text = title
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = translate("رجوع")
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(72, 190, 198, 210)),
                null,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
            setOnClickListener { show() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header)
        page.addView(TextView(activity).apply {
            text = detail
            textSize = 12f
            setTextColor(if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110))
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
        // Do not start a second page animator after MainActivity has already
        // applied the screen motion policy; competing animators caused a visible flash.
        page.animate().cancel()
        page.clearAnimation()
        page.alpha = 1f
        page.translationY = 0f
        scroll.animate().cancel()
        scroll.clearAnimation()
        scroll.alpha = 1f
        scroll.translationY = 0f
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
        // The screen is installed by the activity's shared navigation path.
        // Keep it at its final state instead of restarting an overlapping fade.
        page.animate().cancel()
        page.alpha = 1f
        page.translationY = 0f
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
            setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
            setHintTextColor(if (dark) Color.rgb(170, 176, 186) else Color.rgb(91, 98, 110))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (dark) Color.argb(155, 25, 28, 34) else Color.argb(220, 255, 255, 255))
                setStroke(dp(1), if (dark) Color.argb(120, 130, 138, 151) else Color.argb(160, 160, 168, 181))
            }
        }
    }

    private fun accountSubmitButton(label: String, onClick: (TextView) -> Unit): TextView {
        return TextView(activity).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(20, 23, 29))
            minHeight = dp(52)
            contentDescription = label
            setPadding(dp(18), dp(15), dp(18), dp(15))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(190, 198, 210), Color.rgb(105, 113, 126))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.argb(190, 145, 153, 166))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick(this) }
        }
    }

    private fun showPasswordResetForm() {
        val page = createAccountFormPage(
            translate("استرجاع كلمة المرور"),
            translate("أدخل البريد الإلكتروني المرتبط بحسابك، وسنرسل إليك رابط إعادة التعيين.")
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
                    toast("إذا كان الحساب موجوداً، فستصلك رسالة لإعادة تعيين كلمة المرور.")
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
            if (createAccount) translate("أنشئ حساب MARCO لحفظ ملفك ومزامنة بياناتك.")
            else translate("سجّل الدخول للعودة إلى حساب MARCO.")
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
            setTextColor(Color.rgb(190, 198, 210))
            minHeight = dp(48)
            minWidth = dp(48)
            contentDescription = translate("إظهار كلمة المرور")
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(72, 190, 198, 210)),
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
                setTextColor(Color.rgb(190, 198, 210))
                minHeight = dp(48)
                minWidth = dp(48)
                contentDescription = translate("نسيت كلمة المرور؟")
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(72, 190, 198, 210)),
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
                toast("أدخل عنوان بريد إلكتروني صحيحاً وكلمة المرور.")
                return@accountSubmitButton
            }
            if (createAccount && secret != confirmPassword?.text?.toString()) {
                toast("كلمتا المرور غير متطابقتين")
                return@accountSubmitButton
            }
            if (createAccount && secret.length < 6) {
                toast("يجب أن تتكون كلمة المرور من 6 أحرف على الأقل.")
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
                onSignedIn()
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
            toast("لا يوجد حساب لحذفه حالياً.")
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(translate("حذف حساب MARCO؟"))
            .setMessage(
                translate("هذه العملية نهائية: سنحذف ملف الحساب والإعدادات المحفوظة في Firebase، ثم حساب Firebase نفسه. لن نحذف مكتبة الألعاب أو الملفات المحلية على الهاتف أو حساب Google المسؤول أو محتوى Blogger وDrive.")
            )
            .setNegativeButton(translate("إلغاء"), null)
            .setPositiveButton(translate("متابعة")) { _, _ ->
                when {
                    user.isAnonymous -> deleteAccountDataAndAuth(user)
                    user.providerData.any { it.providerId == "password" } -> showDeletePasswordPrompt(user)
                    user.providerData.any { it.providerId == "google.com" } -> reauthenticateGoogleForDeletion(user)
                    else -> toast("طريقة التحقق من هذا الحساب غير مدعومة حالياً. يُرجى التواصل مع الدعم.")
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
            .setMessage(translate("أدخل كلمة المرور الحالية للتحقق من هويتك قبل الحذف."))
            .setView(password)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton(translate("تحقق وحذف"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val secret = password.text.toString()
                val email = user.email
                if (secret.isBlank() || email.isNullOrBlank()) {
                    toast("أدخل كلمة المرور الحالية.")
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
                        toast("فشل التحقق من كلمة المرور")
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
                        signOutAdmin()
                        onAccountChanged()
                        toast("تم حذف حساب MARCO وبياناته السحابية بنجاح")
                    }
                    .addOnFailureListener { error ->
                        toast("تم حذف بيانات الحساب، لكن تعذر حذف حساب الدخول. تواصل مع الدعم.")
                    }
            }
            .addOnFailureListener { error ->
                toast("تعذر حذف بيانات الحساب؛ لم يتم حذف حساب الدخول")
            }
    }

    /** Complete the normal Firebase account flow without requesting Blogger or Drive scopes. */
    private fun completeGoogleAccountSignIn(message: String) {
        toast(message)
        onSignedIn()
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
            else -> fallback
        }
    }

    private fun toast(message: String) {
        Toast.makeText(activity, translate(message), Toast.LENGTH_LONG).show()
    }
}
