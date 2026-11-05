# MARCO RX6

[العربية](#marco-rx6) | [English](#marco-rx6-english)

تطبيق أندرويد مبني بـ Kotlin لتنظيم وتصفح مكتبة ألعاب PS2 وPS3 وPS4، ومرتبط بمدونة MARCO RX6 على منصة Blogger.

## المميزات
[سياسة الخصوصية](https://2simo.github.io/Marco-RX6/privacy-policy.html) · [حذف الحساب](https://2simo.github.io/Marco-RX6/delete-account.html)

- مزامنة الألعاب من مدونة Blogger
- بحث وفلترة وترتيب (المنصة، النوع، السنة، المفضلة، حالة اللعب)
- نشر وتعديل المقالات (للمشرفين)
- رفع الأغلفة إلى Google Drive
- تسجيل الدخول باستخدام Google أو البريد الإلكتروني وكلمة المرور، مع وضع الزائر
- ملف حساب سحابي محدود عبر Firebase Authentication وCloud Firestore
- إشعارات عند نشر مقال جديد
- مدير تنزيلات
- وضع داكن وفاتح مع ألوان قابلة للتخصيص

## التقنيات
Kotlin · Android (minSdk 24 / targetSdk 36) · WorkManager · Firebase Authentication · Cloud Firestore · Google Sign-In · Blogger API · Google Drive API

## البناء
افتح المشروع في Android Studio ثم شغّل التطبيق، أو استعمل Gradle 9.8.2:

    gradle --no-daemon assembleDebug

للنسخة النهائية (release) تُقرأ بيانات التوقيع من متغيرات البيئة التالية:
- KEYSTORE_FILE
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD

إعدادات Firebase Android موجودة في `app/google-services.json`. مفتاح Firebase API ليس كلمة مرور أو مفتاح خدمة؛ يجب تقييده في Google Cloud Console باسم الحزمة `com.marcorx6.library` وبصمات شهادات التوقيع المعتمدة، مع تقييد واجهات API المطلوبة فقط. لا تضع مفاتيح حسابات الخدمة أو كلمات المرور أو مفاتيح التوقيع في المستودع.

## الأمان والخصوصية
- قواعد Firestore تمنح كل حساب وصولاً إلى مساره الخاص فقط، مع قيود إضافية على بيانات حساب الزائر.
- يجب تفعيل Firebase App Check في Firebase Console والتحقق من تسجيل التطبيق ومزوّد Play Integrity قبل فرض enforcement على Authentication أو Firestore.
- راجع [سياسة الخصوصية](https://2simo.github.io/Marco-RX6/privacy-policy.html) و[صفحة حذف الحساب](https://2simo.github.io/Marco-RX6/delete-account.html).

---

# MARCO RX6 (English)

[العربية](#marco-rx6) | [English](#marco-rx6-english)

An Android app built with Kotlin for browsing and organizing a PS2 / PS3 / PS4 game library, synced with the MARCO RX6 Blogger blog.

## Features
- Sync games from the Blogger blog
- Search, filter and sort (platform, genre, year, favorites, play status)
- Create and edit posts (admins only)
- Upload covers to Google Drive
- Google and email/password accounts plus visitor mode
- Firebase Authentication and a scoped Cloud Firestore account profile
- New-post notifications
- Download manager
- Dark / light theme with custom accent colors

## Tech stack
Kotlin · Android (minSdk 24 / targetSdk 36) · WorkManager · Firebase Authentication · Cloud Firestore · Google Sign-In · Blogger API · Google Drive API

## Build
Open the project in Android Studio and run it, or install Gradle 9.8.2 and use:

    gradle --no-daemon assembleDebug

For release builds, signing details are read from these environment variables:
- KEYSTORE_FILE
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD

The Firebase Android configuration is stored in `app/google-services.json`. The Firebase API key is an application identifier, not a service-account secret. Restrict it in Google Cloud Console to the Android package `com.marcorx6.library`, the approved signing certificate fingerprints, and only the APIs the app needs. Never commit service-account keys, passwords, or signing keys.

## Security and privacy
- Firestore rules scope each account to its own user path and apply tighter limits to anonymous visitor data.
- Configure Firebase App Check in the Firebase Console and register the app with Play Integrity before enforcing App Check for Authentication or Firestore.
- See the [Privacy Policy](https://2simo.github.io/Marco-RX6/privacy-policy.html) and [Account Deletion page](https://2simo.github.io/Marco-RX6/delete-account.html).
