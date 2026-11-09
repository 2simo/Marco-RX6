package com.marcorx6.library.ui

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Batches lazy view work behind one scroll listener per view tree.
 *
 * Registering one OnScrollChangedListener for every game card makes every scroll
 * dispatch hundreds of callbacks. This scheduler checks pending views in one
 * throttled pass instead, and drops each watcher as soon as its view is visible.
 */
internal object VisibleViewWorkScheduler {
    private val handler = Handler(Looper.getMainLooper())
    private val queues = WeakHashMap<ViewTreeObserver, WeakReference<ObserverQueue>>()

    fun watch(view: View, action: () -> Unit) {
        val watcher = Watcher(view, action)
        view.addOnAttachStateChangeListener(watcher)
        if (view.isAttachedToWindow) watcher.register()
    }

    private class Watcher(
        val view: View,
        private var action: (() -> Unit)?
    ) : View.OnAttachStateChangeListener {
        private var queue: ObserverQueue? = null
        private var completed = false

        fun register() {
            if (completed || queue != null || !view.isAttachedToWindow) return
            val observer = view.viewTreeObserver
            if (!observer.isAlive) {
                view.post { register() }
                return
            }
            val target = synchronized(queues) {
                queues[observer]?.get() ?: ObserverQueue(observer).also {
                    queues[observer] = WeakReference(it)
                }
            }
            queue = target
            target.add(this)
        }

        fun unregister() {
            val current = queue ?: return
            queue = null
            current.remove(this)
        }

        fun dispatchFrom(source: ObserverQueue) {
            if (completed || queue !== source) return
            completed = true
            queue = null
            view.removeOnAttachStateChangeListener(this)
            val callback = action
            action = null
            callback?.invoke()
        }

        override fun onViewAttachedToWindow(view: View) {
            register()
        }

        override fun onViewDetachedFromWindow(view: View) {
            unregister()
        }
    }

    private class ObserverQueue(
        private val observer: ViewTreeObserver
    ) {
        private val pending = LinkedHashSet<Watcher>()
        private var scheduled = false
        private val checkRunnable = Runnable { checkVisibleViews() }
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            // Limit visibility scans to at most about 12 per second while scrolling.
            schedule(80L)
        }

        fun add(watcher: Watcher) {
            if (pending.add(watcher) && pending.size == 1 && observer.isAlive) {
                observer.addOnScrollChangedListener(scrollListener)
            }
            schedule(0L)
        }

        fun remove(watcher: Watcher) {
            pending.remove(watcher)
            if (pending.isEmpty()) dispose()
        }

        private fun schedule(delayMillis: Long) {
            if (scheduled || pending.isEmpty()) return
            scheduled = true
            handler.postDelayed(checkRunnable, delayMillis)
        }

        private fun checkVisibleViews() {
            scheduled = false
            if (pending.isEmpty()) {
                dispose()
                return
            }
            val visible = ArrayList<Watcher>()
            val bounds = Rect()
            pending.forEach { watcher ->
                val view = watcher.view
                if (view.isAttachedToWindow &&
                    view.getGlobalVisibleRect(bounds) &&
                    bounds.width() > 0 && bounds.height() > 0
                ) {
                    visible.add(watcher)
                }
            }
            visible.forEach { watcher ->
                pending.remove(watcher)
                watcher.dispatchFrom(this)
            }
            if (pending.isEmpty()) dispose()
        }

        private fun dispose() {
            handler.removeCallbacks(checkRunnable)
            scheduled = false
            if (observer.isAlive) observer.removeOnScrollChangedListener(scrollListener)
            synchronized(queues) {
                if (queues[observer]?.get() === this) queues.remove(observer)
            }
        }
    }
}
