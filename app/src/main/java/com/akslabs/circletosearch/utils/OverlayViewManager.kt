package com.akslabs.circletosearch.utils

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.akslabs.circletosearch.data.OverlaySegment

class OverlayViewManager(private val context: Context, private val windowManager: WindowManager) {

    fun createOverlayView(segment: OverlaySegment, isVisible: Boolean): View {
        val view = View(context)
        updateViewStyle(view, segment, isVisible)
        return view
    }

    fun getLayoutParams(segment: OverlaySegment): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            segment.width,
            segment.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = segment.xOffset
        params.y = segment.yOffset
        return params
    }

    fun updateViewLayout(view: View, segment: OverlaySegment) {
        val params = view.layoutParams as WindowManager.LayoutParams
        var changed = false
        if (params.width != segment.width) { params.width = segment.width; changed = true }
        if (params.height != segment.height) { params.height = segment.height; changed = true }
        if (params.x != segment.xOffset) { params.x = segment.xOffset; changed = true }
        if (params.y != segment.yOffset) { params.y = segment.yOffset; changed = true }
        
        if (changed) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateViewStyle(view: View, segment: OverlaySegment, isVisible: Boolean) {
        if (isVisible) {
            // Cycle through some debug colors based on hash or just random? 
            // AccessibilityService used index. We can just set a fixed debug color or pass index.
            // For simplicity, let's use Red for now or handle color in service loop.
            // We'll leave color setting to the service for index-based cycling.
        } else {
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
}
