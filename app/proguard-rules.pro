# WorkManager creates workers by class name at runtime. Keep the worker and
# the constructor used by its default WorkerFactory while allowing other code
# to be optimized and obfuscated.
-keep class com.marcorx6.library.BlogNotificationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Preserve generic and annotation metadata used by AndroidX and Google APIs.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
