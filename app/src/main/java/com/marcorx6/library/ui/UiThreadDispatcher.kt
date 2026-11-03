package com.marcorx6.library.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity

internal class UiThreadDispatcher(
    private val activity: ComponentActivity,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    fun post(action: () -> Unit) {
        handler.post {
            if (!activity.isFinishing && !activity.isDestroyed) action()
        }
    }
}
