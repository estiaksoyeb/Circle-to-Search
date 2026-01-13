/*
 *
 *  * Copyright (C) 2025 AKS-Labs (original author)
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.akslabs.circletosearch

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.akslabs.circletosearch.data.ActionType
import com.akslabs.circletosearch.data.BitmapRepository
import com.akslabs.circletosearch.data.GestureType
import com.akslabs.circletosearch.data.OverlayConfigurationManager
import com.akslabs.circletosearch.data.OverlaySegment
import com.akslabs.circletosearch.data.TextRepository
import com.akslabs.circletosearch.utils.ActionExecutor
import com.akslabs.circletosearch.utils.OverlayViewManager
import com.akslabs.circletosearch.utils.TextScraper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class CircleToSearchAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private val overlayViews = mutableListOf<View>()
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private lateinit var configManager: OverlayConfigurationManager
    
    // Components
    private lateinit var textScraper: TextScraper
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var overlayViewManager: OverlayViewManager
    
    private var bubbleView: View? = null
    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private val overlayPrefs by lazy { getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE) }
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "bubble_enabled") {
            updateBubbleState()
        }
    }
    
    private val overlayPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        configManager = OverlayConfigurationManager(this)
        
        // Initialize Components
        textScraper = TextScraper(this)
        actionExecutor = ActionExecutor(this)
        overlayViewManager = OverlayViewManager(this, windowManager!!)
        
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        overlayPrefs.registerOnSharedPreferenceChangeListener(overlayPrefsListener)
        
        updateBubbleState()
        updateOverlay()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOverlay()
    }

    private fun updateBubbleState() {
        if (prefs.getBoolean("bubble_enabled", false)) {
            showBubble()
        } else {
            hideBubble()
        }
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val params = WindowManager.LayoutParams(
            100, 100,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        bubbleView = View(this).apply {
            setBackgroundResource(R.mipmap.ic_launcher)
            elevation = 10f
            
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            
            @SuppressLint("ClickableViewAccessibility")
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(this, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX - initialTouchX) < 10 && Math.abs(event.rawY - initialTouchY) < 10) {
                            performCapture()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            windowManager?.addView(bubbleView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideBubble() {
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bubbleView = null
        }
    }

    private fun updateOverlay() {
        val config = configManager.getConfig()
        
        if (!config.isEnabled) {
            clearOverlay()
            return
        }
        
        val currentOrientation = resources.configuration.orientation
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE && !config.isEnabledInLandscape) {
             clearOverlay()
            return
        }

        if (overlayViews.size == config.segments.size) {
            // Update existing views
            config.segments.forEachIndexed { index, segment ->
                val view = overlayViews[index]
                overlayViewManager.updateViewLayout(view, segment)
                
                // Update Color
                if (config.isVisible) {
                    val colors = listOf(Color.parseColor("#80FF0000"), Color.parseColor("#8000FF00"), Color.parseColor("#800000FF"), Color.parseColor("#80FFFF00"), Color.parseColor("#80FF00FF"))
                    view.setBackgroundColor(colors[index % colors.size])
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
                
                attachTouchListener(view, segment, index)
            }
        } else {
            // Rebuild
            clearOverlay()
            
            config.segments.forEachIndexed { index, segment ->
                val view = overlayViewManager.createOverlayView(segment, config.isVisible)
                
                if (config.isVisible) {
                    val colors = listOf(Color.parseColor("#80FF0000"), Color.parseColor("#8000FF00"), Color.parseColor("#800000FF"), Color.parseColor("#80FFFF00"), Color.parseColor("#80FF00FF"))
                    view.setBackgroundColor(colors[index % colors.size])
                }

                attachTouchListener(view, segment, index)
                
                try {
                    windowManager?.addView(view, overlayViewManager.getLayoutParams(segment))
                    overlayViews.add(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun clearOverlay() {
        overlayViews.forEach { 
            try { windowManager?.removeView(it) } catch(e: Exception) {} 
        }
        overlayViews.clear()
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListener(view: View, segment: OverlaySegment, segmentIndex: Int) {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val action = segment.gestures[GestureType.DOUBLE_TAP] ?: ActionType.NONE
                if (action != ActionType.NONE) { performAction(action, segment); return true }
                return false
            }
            
            override fun onLongPress(e: MotionEvent) {
                val action = segment.gestures[GestureType.LONG_PRESS] ?: ActionType.NONE
                if (action != ActionType.NONE) performAction(action, segment)
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                 propagateSingleTap(view, e.rawX, e.rawY)
                 return false
            }
            
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                        if (diffX > 0) {
                             val action = segment.gestures[GestureType.SWIPE_RIGHT] ?: ActionType.NONE
                             if (action != ActionType.NONE) { performAction(action, segment); return true }
                        } else {
                            val action = segment.gestures[GestureType.SWIPE_LEFT] ?: ActionType.NONE
                             if (action != ActionType.NONE) { performAction(action, segment); return true }
                        }
                    }
                } else {
                    if (Math.abs(diffY) > 50 && Math.abs(velocityY) > 100) {
                        if (diffY > 0) {
                             // Swipe Down
                             val action = segment.gestures[GestureType.SWIPE_DOWN] ?: ActionType.NONE
                             if (action != ActionType.NONE) {
                                 performAction(action, segment) 
                             } else {
                                 // Smart Swipe Logic
                                 val screenWidth = resources.displayMetrics.widthPixels
                                 val isFirstOverlay = segmentIndex == 0
                                 val isFullWidth = segment.width >= screenWidth
                                 
                                 if (isFirstOverlay && isFullWidth) {
                                     val touchX = e1.rawX
                                     if (touchX < (screenWidth / 2)) {
                                         performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                                     } else {
                                         performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                                     }
                                 } else {
                                     performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                                 }
                             }
                             return true
                        } else {
                            // Swipe Up
                             val action = segment.gestures[GestureType.SWIPE_UP] ?: ActionType.NONE
                             if (action != ActionType.NONE) { performAction(action, segment); return true }
                        }
                    }
                }
                return false
            }
        })
        
        var lastTapTime: Long = 0
        var tapCount = 0
        
        view.setOnTouchListener { _, event ->
             if (event.action == MotionEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 400) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                lastTapTime = currentTime
                
                if (tapCount == 3) {
                     val action = segment.gestures[GestureType.TRIPLE_TAP] ?: ActionType.NONE
                     if (action != ActionType.NONE) {
                         performAction(action, segment)
                         tapCount = 0 
                         return@setOnTouchListener true
                     }
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }
    
    private fun performAction(action: ActionType, segment: OverlaySegment) {
        if (action == ActionType.NONE) return
        actionExecutor.performAction(action, segment, segment.gestureData)
    }
    
    private fun propagateSingleTap(view: View, x: Float, y: Float) {
        val params = view.layoutParams as WindowManager.LayoutParams
        val originalFlags = params.flags
        
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager?.updateViewLayout(view, params)
        
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    restoreFlags()
                }
    
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    restoreFlags()
                }
                
                fun restoreFlags() {
                    handler.post {
                        params.flags = originalFlags
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (e: Exception) {
                        }
                    }
                }
            }, null)
        }, 100)
    }

    private fun performCapture() {
        // Scrape Text
        val textNodes = textScraper.scrapeScreenText()
        TextRepository.setTextNodes(textNodes)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            if (bitmap == null) {
                                hardwareBuffer.close()
                                return
                            }

                            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()

                            if (copy == null) {
                                return
                            }
                            
                            BitmapRepository.setScreenshot(copy)
                            launchOverlay()
                            
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        android.util.Log.e("CircleToSearch", "Screenshot failed with error code: $errorCode")
                    }
                }
            )
        }
    }

    private fun launchOverlay() {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        private var instance: CircleToSearchAccessibilityService? = null

        fun triggerCapture() {
            instance?.performCapture()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        overlayPrefs.unregisterOnSharedPreferenceChangeListener(overlayPrefsListener)
        clearOverlay()
        hideBubble()
    }
}
