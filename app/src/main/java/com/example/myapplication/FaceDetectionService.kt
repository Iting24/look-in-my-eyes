package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors

class FaceDetectionService : LifecycleService(), DistanceDetectionListener {

    private lateinit var overlayManager: OverlayManager
    private lateinit var headsUpNotificationManager: HeadsUpNotificationManager
    private var cameraView: View? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isOverlayShown = false

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        headsUpNotificationManager = HeadsUpNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification())
        startCamera()
        return START_STICKY
    }

    @OptIn(ExperimentalCamera2Interop::class)
    @SuppressLint("InflateParams", "UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val layoutParams = WindowManager.LayoutParams(
                1, 1,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSPARENT
            )
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            cameraView = LayoutInflater.from(this).inflate(R.layout.camera_preview, null)
            windowManager.addView(cameraView, layoutParams)

            val preview = Preview.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                val camera2CameraInfo = Camera2CameraInfo.from(camera.cameraInfo)
                
                val focalLengths = camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val sensorSize = camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)

                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    sensorSize?.let { nonNullSensorSize ->
                        val focalLength = focalLengths[0]
                        val faceDistanceDetector = FaceDistanceDetector(focalLength, nonNullSensorSize, this)
                        imageAnalysis.setAnalyzer(cameraExecutor, faceDistanceDetector)
                    } ?: run {
                        Log.e(TAG, "Sensor size is null and required for distance estimation.")
                    }
                } else {
                    Log.e(TAG, "Focal lengths are null or empty; cannot estimate distance.")
                }

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDistanceUpdate(distanceInCm: Float) {
        if (distanceInCm < DISTANCE_THRESHOLD_CM) {
            if (!isOverlayShown) {
                overlayManager.showOverlay()
                headsUpNotificationManager.showNotification()
                isOverlayShown = true
            }
        } else {
            if (isOverlayShown) {
                overlayManager.hideOverlay()
                isOverlayShown = false
            }
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Face Detection Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Face Detection Active")
            .setContentText("Monitoring screen distance.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        overlayManager.destroy()
        cameraView?.let { 
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it) 
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        private const val TAG = "FaceDetectionService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "face_detection_service_channel"
        private const val DISTANCE_THRESHOLD_CM = 30f
    }
}
