# MARCO RX6 — 8.16

[العربية](#marco-rx6-بالعربية) | [English](#marco-rx6-english)

تطبيق أندرويد لإدارة وتصفح مكتبة ألعاب **PS2 وPS3 وPS4**، مع مزامنة المحتوى من مدونة MARCO RX6 على Blogger.

## المميزات

- مكتبة ألعاب مقسّمة حسب المنصة، مع صفوف أفقية قابلة للتمرير وبطاقات تعرض الغلاف واسم اللعبة.
- البحث والفلترة حسب المنصة والنوع وسنة الإصدار، مع المفضلة وترتيب النتائج.
- صفحة تفاصيل اللعبة، روابط التنزيل، الألعاب المشابهة، والمفضلة.
- عرض عدد المشاهدات ومتوسط التقييم وعدد التقييمات على بطاقات المكتبة.
- يمكن للحسابات المسجلة تقييم اللعبة من نجمة إلى خمس نجوم؛ وضع الزائر لا يسمح بإرسال التقييم.
- تسجيل الدخول عبر Google أو البريد الإلكتروني وكلمة المرور، مع إمكانية التصفح كزائر وإدارة الملف الشخصي.
- ربط حساب Google بصلاحيات Blogger للمشرفين، وإنشاء المقالات وتعديلها ونشرها.
- اختيار أغلفة الألعاب ورفعها إلى Google Drive، مع دعم التخزين المحلي للأغلفة.
- مدير تنزيلات يدعم التنزيلات المستأنفة وإدارة الملفات.
- إعدادات اللغة والمظهر والألوان والخط والحركة والإشعارات ومجلد التنزيلات.
- شاشة بدء وتجربة إعداد أول تشغيل، مع دعم العربية واتجاه RTL.
- إشعارات عند توفر منشورات جديدة، وفحص تحديثات التطبيق.

## المنصات المدعومة

المكتبة تعرض ألعاب **PS2 وPS3 وPS4**. لا يعلن التطبيق عن توفير محاكيات ألعاب مدمجة.

## التقنيات

- Kotlin وAndroid SDK (minSdk 24، targetSdk 36).
- AndroidX Activity وCore وWorkManager.
- Firebase Authentication وCloud Firestore وFirebase App Check.
- Google Sign-In وBlogger API وGoogle Drive API.
- ML Kit للترجمة وتحديد اللغة.

## إصدار التطبيق

- Version name: `8.16`
- Version code: `33`
- Android application ID: `com.marcorx6.library`

## البناء محلياً

يتطلب البناء Java 17 وGradle 9.8.2، بالإضافة إلى إعداد Firebase Android في `app/google-services.json`.

```bash
gradle --no-daemon testReleaseUnitTest
gradle --no-daemon lintRelease
gradle --no-daemon assembleDebug
```

لإنشاء نسخة Release موقّعة، عيّن متغيرات البيئة التالية قبل تشغيل `assembleRelease`:

- `KEYSTORE_FILE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## البناء عبر GitHub Actions

ملف `.github/workflows/build-apk.yml` يشغّل اختبارات Kotlin، وAndroid Lint، ويبني نسختي Release وDebug. ملف Release الناتج يُرفع كـ artifact باسم:

`marco-rx6-release-apk`

يتطلب مسار البناء إعداد أسرار المستودع الخاصة بمفتاح التوقيع: `KEYSTORE_BASE64` و`KEYSTORE_PASSWORD` و`KEY_ALIAS` و`KEY_PASSWORD`.

## Firebase وFirestore

- إعداد تطبيق Firebase Android موجود في `app/google-services.json`.
- قواعد Firestore موجودة في `firestore.rules`، واختباراتها في `tests/firestore.rules.test.mjs`.
- إحصاءات اللعبة تحفظ في `gameMetrics`: مشاهدة واحدة لكل UID/لعبة، وتقييم واحد قابل للتحديث لكل حساب مسجل.
- قواعد Firestore تمنع الزوار/الحسابات المجهولة من إرسال التقييمات.
- Workflow النشر `.github/workflows/deploy-firestore-rules.yml` يشغّل الاختبارات أولاً، ولا ينشر القواعد إلا عند توفر سر `FIREBASE_SERVICE_ACCOUNT`.
- تفعيل Firebase App Check وفرض Enforcement يحتاج إعداداً ومراجعة من Firebase Console؛ لا تفترض أن إعدادات Console تُنشر بمجرد تغيير ملفات المستودع.

**ملاحظة أمنية:** مفتاح Firebase API الموجود في إعداد Android ليس بديلاً عن مفتاح حساب خدمة. قيّده في Google Cloud حسب اسم الحزمة وبصمات شهادات التوقيع وواجهات API اللازمة. لا ترفع مفاتيح حسابات الخدمة أو كلمات المرور أو ملفات keystore إلى المستودع.

## الخصوصية وحذف الحساب

- [سياسة الخصوصية](https://2simo.github.io/Marco-RX6/privacy-policy.html)
- [حذف الحساب](https://2simo.github.io/Marco-RX6/delete-account.html)

---

# MARCO RX6 (English) {#marco-rx6-english}

An Android app for browsing and managing a **PS2, PS3, and PS4** game library, synchronized with the MARCO RX6 Blogger site.

## Features

- Platform-based game library with horizontally scrollable rows and cover/title cards.
- Search, platform/genre/year filters, favorites, and sorting.
- Game details, download links, similar games, and favorite controls.
- View counts, average ratings, and rating counts on library cards.
- Signed-in users can submit or update a 1–5 star rating; anonymous visitors cannot rate.
- Google and email/password sign-in, visitor browsing, and account profile management.
- Blogger account linking for administrators, with post creation, editing, and publishing.
- Game cover selection and Google Drive uploads, with local cover caching.
- Download manager with resumable downloads and file handling.
- Language, appearance, accent color, typography, motion, notification, and download-folder settings.
- First-run setup, startup experience, Arabic translation, and RTL layout support.
- New-post notifications and app update checks.

## Supported platforms

The catalog supports **PS2, PS3, and PS4** games. The app does not claim to include built-in game emulators.

## Technology

Kotlin · Android SDK (minSdk 24 / targetSdk 36) · AndroidX · WorkManager · Firebase Authentication · Cloud Firestore · Firebase App Check · Google Sign-In · Blogger API · Google Drive API · ML Kit.

## App version

- Version name: `8.16`
- Version code: `33`
- Application ID: `com.marcorx6.library`

## Local build

Use Java 17 and Gradle 9.8.2. The Firebase Android configuration must be available at `app/google-services.json`.

```bash
gradle --no-daemon testReleaseUnitTest
gradle --no-daemon lintRelease
gradle --no-daemon assembleDebug
```

For a signed Release build, provide these environment variables before running `assembleRelease`: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

## GitHub Actions

`.github/workflows/build-apk.yml` runs Kotlin unit tests and Android Lint, then builds Release and Debug APKs. The Release APK is uploaded as the `marco-rx6-release-apk` artifact.

The workflow requires repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

## Firebase and Firestore

- Android Firebase configuration: `app/google-services.json`.
- Firestore rules: `firestore.rules`; emulator tests: `tests/firestore.rules.test.mjs`.
- Game engagement data is stored under `gameMetrics`: one view per UID/game and one updatable rating per registered account.
- Anonymous users are denied permission to submit ratings.
- `.github/workflows/deploy-firestore-rules.yml` tests rules before deployment and deploys only when the `FIREBASE_SERVICE_ACCOUNT` secret is configured.
- Firebase App Check setup and enforcement are managed separately in the Firebase Console; repository changes alone do not configure Console enforcement.

**Security:** The Firebase API key in the Android configuration is not a service-account credential. Restrict it in Google Cloud to the app package, approved signing certificate fingerprints, and required APIs. Never commit service-account keys, passwords, or keystore files.

## Privacy and account deletion

- [Privacy policy](https://2simo.github.io/Marco-RX6/privacy-policy.html)
- [Account deletion](https://2simo.github.io/Marco-RX6/delete-account.html)
