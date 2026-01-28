package com.example.myapplication

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class OverlayManager(private val context: Context) {

    private var overlayView: View? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    fun showOverlay() {
        if (overlayView != null) {
            return // Already shown or animating
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0.0f
        }

        windowManager.addView(overlayView, layoutParams)

        ObjectAnimator.ofFloat(overlayView, "alpha", 0.0f, 0.7f).apply {
            duration = 300
            start()
        }
    }

    fun hideOverlay() {
        overlayView?.let { view ->
            val animator = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0.0f)
            animator.duration = 300
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (view.alpha == 0.0f) {
                        // Ensure the view is still attached to the window before removing
                        if (view.parent != null) {
                            windowManager.removeView(view)
                        }
                        overlayView = null
                    }
                }
            })
            animator.start()
        }
    }

    /**
     * Immediately removes the overlay without animation. 
     * To be used when the service is destroyed.
     */
    fun destroy() {
        overlayView?.let {
            if (it.parent != null) {
                windowManager.removeView(it)
            }
            overlayView = null
        }
    }
}
