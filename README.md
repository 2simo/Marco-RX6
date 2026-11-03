# MARCO RX6

[العربية](#marco-rx6) | [English](#marco-rx6-english)

تطبيق أندرويد مبني بـ Kotlin لتنظيم وتصفح مكتبة ألعاب PSP وPS1 وPS2 وPS3 وPS4، ومرتبط بمدونة MARCO RX6 على منصة بلوجر.

## المميزات
- مزامنة الألعاب من مدونة Blogger
- بحث وفلترة وترتيب (المنصة، النوع، السنة، المفضلة، حالة اللعب)
- نشر وتعديل المقالات (للمشرفين)
- رفع الأغلفة إلى Google Drive
- إشعارات عند نشر مقال جديد
- مدير تنزيلات
- نسخة احتياطية: تصدير واستيراد
- وضع داكن وفاتح مع ألوان قابلة للتخصيص

## التقنيات
Kotlin · Android (minSdk 23) · WorkManager · Google Sign-In · Blogger API · Google Drive API

## البناء
افتح المشروع في Android Studio ثم شغّل التطبيق، أو استعمل:

```bash
gradle assembleDebug
```

للنسخة النهائية (release) تُقرأ بيانات التوقيع من متغيرات البيئة التالية:

- `KEYSTORE_FILE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

---

# MARCO RX6 (English)

[العربية](#marco-rx6) | [English](#marco-rx6-english)

An Android app built with Kotlin for browsing and organizing a PSP / PS1 / PS2 / PS3 / PS4 game library, synced with the MARCO RX6 Blogger blog.

## Features
- Sync games from the Blogger blog
- Search, filter and sort (platform, genre, year, favorites, play status)
- Create and edit posts (admins only)
- Upload covers to Google Drive
- New-post notifications
- Download manager
- Backup: export and import
- Dark / light theme with custom accent colors

## Tech stack
Kotlin · Android (minSdk 23) · WorkManager · Google Sign-In · Blogger API · Google Drive API

## Build
Open the project in Android Studio and run it, or use:

```bash
gradle assembleDebug
```

For release builds, signing details are read from these environment variables:

- `KEYSTORE_FILE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
