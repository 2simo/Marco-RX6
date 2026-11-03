package com.marcorx6.library

import android.content.Context
import androidx.work.WorkerParameters
import com.marcorx6.library.notification.BlogNotificationWorker as CurrentBlogNotificationWorker

/**
 * Compatibility entry point for WorkManager jobs persisted by app versions
 * that used the original com.marcorx6.library.BlogNotificationWorker name.
 */
@Deprecated("Use com.marcorx6.library.notification.BlogNotificationWorker")
class BlogNotificationWorker(context: Context, params: WorkerParameters) :
    CurrentBlogNotificationWorker(context, params)
