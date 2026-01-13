package com.akslabs.circletosearch.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.widget.Toast
import com.akslabs.circletosearch.CircleToSearchAccessibilityService
import com.akslabs.circletosearch.data.ActionType
import com.akslabs.circletosearch.data.OverlaySegment

class ActionExecutor(private val service: AccessibilityService) {

    private var isFlashlightOn = false

    fun performAction(action: ActionType, segment: OverlaySegment, gestureData: Map<com.akslabs.circletosearch.data.GestureType, String>) {
        // Haptic feedback
        val vibrator = service.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }

        when(action) {
            ActionType.SCREENSHOT -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            ActionType.FLASHLIGHT -> toggleFlashlight()
            ActionType.HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ActionType.BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionType.RECENTS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            ActionType.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }
            ActionType.OPEN_NOTIFICATIONS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            ActionType.OPEN_QUICK_SETTINGS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            ActionType.OPEN_APP -> {
                // Open App Logic - Need gesture type to find package name.
                // Assuming passed map is accessible.
                // We need to pass the specific gesture key, or just pass the package name directly if we know it.
                // Refactoring: Logic in Service found the gesture key.
                // Let's rely on caller to pass package name if needed, or handle it here if we pass Segment.
                // We'll iterate gestures to find OPEN_APP.
                val gestureType = segment.gestures.entries.firstOrNull { it.value == ActionType.OPEN_APP }?.key
                val packageName = if (gestureType != null) gestureData[gestureType] else null
                
                if (!packageName.isNullOrEmpty()) {
                    val launchIntent = service.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        service.startActivity(launchIntent)
                    }
                }
            }
            ActionType.CTS_LENS -> {
                 val uiPrefs = UIPreferences(service)
                 uiPrefs.setUseGoogleLensOnly(true)
                 CircleToSearchAccessibilityService.triggerCapture()
            }
            ActionType.CTS_MULTI -> {
                 val uiPrefs = UIPreferences(service)
                 uiPrefs.setUseGoogleLensOnly(false)
                 CircleToSearchAccessibilityService.triggerCapture()
            }
            ActionType.SPLIT_SCREEN -> {
                 val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                 if (!success) {
                     Toast.makeText(service, "Split Screen not supported or failed", Toast.LENGTH_SHORT).show()
                 }
            }
            ActionType.SCROLL_TOP -> performScroll(true)
            ActionType.SCROLL_BOTTOM -> performScroll(false)
            ActionType.SCREEN_OFF -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                     Toast.makeText(service, "Screen Off requires Android 9+", Toast.LENGTH_SHORT).show()
                }
            }
            ActionType.TOGGLE_AUTO_ROTATE -> toggleAutoRotate()
            ActionType.MEDIA_PLAY_PAUSE -> injectMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionType.MEDIA_NEXT -> injectMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            ActionType.MEDIA_PREVIOUS -> injectMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            else -> {}
        }
    }

    private fun toggleFlashlight() {
         try {
            val cameraManager = service.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            if (isFlashlightOn) {
                cameraManager.setTorchMode(cameraId, false)
                isFlashlightOn = false
            } else {
                cameraManager.setTorchMode(cameraId, true)
                isFlashlightOn = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun performScroll(toTop: Boolean) {
        val displayMetrics = service.resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        for (i in 0..2) {
            handler.postDelayed({
                val startY = if (toTop) displayMetrics.heightPixels * 0.3f else displayMetrics.heightPixels * 0.7f
                val endY = if (toTop) displayMetrics.heightPixels * 0.7f else displayMetrics.heightPixels * 0.3f
                
                val path = android.graphics.Path().apply {
                    moveTo(centerX, startY)
                    lineTo(centerX, endY)
                }
                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 200)
                val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                
                service.dispatchGesture(gesture, null, null)
            }, i * 250L)
        }
    }

    private fun injectMediaKey(keyCode: Int) {
        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val eventTime = android.os.SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    private fun toggleAutoRotate() {
        if (android.provider.Settings.System.canWrite(service)) {
            val resolver = service.contentResolver
            val current = android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0)
            val next = if (current == 1) 0 else 1
            android.provider.Settings.System.putInt(resolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, next)
            Toast.makeText(service, "Auto Rotate: ${if (next == 1) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        } else {
             Toast.makeText(service, "Permission required for Auto Rotate", Toast.LENGTH_SHORT).show()
             val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                 data = android.net.Uri.parse("package:${service.packageName}")
                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
             }
             service.startActivity(intent)
        }
    }
}
