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
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameEngagementRepository
import com.marcorx6.library.data.auth.FirebaseAuthRepository
import com.marcorx6.library.data.auth.FirebaseUserDocumentRepository
import com.marcorx6.library.data.auth.FirebaseMessagingRepository
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
    private val games: () -> List<Game>,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val openGameDetails: (Game) -> Unit,
    private val gameEngagementRepository: GameEngagementRepository
) {
    private val userDocuments = FirebaseUserDocumentRepository()
    private val messaging = FirebaseMessagingRepository()
    private val notifications = com.marcorx6.library.data.auth.FirebaseNotificationRepository()
    private var notificationsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var inboxListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var pendingDeletionUid: String? = null
    private var returnToProfileEditorAfterPhotoPick = false

    fun openNotificationsFromLibrary() = showNotifications()

    fun openMessagesFromLibrary() = showInbox()

    fun show() {
        clearAccountListeners()
        returnToProfileEditorAfterPhotoPick = false
        // Keep the social profile strictly monochrome, independent of the app reading theme.
        val dark = true
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(18), dp(20), dp(30))
            setBackgroundColor(Color.rgb(7, 8, 10))
        }
        val user = authRepository.currentUser
        if (user != null && !user.isAnonymous) syncNewGameNotifications(user.uid)
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
        val accountHandleView = TextView(activity).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(155, 162, 173))
            visibility = View.GONE
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
            identityCard.addView(TextView(activity).apply {
                text = translate("ملف MARCO الشخصي")
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.12f
                setTextColor(Color.rgb(190, 198, 210))
            })
            val accountNameRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            accountNameRow.addView(TextView(activity).apply {
                text = user.displayName?.takeIf { it.isNotBlank() } ?: translate("مستخدم MARCO")
                textSize = 23f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, dp(7), 0, dp(4))
            })
            accountNameRow.addView(ImageView(activity).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
                contentDescription = translate("تعديل الملف الشخصي")
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                isClickable = true
                isFocusable = true
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(35, 39, 46))
                    setStroke(dp(1), Color.rgb(100, 108, 120))
                }
                setOnClickListener { showEditProfile() }
            }, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginStart = dp(8)
            })
            identityCard.addView(accountNameRow)
            identityCard.addView(accountHandleView)
            accountHandleView.isClickable = true
            accountHandleView.isFocusable = true
            accountHandleView.setOnClickListener { showEditProfile() }
            user.email?.takeIf { it.isNotBlank() }?.let { emailAddress ->
                identityCard.addView(TextView(activity).apply {
                    text = emailAddress
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(155, 162, 173))
                    setPadding(0, 0, 0, dp(6))
                })
            }
            identityCard.addView(TextView(activity).apply {
                text = translate(if (user.isEmailVerified) "حساب موثّق" else "عضو في مجتمع MARCO")
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(205, 211, 220))
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(30).toFloat()
                    setColor(Color.rgb(35, 39, 46))
                    setStroke(dp(1), Color.rgb(100, 108, 120))
                }
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
            addPublicFollowControls(page, user.uid, dark)
            // These figures describe the locally available library, not cloud-account-wide activity.
            val libraryGames = games()
            userDocuments.syncPublicFavorites(
                uid = user.uid,
                displayName = user.displayName,
                bio = null,
                photoUrl = user.photoUrl?.toString(),
                photoData = null,
                favoriteGames = libraryGames.filter { it.isFavorite }.take(30).map { game ->
                    mapOf("name" to game.name, "platform" to game.platform, "coverUrl" to game.coverUrl)
                }
            ).addOnFailureListener { /* The local library remains usable offline. */ }
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
            page.addView(statsCard, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            })

            val favoritesCard = profileSectionCard(dark)
            val favoritePreview = libraryGames.filter { it.isFavorite }.take(8)
            favoritesCard.addView(TextView(activity).apply {
                text = translate("ألعابك المفضلة")
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
            })
            favoritesCard.addView(TextView(activity).apply {
                text = if (favoriteCount > 8) translate("عرض 8 من " + favoriteCount + " لعبة مفضلة")
                    else translate(favoriteCount.toString() + " لعبة محفوظة في المفضلة")
                textSize = 11f
                setTextColor(if (dark) Color.rgb(145, 154, 168) else Color.rgb(105, 114, 128))
                setPadding(0, dp(4), 0, dp(10))
            })
            if (favoritePreview.isEmpty()) {
                favoritesCard.addView(TextView(activity).apply {
                    text = translate("لا توجد ألعاب مفضلة بعد. أضف الألعاب التي تحبها باستخدام زر النجمة في صفحة التفاصيل.")
                    textSize = 12f
                    setTextColor(if (dark) Color.rgb(160, 168, 181) else Color.rgb(91, 98, 110))
                    setPadding(dp(12), dp(18), dp(12), dp(18))
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(if (dark) Color.rgb(29, 32, 39) else Color.rgb(244, 246, 249))
                    }
                })
            } else {
                val favoritesScroll = HorizontalScrollView(activity).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    clipToPadding = false
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(0, dp(2), 0, dp(6))
                }
                val favoritesTrack = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                }
                favoritePreview.forEach { game ->
                    favoritesTrack.addView(profileGameCard(game, dark, ""), LinearLayout.LayoutParams(dp(130), -2).apply {
                        marginEnd = dp(9)
                    })
                }
                favoritesScroll.addView(favoritesTrack)
                favoritesCard.addView(favoritesScroll, LinearLayout.LayoutParams(-1, -2))
            }
            page.addView(favoritesCard, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            })

            val ratingsCard = profileSectionCard(dark)
            val ratingsHeading = TextView(activity).apply {
                text = translate("تقييماتي")
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
            }
            ratingsCard.addView(ratingsHeading)
            val ratingsContent = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(9), 0, 0)
            }
            ratingsContent.addView(TextView(activity).apply {
                text = translate("جارٍ تحميل تقييماتك...")
                textSize = 12f
                setTextColor(if (dark) Color.rgb(150, 158, 171) else Color.rgb(105, 112, 124))
            })
            ratingsCard.addView(ratingsContent)
            page.addView(ratingsCard, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            })
            gameEngagementRepository.loadMyRatings(libraryGames, onSuccess = { ratings ->
                ratingsContent.removeAllViews()
                ratingsHeading.text = translate("تقييماتي • " + ratings.size)
                if (ratings.isEmpty()) {
                    ratingsContent.addView(TextView(activity).apply {
                        text = translate("لم تقيّم أي لعبة بعد. افتح تفاصيل لعبة وأضف تقييمك لتظهر هنا.")
                        textSize = 12f
                        setTextColor(if (dark) Color.rgb(150, 158, 171) else Color.rgb(105, 112, 124))
                        setPadding(0, dp(5), 0, dp(4))
                    })
                } else {
                    val ratingsScroll = HorizontalScrollView(activity).apply {
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                    }
                    val ratingsTrack = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                    }
                    ratings.take(12).forEach { entry ->
                        ratingsTrack.addView(
                            profileGameCard(entry.game, dark, "★ " + entry.rating + "/5"),
                            LinearLayout.LayoutParams(dp(130), -2).apply { marginEnd = dp(9) }
                        )
                    }
                    ratingsScroll.addView(ratingsTrack)
                    ratingsContent.addView(ratingsScroll)
                }
            }, onFailure = { error ->
                android.util.Log.e("FirebaseAccountController", "Failed to load account ratings", error)
                ratingsContent.removeAllViews()
                val details = (error.localizedMessage ?: error.javaClass.simpleName)
                    .replace(Regex("\\s+"), " ")
                    .take(180)
                ratingsContent.addView(TextView(activity).apply {
                    text = translate("تعذر تحميل تقييماتك الآن. ") + details
                    textSize = 12f
                    setTextColor(Color.rgb(165, 173, 185))
                })
            })

            // Real profile tabs: show the existing library, favorites, and Firestore ratings
            // without inventing a posts/activity feed that the app does not currently store.
            val profileTabs = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(17).toFloat()
                    setColor(Color.rgb(18, 20, 25))
                    setStroke(dp(1), Color.rgb(70, 77, 89))
                }
            }
            val tabLabels = listOf("الكل", "المفضلة", "التقييمات")
            val tabViews = tabLabels.map { label ->
                TextView(activity).apply {
                    text = translate(label)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(dp(10), dp(12), dp(10), dp(12))
                    setTextColor(Color.rgb(155, 163, 176))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(13).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                    isClickable = true
                    isFocusable = true
                }.also { tab ->
                    profileTabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        marginEnd = dp(3)
                    })
                }
            }
            fun selectProfileTab(index: Int) {
                tabViews.forEachIndexed { tabIndex, tab ->
                    val selected = tabIndex == index
                    tab.setTextColor(if (selected) Color.WHITE else Color.rgb(155, 163, 176))
                    tab.background = GradientDrawable().apply {
                        cornerRadius = dp(13).toFloat()
                        setColor(if (selected) Color.rgb(62, 68, 79) else Color.TRANSPARENT)
                        if (selected) setStroke(dp(1), Color.rgb(145, 153, 166))
                    }
                }
                favoritesCard.visibility = if (index == 0 || index == 1) View.VISIBLE else View.GONE
                ratingsCard.visibility = if (index == 0 || index == 2) View.VISIBLE else View.GONE
            }
            tabViews.forEachIndexed { index, tab ->
                tab.setOnClickListener { selectProfileTab(index) }
            }
            page.addView(profileTabs, page.indexOfChild(favoritesCard), LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
            selectProfileTab(0)

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
            userDocuments.loadUser(user.uid)
                .addOnSuccessListener { snapshot ->
                    val savedHandle = snapshot.getString("username")
                    accountHandleView.text = if (savedHandle.isNullOrBlank()) {
                        translate("＋ اختر اسم مستخدم فريداً")
                    } else {
                        "@$savedHandle"
                    }
                    accountHandleView.visibility = View.VISIBLE
                    snapshot.getString("photoData")?.takeIf { it.isNotBlank() }?.let { encoded ->
                        runCatching {
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()?.let { bitmap ->
                            photoView.setImageBitmap(bitmap)
                            photoView.imageTintList = null
                            photoView.clearColorFilter()
                        }
                    }
                    snapshot.getString("bio")?.trim()?.takeIf { it.isNotBlank() }?.let { bio ->
                        profileBioPreview.text = bio
                        profileBioPreview.visibility = View.VISIBLE
                    }
                    if (snapshot.getString("photoData").isNullOrBlank()) {
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
                            }.apply { name = "marco-profile-avatar-preview-loader" }.start()
                        }
                    }
                }
                .addOnFailureListener {
                    // Keep the provider avatar and the rest of the profile usable if
                    // the private profile document is temporarily unavailable.
                }
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
            page.addView(sectionCaption("إدارة ملفك الاجتماعي", dark))
            val profileIdCard = profileSectionCard(dark)
            profileIdCard.addView(TextView(activity).apply {
                text = translate("معرّف ملفك الشخصي")
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            profileIdCard.addView(TextView(activity).apply {
                text = user.uid
                textSize = 10f
                setTextColor(Color.rgb(155, 162, 173))
                setPadding(0, dp(6), 0, dp(10))
            })
            profileIdCard.addView(accountSubmitButton(translate("نسخ المعرّف لمشاركته")) {
                val clipboard = activity.getSystemService(Activity.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MARCO profile ID", user.uid))
                toast("تم نسخ معرّف الملف الشخصي")
            }, LinearLayout.LayoutParams(-1, dp(46)))
            page.addView(profileIdCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            action(
                page,
                translate("الإشعارات"),
                translate("متابعون جدد ورسائلك الخاصة"),
                dark,
                run = ::showNotifications
            )
            action(
                page,
                translate("الرسائل الخاصة"),
                translate("محادثاتك مع الأشخاص الذين يتابعونك وتتابعهم"),
                dark,
                run = ::showInbox
            )
            action(
                page,
                translate("إعدادات الحساب"),
                translate("البريد الإلكتروني والأمان وتسجيل الخروج"),
                dark,
                run = ::showAccountSettings
            )
            action(
                page,
                translate("فتح ملف شخصي آخر"),
                translate("تصفح ملف مستخدم شارك معك معرّفه"),
                dark,
                run = ::promptPublicProfile
            )
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

    private fun showEditProfile() {
        val user = authRepository.currentUser
        if (user == null || user.isAnonymous) {
            toast("سجّل الدخول أولاً لتعديل ملفك الشخصي")
            show()
            return
        }
        val dark = isDarkMode()
        val page = createAccountFormPage(
            translate("تعديل الملف الشخصي"),
            translate("حدّث معلوماتك بالطريقة التي تظهر بها للآخرين في MARCO")
        )
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
        page.addView(photoView, LinearLayout.LayoutParams(dp(104), dp(104)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })
        action(page, translate("تغيير الصورة الشخصية"), translate("اختر صورة من هاتفك"), dark, run = {
            returnToProfileEditorAfterPhotoPick = true
            chooseProfilePhoto()
        })
            page.addView(sectionCaption("بيانات الملف الشخصي", dark))
            page.addView(TextView(activity).apply {
                text = translate("الاسم الظاهر")
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                setPadding(0, dp(8), 0, dp(6))
            })
            val nameInput = accountInput(
                translate("اسمك الظاهر"),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            ).apply {
                filters = arrayOf(android.text.InputFilter.LengthFilter(40))
                setText(user.displayName.orEmpty())
                setSelection(text.length)
            }
            page.addView(nameInput, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })
            page.addView(TextView(activity).apply {
                text = translate("اسم المستخدم الفريد")
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
                setPadding(0, dp(2), 0, dp(6))
            })
            val usernameInput = accountInput(
                "@marco_rx6",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            ).apply {
                filters = arrayOf(android.text.InputFilter.LengthFilter(20))
                setSingleLine(true)
            }
            page.addView(usernameInput, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(4) })
            page.addView(TextView(activity).apply {
                text = translate("من 3 إلى 20 حرفاً: أحرف إنجليزية وأرقام وشرطة سفلية فقط. لا يمكن تكرار الاسم.")
                textSize = 10f
                setTextColor(Color.rgb(145, 153, 166))
                setPadding(dp(4), 0, dp(4), dp(12))
            })

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

            page.addView(sectionCaption("خصوصية الملف الشخصي", dark))
            val publicProfileToggle = CheckBox(activity).apply {
                text = translate("السماح للآخرين بمشاهدة ملفي الشخصي")
                textSize = 13f
                setTextColor(if (dark) Color.rgb(225, 229, 236) else Color.rgb(45, 52, 63))
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(180, 190, 205))
                isEnabled = false
            }
            val showFavoritesToggle = CheckBox(activity).apply {
                text = translate("إظهار ألعابي المفضلة في الملف العام")
                textSize = 12f
                setTextColor(if (dark) Color.rgb(190, 198, 210) else Color.rgb(75, 83, 96))
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(180, 190, 205))
                isEnabled = false
            }
            page.addView(publicProfileToggle, LinearLayout.LayoutParams(-1, dp(48)))
            page.addView(showFavoritesToggle, LinearLayout.LayoutParams(-1, dp(44)).apply {
                bottomMargin = dp(10)
            })

            var profilePhotoData: String? = null
            var profileLoaded = false
            val saveButton = accountSubmitButton(translate("حفظ التغييرات")) { button ->
                val name = nameInput.text.toString().trim()
                val bio = bioInput.text.toString().trim()
                val username = usernameInput.text.toString().trim().removePrefix("@")
                if (!Regex("[A-Za-z0-9][A-Za-z0-9_]{2,19}").matches(username)) {
                    toast("اسم المستخدم يجب أن يكون من 3 إلى 20 حرفاً إنجليزياً أو رقماً أو _.")
                    return@accountSubmitButton
                }
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
                        photoData = profilePhotoData,
                        username = username,
                        publicProfile = publicProfileToggle.isChecked,
                        showFavorites = showFavoritesToggle.isChecked,
                        favoriteGames = games().filter { it.isFavorite }.take(30).map { game ->
                            mapOf("name" to game.name, "platform" to game.platform, "coverUrl" to game.coverUrl)
                        }
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
                    usernameInput.setText(snapshot.getString("username").orEmpty())
                    publicProfileToggle.isChecked = snapshot.getBoolean("publicProfile") ?: false
                    showFavoritesToggle.isChecked = snapshot.getBoolean("showFavorites") ?: true
                    publicProfileToggle.isEnabled = true
                    showFavoritesToggle.isEnabled = true
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


        showAccountPage(page)
    }

    private fun showAccountSettings() {
        val user = authRepository.currentUser
        if (user == null || user.isAnonymous) {
            show()
            return
        }
        val dark = isDarkMode()
        val page = createAccountFormPage(
            translate("إعدادات الحساب"),
            translate("إدارة البريد الإلكتروني وأمان حساب MARCO")
        )
        page.addView(sectionCaption("معلومات الاتصال", dark))
        user.email?.let { email ->
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
            page.addView(emailRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }
        page.addView(sectionCaption("الخصوصية والملف العام", dark))
        action(page, translate("تعديل الخصوصية"), translate("تحديد من يستطيع مشاهدة ملفك ومفضلاتك"), dark, run = ::showEditProfile)
        page.addView(sectionCaption("الأمان", dark))
        if (!user.isEmailVerified && user.email != null) {
            action(page, translate("تأكيد البريد الإلكتروني"), translate("إرسال رسالة تحقق جديدة إلى بريدك"), dark, run = {
                user.sendEmailVerification()
                    .addOnSuccessListener { toast("تم إرسال رسالة تأكيد البريد الإلكتروني") }
                    .addOnFailureListener { toast("تعذر إرسال رسالة التأكيد") }
            })
        }
        if (user.email != null && user.providerData.any { it.providerId == "password" }) {
            action(page, translate("إعادة تعيين كلمة المرور"), translate("إرسال رابط آمن لتغيير كلمة مرور الحساب"), dark, run = {
                authRepository.sendPasswordReset(user.email!!)
                    .addOnSuccessListener { toast("إذا كان الحساب موجوداً، فستصلك رسالة إعادة التعيين.") }
                    .addOnFailureListener { toast("تعذر إرسال رابط إعادة تعيين كلمة المرور") }
            })
        }
        action(page, translate("نسخ معرّف ملفي الشخصي"), translate("شارك المعرّف مع مستخدم آخر لفتح ملفك العام"), dark, run = {
            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MARCO profile ID", user.uid))
            toast("تم نسخ معرّف الملف الشخصي")
        })
        page.addView(sectionCaption("إدارة الحساب", dark))
        action(page, translate("تسجيل الخروج"), translate("الخروج من حساب MARCO مع إبقاء جلسة Blogger وGoogle Drive منفصلة"), dark, run = {
            val logoutDialog = AlertDialog.Builder(activity)
                .setTitle(translate("تسجيل الخروج من MARCO؟"))
                .setMessage(translate("سيتم تسجيل الخروج من حساب MARCO فقط. ستبقى جلسة Google المستعملة في Blogger وGoogle Drive كما هي، ولن تُحذف مكتبتك أو ملفات التنزيل على هذا الهاتف."))
                .setNegativeButton(translate("إلغاء"), null)
                .setPositiveButton(translate("تسجيل الخروج")) { _, _ ->
                    clearAccountListeners()
                    authRepository.signOut()
                    signOutAdmin()
                    onAccountChanged()
                    toast("تم تسجيل الخروج بنجاح")
                }
                .create()
            logoutDialog.show()
            styleDialog(logoutDialog)
        })
        action(page, translate("حذف الحساب"), translate("حذف حساب MARCO وبياناته السحابية نهائياً"), dark, run = {
            confirmAccountDeletion(user)
        })
        showAccountPage(page)
    }

    private fun promptPublicProfile() {
        val uidInput = EditText(activity).apply {
            hint = translate("@اسم_المستخدم أو معرّف الملف الشخصي")
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(translate("فتح ملف شخصي"))
            .setMessage(translate("أدخل معرّف المستخدم الذي شاركه معك صاحب الحساب."))
            .setView(uidInput)
            .setNegativeButton(translate("إلغاء"), null)
            .setPositiveButton(translate("فتح"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = uidInput.text.toString().trim()
                dialog.dismiss()
                if (entered.startsWith("@") || Regex("[A-Za-z0-9][A-Za-z0-9_]{2,19}").matches(entered)) {
                    userDocuments.loadUidByUsername(entered).addOnSuccessListener { uid ->
                        if (uid.isNullOrBlank()) toast("اسم المستخدم غير موجود.") else showPublicProfile(uid)
                    }.addOnFailureListener { toast("تعذر البحث عن اسم المستخدم. تحقق من الاتصال.") }
                } else if (entered.length in 10..128) {
                    showPublicProfile(entered)
                } else {
                    toast("أدخل اسم مستخدم صالحاً أو معرّف الملف الشخصي.")
                }
            }
        }
        dialog.show()
        styleDialog(dialog)
    }

    private fun showPublicProfile(uid: String) {
        userDocuments.loadPublicProfile(uid).addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                toast("هذا الملف الشخصي غير متاح أو لم يسمح صاحبه بعرضه")
                return@addOnSuccessListener
            }
            val dark = true
            val page = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(20), dp(18), dp(20), dp(30))
                setBackgroundColor(Color.rgb(7, 8, 10))
            }
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(activity).apply {
                text = translate("رجوع")
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(190, 198, 210))
                setPadding(dp(8), dp(12), dp(18), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener { show() }
            })
            header.addView(TextView(activity).apply {
                text = translate("ملف مستخدم MARCO")
                textSize = 21f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
            })
            page.addView(header)
            val identity = profileSectionCard(dark)
            identity.gravity = Gravity.CENTER_HORIZONTAL
            addPublicAvatar(identity, snapshot, dark)
            identity.addView(TextView(activity).apply {
                text = snapshot.getString("displayName")?.takeIf { it.isNotBlank() } ?: translate("مستخدم MARCO")
                textSize = 22f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
            })
            snapshot.getString("username")?.takeIf { it.isNotBlank() }?.let { handle ->
                identity.addView(TextView(activity).apply {
                    text = "@$handle"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(165, 173, 185))
                    setPadding(0, dp(3), 0, dp(4))
                })
            }
            snapshot.getString("bio")?.takeIf { it.isNotBlank() }?.let { bio ->
                identity.addView(TextView(activity).apply {
                    text = bio
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(if (dark) Color.rgb(185, 193, 205) else Color.rgb(75, 83, 96))
                    setPadding(0, dp(8), 0, dp(3))
                })
            }
            page.addView(identity, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
                bottomMargin = dp(14)
            })
            addPublicFollowControls(page, uid, dark)
            val showFavorites = snapshot.getBoolean("showFavorites") == true
            val favoritesSection = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                if (showFavorites) {
                    addView(sectionCaption("الألعاب المفضلة", dark))
                    val values = snapshot.get("favoriteGames") as? List<*>
                    val favorites = values.orEmpty().mapNotNull { value ->
                        val item = value as? Map<*, *> ?: return@mapNotNull null
                        val name = item["name"] as? String ?: return@mapNotNull null
                        val platform = item["platform"] as? String ?: return@mapNotNull null
                        val coverUrl = item["coverUrl"] as? String ?: ""
                        Game(name, platform, "", "", coverUrl = coverUrl)
                    }
                    if (favorites.isEmpty()) {
                        addView(TextView(activity).apply {
                            text = translate("لم يشارك هذا المستخدم ألعاباً مفضلة بعد.")
                            textSize = 12f
                            setTextColor(Color.rgb(150, 158, 171))
                            setPadding(dp(12), dp(14), dp(12), dp(14))
                        })
                    } else {
                        val scroll = HorizontalScrollView(activity).apply {
                            isHorizontalScrollBarEnabled = false
                            layoutDirection = View.LAYOUT_DIRECTION_RTL
                        }
                        val track = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutDirection = View.LAYOUT_DIRECTION_RTL
                        }
                        favorites.forEach { game ->
                            track.addView(profileGameCard(game, dark, ""), LinearLayout.LayoutParams(dp(130), -2).apply {
                                marginEnd = dp(9)
                            })
                        }
                        scroll.addView(track)
                        addView(scroll)
                    }
                }
            }
            val ratingsSection = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(sectionCaption("تقييمات هذا المستخدم", dark))
                addView(TextView(activity).apply {
                    text = translate("جارٍ تحميل التقييمات...")
                    textSize = 12f
                    setTextColor(Color.rgb(150, 158, 171))
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                })
            }
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tabs = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.rgb(19, 22, 28))
                    setStroke(dp(1), Color.rgb(70, 77, 89))
                }
            }
            val tabItems = mutableListOf<Pair<TextView, String>>()
            fun selectPublicTab(selected: String) {
                content.removeAllViews()
                when (selected) {
                    "favorites" -> content.addView(favoritesSection)
                    "ratings" -> content.addView(ratingsSection)
                    else -> {
                        if (showFavorites) content.addView(favoritesSection)
                        content.addView(ratingsSection)
                    }
                }
                tabItems.forEach { (view, key) ->
                    val active = key == selected
                    view.setTextColor(if (active) Color.rgb(12, 14, 18) else Color.rgb(190, 198, 210))
                    (view.background as? GradientDrawable)?.setColor(
                        if (active) Color.rgb(190, 198, 210) else Color.TRANSPARENT
                    )
                }
            }
            fun addPublicTab(label: String, key: String) {
                val tab = TextView(activity).apply {
                    text = translate(label)
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(dp(16), dp(11), dp(16), dp(11))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                    setOnClickListener { selectPublicTab(key) }
                }
                tabItems.add(tab to key)
                tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
            }
            addPublicTab("الكل", "all")
            if (showFavorites) addPublicTab("المفضلة", "favorites")
            addPublicTab("التقييمات", "ratings")
            page.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            })
            page.addView(content, LinearLayout.LayoutParams(-1, -2))
            selectPublicTab("all")
            gameEngagementRepository.loadRatingsForUser(uid, games(), onSuccess = { ratings ->
                ratingsSection.removeAllViews()
                ratingsSection.addView(sectionCaption("تقييمات هذا المستخدم", dark))
                if (ratings.isEmpty()) {
                    ratingsSection.addView(TextView(activity).apply {
                        text = translate("لا توجد تقييمات عامة متاحة لهذا المستخدم بعد.")
                        textSize = 12f
                        setTextColor(Color.rgb(150, 158, 171))
                        setPadding(dp(4), dp(8), dp(4), dp(8))
                    })
                } else {
                    val scroll = HorizontalScrollView(activity).apply {
                        isHorizontalScrollBarEnabled = false
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                    }
                    val track = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                    }
                    ratings.take(20).forEach { entry ->
                        track.addView(
                            profileGameCard(entry.game, dark, "★ " + entry.rating + "/5"),
                            LinearLayout.LayoutParams(dp(130), -2).apply { marginEnd = dp(9) }
                        )
                    }
                    scroll.addView(track)
                    ratingsSection.addView(scroll)
                }
            }, onFailure = {
                ratingsSection.removeAllViews()
                ratingsSection.addView(sectionCaption("تقييمات هذا المستخدم", dark))
                ratingsSection.addView(TextView(activity).apply {
                    text = translate("تعذر تحميل التقييمات حالياً.")
                    textSize = 12f
                    setTextColor(Color.rgb(190, 198, 210))
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                })
            })
            showAccountPage(page)
        }.addOnFailureListener {
            toast("تعذر تحميل الملف الشخصي. تحقق من المعرّف والاتصال.")
        }
    }


    private fun syncNewGameNotifications(uid: String) {
        val catalog = games()
        if (uid.isBlank() || catalog.isEmpty()) return
        val preferences = activity.getSharedPreferences("marco_notification_state", Activity.MODE_PRIVATE)
        val key = "known_games_$uid"
        val current = catalog.map { "${it.platform.trim().lowercase()}::${it.name.trim().lowercase()}" }
            .filter { it != "::" }.toSet()
        val previous = preferences.getStringSet(key, null)
        if (previous == null) {
            preferences.edit().putStringSet(key, current).apply()
            return
        }
        val addedKeys = current - previous
        if (addedKeys.isEmpty()) return
        val addedGames = catalog.filter {
            "${it.platform.trim().lowercase()}::${it.name.trim().lowercase()}" in addedKeys
        }.distinctBy { "${it.platform}::${it.name}" }.take(30)
        notifications.addNewGameNotifications(uid, addedGames.map { it.name to it.platform })
            .addOnSuccessListener {
                preferences.edit().putStringSet(key, current).apply()
            }
            .addOnFailureListener { error ->
                android.util.Log.w("MarcoNotifications", "New-game notification sync failed", error)
            }
    }

    private fun showNotifications() {
        val user = authRepository.currentUser
        if (user == null || user.isAnonymous) {
            toast("سجّل الدخول لمتابعة إشعارات حسابك.")
            show()
            return
        }
        syncNewGameNotifications(user.uid)
        notificationsListener?.remove()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(16), dp(18), dp(24))
            setBackgroundColor(Color.rgb(7, 8, 10))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = translate("الإشعارات")
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
            contentDescription = translate("العودة إلى القائمة الرئيسية")
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                notificationsListener?.remove()
                notificationsListener = null
                onBack()
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        page.addView(header)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var selectedSection = "game"
        var allItems: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
        fun renderItems() {
            list.removeAllViews()
            val filtered = allItems.filter { item ->
                when (selectedSection) {
                    "game" -> item.getString("type") == "game"
                    "follow" -> item.getString("type") == "follow"
                    "message" -> item.getString("type") == "message"
                    else -> false
                }
            }
            if (filtered.isEmpty()) {
                list.addView(TextView(activity).apply {
                    text = translate(when (selectedSection) {
                        "game" -> "لا توجد ألعاب جديدة حالياً."
                        "follow" -> "لا يوجد متابعون جدد حالياً."
                        else -> "لا توجد رسائل جديدة حالياً."
                    })
                    textSize = 13f
                    setTextColor(Color.rgb(165, 173, 185))
                    setPadding(dp(12), dp(28), dp(12), dp(28))
                })
            }
            filtered.forEach { item ->
                val type = item.getString("type").orEmpty()
                val actorUid = item.getString("actorUid").orEmpty()
                val unread = item.getBoolean("read") != true
                val title = item.getString("title") ?: "إشعار جديد"
                val body = item.getString("body").orEmpty()
                val row = TextView(activity).apply {
                    text = (if (unread) "●  " else "○  ") + title + "\n" + body
                    textSize = 13f
                    setTextColor(if (unread) Color.WHITE else Color.rgb(170, 177, 188))
                    setPadding(dp(15), dp(14), dp(15), dp(14))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(15).toFloat()
                        setColor(if (unread) Color.rgb(35, 38, 45) else Color.rgb(18, 20, 25))
                        setStroke(dp(1), Color.rgb(75, 82, 94))
                    }
                    setOnClickListener {
                        notifications.markRead(user.uid, item)
                            .addOnFailureListener { error ->
                                android.util.Log.w("MarcoNotifications", "Mark read failed", error)
                            }
                        when (type) {
                            "follow" -> if (actorUid.isNotBlank()) showPublicProfile(actorUid)
                            "message" -> if (actorUid.isNotBlank()) openConversation(actorUid)
                            "game" -> {
                                val gameName = item.getString("gameName").orEmpty()
                                val platform = item.getString("platform").orEmpty()
                                val game = games().firstOrNull {
                                    it.name.equals(gameName, ignoreCase = true) &&
                                        it.platform.equals(platform, ignoreCase = true)
                                }
                                if (game != null) openGameDetails(game)
                                else toast("هذه اللعبة لم تعد متاحة في المكتبة.")
                            }
                        }
                    }
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            }
        }
        val tabs = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        fun addTab(label: String, key: String) {
            val tab = TextView(activity).apply {
                text = translate(label)
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(205, 211, 220))
                setPadding(dp(9), dp(11), dp(9), dp(11))
                background = GradientDrawable().apply {
                    cornerRadius = dp(13).toFloat()
                    setColor(if (selectedSection == key) Color.rgb(55, 60, 70) else Color.rgb(20, 22, 27))
                    setStroke(dp(1), Color.rgb(90, 98, 111))
                }
                setOnClickListener {
                    selectedSection = key
                    for (i in 0 until tabs.childCount) {
                        val child = tabs.getChildAt(i) as? TextView ?: continue
                        child.background = GradientDrawable().apply {
                            cornerRadius = dp(13).toFloat()
                            setColor(if (child.tag == key) Color.rgb(55, 60, 70) else Color.rgb(20, 22, 27))
                            setStroke(dp(1), Color.rgb(90, 98, 111))
                        }
                    }
                    renderItems()
                }
                tag = key
            }
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
            })
        }
        addTab("الألعاب الجديدة", "game")
        addTab("المتابعون", "follow")
        addTab("الرسائل", "message")
        page.addView(tabs)
        val scroll = ScrollView(activity).apply { addView(list) }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        showAccountPage(page)
        notificationsListener = notifications.listen(user.uid, { items ->
            if (activity.isFinishing || activity.isDestroyed) return@listen
            allItems = items
            val unread = notifications.unreadCount(items)
            (header.getChildAt(0) as? TextView)?.text =
                if (unread > 0) "${translate("الإشعارات")} • $unread" else translate("الإشعارات")
            renderItems()
        }, { error ->
            android.util.Log.e("MarcoNotifications", "Notification listener failed", error)
            toast(error.localizedMessage ?: "تعذر تحميل الإشعارات. تحقق من الاتصال.")
        })
    }

    private fun showInbox() {
        val user = authRepository.currentUser
        if (user == null || user.isAnonymous) {
            toast("سجّل الدخول لاستخدام الرسائل الخاصة.")
            show()
            return
        }
        inboxListener?.remove()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(16), dp(18), dp(24))
            setBackgroundColor(Color.rgb(7, 8, 10))
        }
        page.addView(TextView(activity).apply {
            text = translate("الرسائل الخاصة")
            textSize = 23f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(3))
        })
        page.addView(TextView(activity).apply {
            text = translate("اضغط مطولاً على المحادثة لحذفها من قائمتك فقط")
            textSize = 11f
            setTextColor(Color.rgb(145, 155, 170))
            setPadding(0, 0, 0, dp(14))
        })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply { addView(list) }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(TextView(activity).apply {
            text = translate("رسالة جديدة  +")
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(225, 230, 237))
            setPadding(dp(14), dp(15), dp(14), dp(15))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(38, 42, 50))
                setStroke(dp(1), Color.rgb(115, 124, 138))
            }
            setOnClickListener { showFollowersForNewMessage() }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
            bottomMargin = dp(6)
        })
        page.addView(TextView(activity).apply {
            text = translate("العودة إلى القائمة الرئيسية")
            textSize = 13f
            setTextColor(Color.rgb(190, 198, 210))
            setPadding(dp(8), dp(14), dp(8), dp(14))
            setOnClickListener {
                inboxListener?.remove()
                inboxListener = null
                onBack()
            }
        })
        showAccountPage(page)
        inboxListener = messaging.listenInbox(user.uid, { conversations ->
            if (activity.isFinishing || activity.isDestroyed) return@listenInbox
            list.removeAllViews()
            if (conversations.isEmpty()) {
                list.addView(TextView(activity).apply {
                    text = translate("لا توجد محادثات بعد. ابدأ محادثة مع شخص يتابعك وتتابعه.")
                    setTextColor(Color.rgb(165, 173, 185))
                    setPadding(dp(12), dp(24), dp(12), dp(24))
                })
            }
            conversations.forEach { conversation ->
                if ((conversation.get("deletedFor") as? List<*>)?.contains(user.uid) == true) return@forEach
                val otherUid = messaging.otherParticipant(conversation, user.uid) ?: return@forEach
                userDocuments.loadPublicProfile(otherUid).addOnSuccessListener { profile ->
                    if (!profile.exists() || activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                    val row = TextView(activity).apply {
                        text = (profile.getString("displayName") ?: "MARCO") + "\n" +
                            (conversation.getString("lastMessage") ?: "")
                        textSize = 14f
                        setTextColor(Color.rgb(220, 225, 232))
                        setPadding(dp(16), dp(16), dp(16), dp(16))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(14).toFloat()
                            setColor(Color.rgb(25, 28, 34))
                            setStroke(dp(1), Color.rgb(75, 83, 96))
                        }
                        setOnClickListener { openConversation(otherUid) }
                        setOnLongClickListener {
                            AlertDialog.Builder(activity)
                                .setIcon(com.marcorx6.library.R.drawable.ic_action_delete)
                                .setMessage(translate("حذف المحادثة من قائمتك فقط؟"))
                                .setNegativeButton(translate("إلغاء"), null)
                                .setPositiveButton(translate("حذف")) { _, _ ->
                                    messaging.hideConversation(user.uid, otherUid)
                                        .addOnFailureListener { error ->
                                            toast(error.localizedMessage ?: translate("تعذر حذف المحادثة."))
                                        }
                                }.show()
                            true
                        }
                    }
                    list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                }.addOnFailureListener { error ->
                    android.util.Log.w("MarcoMessages", "Inbox profile load failed", error)
                }
            }
        }, { error ->
            android.util.Log.e("MarcoMessages", "Inbox listener failed", error)
            toast(error.localizedMessage ?: translate("تعذر تحميل الرسائل. تحقق من الاتصال."))
        })
    }

    private fun showFollowersForNewMessage() {
        val user = authRepository.currentUser ?: return
        val dialogContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val scroll = ScrollView(activity).apply {
            addView(dialogContent)
            isFillViewport = true
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(translate("اختر متابعاً لإرسال رسالة"))
            .setView(scroll)
            .setNegativeButton(translate("إغلاق"), null)
            .create()
        dialog.show()
        styleDialog(dialog)
        dialogContent.addView(TextView(activity).apply {
            text = translate("جارٍ تحميل المتابعين...")
            textSize = 13f
            setTextColor(Color.rgb(170, 178, 190))
            setPadding(dp(8), dp(18), dp(8), dp(18))
        })
        userDocuments.loadFollowerIds(user.uid)
            .addOnSuccessListener { followerIds ->
                if (activity.isFinishing || activity.isDestroyed || !dialog.isShowing) return@addOnSuccessListener
                dialogContent.removeAllViews()
                if (followerIds.isEmpty()) {
                    dialogContent.addView(TextView(activity).apply {
                        text = translate("لا يوجد متابعون حتى الآن.")
                        setTextColor(Color.rgb(170, 178, 190))
                        setPadding(dp(8), dp(20), dp(8), dp(20))
                    })
                    return@addOnSuccessListener
                }
                var pending = followerIds.size
                val eligibleProfiles = java.util.Collections.synchronizedList(
                    mutableListOf<Pair<String, com.google.firebase.firestore.DocumentSnapshot>>()
                )
                fun renderEligible() {
                    if (pending != 0 || activity.isFinishing || activity.isDestroyed || !dialog.isShowing) return
                    dialogContent.removeAllViews()
                    val profiles = eligibleProfiles.sortedBy {
                        it.second.getString("displayName").orEmpty().lowercase()
                    }
                    if (profiles.isEmpty()) {
                        dialogContent.addView(TextView(activity).apply {
                            text = translate("لا يوجد متابعون متبادلون يمكن مراسلتهم حالياً.")
                            setTextColor(Color.rgb(170, 178, 190))
                            setPadding(dp(8), dp(20), dp(8), dp(20))
                        })
                    }
                    profiles.forEach { (followerUid, profile) ->
                        val row = TextView(activity).apply {
                            text = (profile.getString("displayName") ?: profile.getString("username") ?: "MARCO") +
                                (profile.getString("username")?.let { "\n@$it" } ?: "")
                            textSize = 14f
                            setTextColor(Color.rgb(225, 230, 237))
                            setPadding(dp(14), dp(14), dp(14), dp(14))
                            background = GradientDrawable().apply {
                                cornerRadius = dp(13).toFloat()
                                setColor(Color.rgb(25, 28, 34))
                                setStroke(dp(1), Color.rgb(75, 83, 96))
                            }
                            setOnClickListener {
                                dialog.dismiss()
                                openConversation(followerUid)
                            }
                        }
                        dialogContent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = dp(7)
                        })
                    }
                }

                followerIds.forEach { followerUid ->
                    userDocuments.isFollowing(user.uid, followerUid)
                        .addOnSuccessListener { followsBack ->
                            if (followsBack) {
                                userDocuments.loadPublicProfile(followerUid)
                                    .addOnSuccessListener { profile ->
                                        if (profile.exists()) eligibleProfiles.add(followerUid to profile)
                                        pending--
                                        renderEligible()
                                    }
                                    .addOnFailureListener {
                                        pending--
                                        renderEligible()
                                    }
                            } else {
                                pending--
                                renderEligible()
                            }
                        }
                        .addOnFailureListener {
                            pending--
                            renderEligible()
                        }
                }
            }
            .addOnFailureListener { error ->
                if (!dialog.isShowing) return@addOnFailureListener
                dialogContent.removeAllViews()
                dialogContent.addView(TextView(activity).apply {
                    text = error.localizedMessage ?: translate("تعذر تحميل المتابعين.")
                    setTextColor(Color.rgb(210, 170, 170))
                    setPadding(dp(8), dp(18), dp(8), dp(18))
                })
            }
    }

    private fun openConversation(otherUid: String) {
        val user = authRepository.currentUser ?: return
        if (user.isAnonymous) return
        messagesListener?.remove()
        inboxListener?.remove()
        messaging.canMessage(user.uid, otherUid).addOnSuccessListener { allowed ->
            if (!allowed) {
                toast(translate("المراسلة متاحة فقط عندما يتابع كل منكما الآخر."))
                showPublicProfile(otherUid)
                return@addOnSuccessListener
            }
            messaging.ensureConversation(user.uid, otherUid).addOnSuccessListener {
                userDocuments.loadPublicProfile(otherUid).addOnSuccessListener { profile ->
                    if (!profile.exists()) {
                        toast(translate("الملف الشخصي غير متاح."))
                        return@addOnSuccessListener
                    }
                    val page = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        setBackgroundColor(Color.rgb(7, 8, 10))
                    }
                    val header = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    header.addView(TextView(activity).apply {
                        text = translate("رجوع")
                        setTextColor(Color.rgb(190, 198, 210))
                        setPadding(dp(8), dp(12), dp(16), dp(12))
                        setOnClickListener { messagesListener?.remove(); messagesListener = null; showInbox() }
                    })
                    header.addView(TextView(activity).apply {
                        text = profile.getString("displayName") ?: translate("محادثة")
                        textSize = 19f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.WHITE)
                    })
                    page.addView(header)
                    val messages = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    val scroll = ScrollView(activity).apply { isFillViewport = true; addView(messages) }
                    page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
                    var selectedReply: com.google.firebase.firestore.DocumentSnapshot? = null
                    page.addView(TextView(activity).apply {
                        text = translate("اضغط على الرسالة للرد، واضغط مطولاً لحذفها لدى الطرفين")
                        textSize = 11f
                        setTextColor(Color.rgb(145, 155, 170))
                        setPadding(dp(3), dp(5), dp(3), dp(5))
                    })
                    val replyPreview = TextView(activity).apply {
                        visibility = View.GONE
                        textSize = 12f
                        setTextColor(Color.rgb(198, 203, 212))
                        setPadding(dp(10), dp(7), dp(10), dp(7))
                        setOnClickListener { selectedReply = null; visibility = View.GONE }
                    }
                    page.addView(replyPreview)
                    val composer = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(8), 0, 0)
                    }
                    val input = EditText(activity).apply {
                        hint = translate("اكتب رسالة...")
                        textSize = 14f
                        maxLines = 4
                        minLines = 1
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                        setTextColor(Color.WHITE)
                        setHintTextColor(Color.rgb(130, 138, 151))
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(16).toFloat()
                            setColor(Color.rgb(24, 27, 33))
                            setStroke(dp(1), Color.rgb(80, 88, 101))
                        }
                    }
                    composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
                    val send = TextView(activity).apply {
                        text = translate("إرسال")
                        gravity = Gravity.CENTER
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.rgb(18, 20, 24))
                        setPadding(dp(14), dp(14), dp(14), dp(14))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(15).toFloat()
                            setColor(Color.rgb(190, 198, 210))
                        }
                    }
                    composer.addView(send, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7) })
                    page.addView(composer)
                    send.setOnClickListener {
                        val body = input.text.toString().trim()
                        if (body.isEmpty()) return@setOnClickListener
                        send.isEnabled = false
                        val reply = selectedReply
                        messaging.sendMessage(
                            user.uid, otherUid, body,
                            reply?.id,
                            reply?.getString("text"),
                            reply?.getString("senderUid")
                        ).addOnSuccessListener {
                            input.text.clear()
                            selectedReply = null
                            replyPreview.visibility = View.GONE
                            send.isEnabled = true
                        }
                            .addOnFailureListener { error ->
                                send.isEnabled = true
                                android.util.Log.e("MarcoMessages", "Send message failed", error)
                                toast(error.localizedMessage ?: "تعذر إرسال الرسالة.")
                            }
                    }
                    input.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                            send.performClick()
                            true
                        } else false
                    }
                    showConversationPage(page)
                    val id = messaging.conversationId(user.uid, otherUid)
                    messagesListener = messaging.listenMessages(id, { snapshots ->
                        if (activity.isFinishing || activity.isDestroyed) return@listenMessages
                        messages.removeAllViews()
                        messaging.markMessagesRead(user.uid, snapshots).addOnFailureListener { error ->
                            android.util.Log.w("MarcoMessages", "Read receipt update failed", error)
                        }
                        snapshots.forEach { snapshot ->
                            val mine = snapshot.getString("senderUid") == user.uid
                            val recipientRead = (snapshot.get("readBy") as? List<*>)?.contains(otherUid) == true
                            val sentAt = snapshot.getTimestamp("createdAt")?.toDate()
                            val timeLabel = sentAt?.let {
                                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(it)
                            }.orEmpty()
                            val messageText = buildString {
                                append(snapshot.getString("text").orEmpty())
                                if (timeLabel.isNotBlank()) append("\n").append(timeLabel)
                                if (mine) append(if (recipientRead) "  ✓✓" else "  ✓")
                            }
                            val replyText = snapshot.getString("replyToText")
                            val bubble = TextView(activity).apply {
                                text = buildString {
                                    if (!replyText.isNullOrBlank()) append(replyText).append("\n")
                                    append(messageText)
                                }
                                if (!replyText.isNullOrBlank()) {
                                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                                        activity.getDrawable(com.marcorx6.library.R.drawable.ic_action_reply),
                                        null, null, null
                                    )
                                    compoundDrawablePadding = dp(5)
                                }
                                textSize = 14f
                                setTextColor(if (mine) Color.rgb(18, 20, 24) else Color.WHITE)
                                setPadding(dp(13), dp(10), dp(13), dp(10))
                                background = GradientDrawable().apply {
                                    cornerRadius = dp(15).toFloat()
                                    setColor(if (mine) Color.rgb(190, 198, 210) else Color.rgb(35, 39, 46))
                                }
                            }
                            bubble.setOnClickListener {
                                selectedReply = snapshot
                                replyPreview.text = translate("رد على:") + " " + snapshot.getString("text").orEmpty().take(100) + "  ×"
                                replyPreview.visibility = View.VISIBLE
                                input.requestFocus()
                            }
                            bubble.setOnLongClickListener {
                                AlertDialog.Builder(activity)
                                    .setIcon(com.marcorx6.library.R.drawable.ic_action_delete)
                                    .setMessage(translate("حذف هذه الرسالة لدى الطرفين؟"))
                                    .setNegativeButton("إلغاء", null)
                                    .setPositiveButton("حذف") { _, _ ->
                                        messaging.deleteMessage(user.uid, otherUid, snapshot.id)
                                            .addOnFailureListener { error ->
                                                toast(error.localizedMessage ?: translate("تعذر حذف الرسالة."))
                                            }
                                    }.show()
                                true
                            }
                            val line = LinearLayout(activity).apply {
                                gravity = if (mine) Gravity.END else Gravity.START
                                addView(bubble)
                            }
                            messages.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
                        }
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }, { error ->
                        android.util.Log.e("MarcoMessages", "Conversation messages listener failed", error)
                        toast(error.localizedMessage ?: "تعذر تحديث المحادثة. تحقق من الاتصال.")
                    })
                }.addOnFailureListener { error ->
                    android.util.Log.e("MarcoMessages", "Public profile load failed", error)
                    toast(error.localizedMessage ?: "تعذر فتح ملف المستخدم.")
                }
            }.addOnFailureListener { error ->
                android.util.Log.e("MarcoMessages", "Conversation initialization failed", error)
                toast(error.localizedMessage ?: "تعذر تهيئة المحادثة.")
            }
        }.addOnFailureListener { error ->
            android.util.Log.e("MarcoMessages", "Mutual-follow check failed", error)
            toast(error.localizedMessage ?: "تعذر التحقق من صلاحية المراسلة.")
        }
    }

    private fun showSocialConnections(uid: String, showFollowers: Boolean) {
        val currentUser = authRepository.currentUser
        if (currentUser == null || currentUser.isAnonymous || currentUser.uid != uid) {
            toast("قوائم المتابعين متاحة لصاحب الحساب فقط.")
            return
        }
        val title = translate(if (showFollowers) "المتابعون" else "يتابع")
        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val loading = TextView(activity).apply {
            text = translate("جارٍ تحميل القائمة...")
            textSize = 13f
            setTextColor(Color.rgb(190, 198, 210))
            setPadding(dp(4), dp(18), dp(4), dp(18))
        }
        listContainer.addView(loading)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(listContainer)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(translate("إغلاق"), null)
            .create()
        dialog.setOnShowListener { styleDialog(dialog) }
        dialog.show()
        val idsTask = if (showFollowers) userDocuments.loadFollowerIds(uid)
            else userDocuments.loadFollowingIds(uid)
        idsTask.addOnSuccessListener { ids ->
            if (!dialog.isShowing) return@addOnSuccessListener
            if (ids.isEmpty()) {
                loading.text = translate(if (showFollowers) "لا يوجد متابعون بعد." else "لا تتابع أي حساب حالياً.")
                return@addOnSuccessListener
            }
            val tasks = ids.map { userDocuments.loadPublicProfile(it) }
            com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks)
                .addOnSuccessListener { profiles ->
                    if (!dialog.isShowing) return@addOnSuccessListener
                    listContainer.removeAllViews()
                    profiles.filter { it.exists() }.forEach { profile ->
                        val name = profile.getString("displayName")?.takeIf { it.isNotBlank() }
                            ?: translate("مستخدم MARCO")
                        val bio = profile.getString("bio").orEmpty()
                        val row = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            layoutDirection = View.LAYOUT_DIRECTION_RTL
                            setPadding(dp(12), dp(11), dp(12), dp(11))
                            background = GradientDrawable().apply {
                                cornerRadius = dp(17).toFloat()
                                setColor(Color.rgb(23, 26, 32))
                                setStroke(dp(1), Color.rgb(76, 84, 97))
                            }
                            isClickable = true
                            isFocusable = true
                            setOnClickListener { showPublicProfile(profile.id) }
                        }
                        val avatar = ImageView(activity).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageResource(com.marcorx6.library.R.drawable.ic_nav_profile)
                            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.rgb(39, 43, 51))
                                setStroke(dp(1), Color.rgb(145, 153, 166))
                            }
                            clipToOutline = true
                            contentDescription = translate("صورة الحساب")
                        }
                        row.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                            marginEnd = dp(12)
                        })
                        val identity = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        identity.addView(TextView(activity).apply {
                            text = name
                            textSize = 14f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        })
                        if (bio.isNotBlank()) identity.addView(TextView(activity).apply {
                            text = bio
                            textSize = 11f
                            setTextColor(Color.rgb(160, 168, 181))
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setPadding(0, dp(3), 0, 0)
                        })
                        row.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
                        row.addView(TextView(activity).apply {
                            text = "‹"
                            textSize = 25f
                            setTextColor(Color.rgb(150, 158, 171))
                            gravity = Gravity.CENTER
                        }, LinearLayout.LayoutParams(dp(24), dp(42)))
                        val publicPhotoData = profile.getString("photoData")
                        val publicAvatar = publicPhotoData?.takeIf { it.isNotBlank() }?.let { encoded ->
                            runCatching {
                                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (publicAvatar != null) {
                            avatar.setImageBitmap(publicAvatar)
                            avatar.imageTintList = null
                        } else {
                            profile.getString("photoUrl")?.takeIf { it.startsWith("https://") }?.let { photoUrl ->
                                Thread {
                                    val bitmap = runCatching {
                                        java.net.URL(photoUrl).openConnection().apply {
                                            connectTimeout = 6000
                                            readTimeout = 6000
                                            useCaches = true
                                        }.getInputStream().use { BitmapFactory.decodeStream(it) }
                                    }.getOrNull()
                                    if (bitmap != null && !activity.isFinishing && !activity.isDestroyed) {
                                        avatar.post {
                                            avatar.setImageBitmap(bitmap)
                                            avatar.imageTintList = null
                                        }
                                    }
                                }.start()
                            }
                        }
                        listContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = dp(9)
                        })
                    }
                    if (listContainer.childCount == 0) {
                        listContainer.addView(TextView(activity).apply {
                            text = translate("لا توجد ملفات عامة متاحة للعرض حالياً.")
                            textSize = 12f
                            setTextColor(Color.rgb(160, 168, 181))
                            setPadding(dp(4), dp(18), dp(4), dp(18))
                        })
                    }
                }
                .addOnFailureListener { error ->
                    android.util.Log.e("MarcoSocial", "Failed to load public profiles for social list", error)
                    val code = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name
                        ?: error.javaClass.simpleName
                    if (dialog.isShowing) {
                        loading.text = translate("تعذر تحميل بيانات الحسابات (") + code + ")"
                    }
                }
        }.addOnFailureListener { error ->
            android.util.Log.e("MarcoSocial", "Failed to load social connection IDs for uid=$uid", error)
            val code = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name
                ?: error.javaClass.simpleName
            val message = when (code) {
                "PERMISSION_DENIED" -> "رفضت قواعد Firestore قراءة قائمة المتابعين (PERMISSION_DENIED)."
                "FAILED_PRECONDITION" -> "استعلام المتابعين يحتاج إلى فهرس Firestore (FAILED_PRECONDITION)."
                "UNAVAILABLE" -> "خدمة Firestore غير متاحة حالياً؛ تحقق من الاتصال."
                else -> "تعذر تحميل القائمة (" + code + ")."
            }
            if (dialog.isShowing) loading.text = translate(message)
        }
    }

    private fun addPublicAvatar(identity: LinearLayout, snapshot: com.google.firebase.firestore.DocumentSnapshot, dark: Boolean) {
        val avatar = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) Color.rgb(35, 38, 45) else Color.rgb(222, 226, 232))
                setStroke(dp(2), Color.rgb(190, 198, 210))
            }
            clipToOutline = true
            setImageResource(com.marcorx6.library.R.drawable.ic_nav_profile)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
        }
        identity.addView(avatar, LinearLayout.LayoutParams(dp(92), dp(92)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })
        val encoded = snapshot.getString("photoData")
        if (!encoded.isNullOrBlank()) {
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()?.let {
                avatar.setImageBitmap(it)
                avatar.imageTintList = null
            }
        } else {
            snapshot.getString("photoUrl")?.takeIf { it.startsWith("https://") }?.let { photoUrl ->
                Thread {
                    val bitmap = runCatching {
                        java.net.URL(photoUrl).openConnection().apply {
                            connectTimeout = 6000
                            readTimeout = 6000
                            useCaches = true
                        }.getInputStream().use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                    if (bitmap != null && !activity.isFinishing && !activity.isDestroyed) {
                        avatar.post {
                            avatar.setImageBitmap(bitmap)
                            avatar.imageTintList = null
                        }
                    }
                }.apply { name = "marco-public-avatar-loader" }.start()
            }
        }
    }

    private fun addPublicFollowControls(page: LinearLayout, targetUid: String, dark: Boolean) {
        val currentUser = authRepository.currentUser
        val isOwnProfile = currentUser?.uid == targetUid
        val stats = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.rgb(19, 22, 28))
                setStroke(dp(1), Color.rgb(82, 91, 105))
            }
        }
        val followers = TextView(activity).apply {
            text = "—  " + translate("المتابعون")
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(if (dark) Color.rgb(205, 212, 222) else Color.rgb(65, 72, 84))
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        val following = TextView(activity).apply {
            text = "—  " + translate("يتابع")
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(if (dark) Color.rgb(205, 212, 222) else Color.rgb(65, 72, 84))
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        stats.addView(followers, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(following, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        if (isOwnProfile) {
            listOf(followers, following).forEach { counter ->
                counter.isClickable = true
                counter.isFocusable = true
                counter.background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.rgb(29, 33, 40))
                }
                counter.setOnClickListener {
                    showSocialConnections(targetUid, counter === followers)
                }
            }
        }
        fun renderCounts(followerCount: Long, followingCount: Long) {
            followers.text = followerCount.toString() + "  " + translate("المتابعون")
            following.text = followingCount.toString() + "  " + translate("يتابع")
        }
        fun loadCachedPublicCounts() {
            userDocuments.loadPublicProfile(targetUid).addOnSuccessListener { profile ->
                if (profile.exists()) {
                    renderCounts(
                        profile.getLong("followerCount") ?: 0L,
                        profile.getLong("followingCount") ?: 0L
                    )
                } else if (isOwnProfile) {
                    userDocuments.loadFollowCounts(targetUid).addOnSuccessListener { counts ->
                        renderCounts(counts.first, counts.second)
                    }
                }
            }
        }
        if (isOwnProfile) {
            userDocuments.refreshPublicFollowCounts(targetUid)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        android.util.Log.w("MarcoSocial", "Public follow-count refresh failed", task.exception)
                    }
                    loadCachedPublicCounts()
                }
        } else {
            loadCachedPublicCounts()
        }
        if (isOwnProfile) return
        if (currentUser == null || currentUser.isAnonymous) {
            action(
                page,
                translate("سجّل الدخول للمتابعة"),
                translate("أنشئ حساب MARCO لمتابعة هذا الملف الشخصي"),
                dark,
                run = ::show
            )
            return
        }
        val followButton = TextView(activity).apply {
            text = translate("جارٍ التحقق...")
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(35, 38, 45))
                setStroke(dp(1), Color.rgb(105, 113, 126))
            }
            setTextColor(Color.rgb(190, 198, 210))
            contentDescription = translate("جارٍ التحقق من حالة المتابعة")
            isEnabled = false
        }
        page.addView(followButton, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val messageButton = TextView(activity).apply {
            text = translate("رسالة")
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(18, 20, 24))
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(Color.rgb(190, 198, 210))
                setStroke(dp(1), Color.rgb(145, 153, 166))
            }
            visibility = View.GONE
            setOnClickListener { openConversation(targetUid) }
        }
        page.addView(messageButton, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        fun refreshMessageAvailability() {
            messaging.canMessage(currentUser.uid, targetUid).addOnSuccessListener { allowed ->
                messageButton.visibility = if (allowed) View.VISIBLE else View.GONE
            }
        }
        userDocuments.isFollowing(currentUser.uid, targetUid).addOnSuccessListener { initialFollowing ->
            var followingState = initialFollowing
            fun updateLabel() {
                followButton.text = translate(if (followingState) "تتابعه ✓" else "＋ متابعة")
                followButton.contentDescription = translate(
                    if (followingState) "أنت تتابع هذا المستخدم. اضغط لإلغاء المتابعة"
                    else "اضغط لمتابعة هذا المستخدم"
                )
                followButton.background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    if (followingState) {
                        setColor(Color.rgb(30, 33, 40))
                        setStroke(dp(1), Color.rgb(190, 198, 210))
                    } else {
                        setColor(Color.rgb(190, 198, 210))
                        setStroke(dp(1), Color.rgb(145, 153, 166))
                    }
                }
                followButton.setTextColor(
                    if (followingState) Color.rgb(215, 220, 228) else Color.rgb(20, 23, 29)
                )
            }
            updateLabel()
            refreshMessageAvailability()
            followButton.isEnabled = true
            followButton.setOnClickListener {
                followButton.isEnabled = false
                followButton.text = translate("جارٍ تحديث المتابعة...")
                val nextState = !followingState
                userDocuments.setFollowing(currentUser.uid, targetUid, nextState)
                    .addOnSuccessListener {
                        followingState = nextState
                        updateLabel()
                        followButton.isEnabled = true
                        refreshMessageAvailability()
                        userDocuments.refreshPublicFollowCounts(currentUser.uid)
                        followButton.animate().cancel()
                        followButton.scaleX = 0.97f
                        followButton.scaleY = 0.97f
                        followButton.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }
                    .addOnFailureListener {
                        updateLabel()
                        followButton.isEnabled = true
                        toast("تعذرت مزامنة المتابعة. تحقق من الاتصال وحاول مجدداً.")
                    }
            }
        }.addOnFailureListener {
            followButton.text = translate("تعذر تحميل المتابعة")
            followButton.contentDescription = translate("تعذر تحميل حالة المتابعة")
            followButton.isEnabled = false
        }
    }

    private fun profileSectionCard(dark: Boolean): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(if (dark) Color.rgb(19, 22, 28) else Color.WHITE)
            setStroke(dp(1), if (dark) Color.rgb(82, 91, 105) else Color.rgb(199, 207, 218))
        }
        elevation = dp(2).toFloat()
    }

    private fun profileGameCard(game: Game, dark: Boolean, footer: String): LinearLayout {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(if (dark) Color.rgb(30, 33, 40) else Color.rgb(247, 248, 250))
                setStroke(dp(1), if (dark) Color.rgb(76, 84, 97) else Color.rgb(205, 211, 220))
            }
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(55, 190, 198, 210)),
                null,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
            contentDescription = game.name + "، " + translate(game.platform)
            setOnClickListener { openGameDetails(game) }
        }
        val cover = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (dark) Color.rgb(42, 46, 55) else Color.rgb(225, 229, 235))
            }
            clipToOutline = true
            contentDescription = translate("غلاف اللعبة") + " " + game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
            loadCover(cover, game.coverLocalPath, game.coverUrl)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
            cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        card.addView(cover, LinearLayout.LayoutParams(dp(112), dp(132)))
        card.addView(TextView(activity).apply {
            text = game.name
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (dark) Color.WHITE else Color.rgb(25, 29, 36))
            setPadding(dp(2), dp(8), dp(2), dp(3))
        }, LinearLayout.LayoutParams(dp(112), -2))
        card.addView(TextView(activity).apply {
            text = if (footer.isBlank()) game.platform else game.platform + "  •  " + footer
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (dark) Color.rgb(165, 174, 188) else Color.rgb(100, 108, 120))
            setPadding(dp(2), 0, dp(2), 0)
        }, LinearLayout.LayoutParams(dp(112), -2))
        return card
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
            userDocuments.loadUser(user.uid).continueWithTask { profileTask ->
                if (!profileTask.isSuccessful) {
                    throw profileTask.exception ?: IllegalStateException("تعذر تحميل إعدادات الملف")
                }
                val profile = profileTask.result
                userDocuments.saveProfile(
                    uid = user.uid,
                    displayName = user.displayName,
                    bio = profile.getString("bio"),
                    photoUrl = user.photoUrl?.toString(),
                    photoData = encoded,
                    publicProfile = profile.getBoolean("publicProfile") ?: false,
                    showFavorites = profile.getBoolean("showFavorites") ?: true,
                    favoriteGames = games().filter { it.isFavorite }.take(30).map { game ->
                        mapOf("name" to game.name, "platform" to game.platform, "coverUrl" to game.coverUrl)
                    }
                )
            }.addOnSuccessListener {
                profilePhotoUploadInProgress = false
                toast("تم تحديث صورة الحساب")
                val returnToEditor = returnToProfileEditorAfterPhotoPick
                returnToProfileEditorAfterPhotoPick = false
                if (returnToEditor) showEditProfile() else show()
            }.addOnFailureListener { error ->
                profilePhotoUploadInProgress = false
                returnToProfileEditorAfterPhotoPick = false
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
        } while (output.size() > 160_000 && quality >= 30)
        scaled.recycle()
        require(output.size() <= 180_000) { "الصورة كبيرة جداً للملف العام؛ اختر صورة أصغر." }
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
        clearAccountListeners()
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

    /** Keep the composer above the keyboard while only the message list resizes. */
    private fun showConversationPage(page: LinearLayout) {
        val dark = true
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val safe = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(0, safe.top, 0, maxOf(safe.bottom, ime.bottom))
            insets
        }
        page.animate().cancel()
        page.clearAnimation()
        page.alpha = 1f
        page.translationY = 0f
        setContentView(page)
        activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        androidx.core.view.ViewCompat.requestApplyInsets(page)
        applyUnifiedSkin(page)
        page.animate().cancel()
        page.alpha = 1f
        page.translationY = 0f
    }

    private fun showAccountPage(page: LinearLayout) {
        val dark = true
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
                        clearAccountListeners()
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

    /** Stop account-scoped Firestore listeners before leaving or changing the Firebase session. */
    private fun clearAccountListeners() {
        notificationsListener?.remove()
        notificationsListener = null
        inboxListener?.remove()
        inboxListener = null
        messagesListener?.remove()
        messagesListener = null
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
