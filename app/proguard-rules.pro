# WorkManager persists worker class names. Keep both the current class and
# the legacy fully-qualified name so work enqueued by older app versions can
# still be instantiated after this package refactor.
-keep class com.marcorx6.library.notification.BlogNotificationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.marcorx6.library.BlogNotificationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Preserve generic and annotation metadata used by AndroidX and Google APIs.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
