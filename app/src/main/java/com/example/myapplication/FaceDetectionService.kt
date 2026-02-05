package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.Executors

class FaceDetectionService : LifecycleService(), FacePostureDetector.Listener {

    private lateinit var overlayManager: OverlayManager
    private lateinit var headsUpNotificationManager: HeadsUpNotificationManager
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var facePostureDetector: FacePostureDetector

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        headsUpNotificationManager = HeadsUpNotificationManager(this)
        facePostureDetector = FacePostureDetector(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification())
        startCamera()
        return START_STICKY
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    facePostureDetector.detect(imageProxy)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResult(result: FaceLandmarkerResult) {
        Log.i(TAG, "Detected faces: ${result.faceLandmarks().size}")
    }

    override fun onError(error: Exception) {
        Log.e(TAG, "Face posture detection error", error)
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
            .setContentText("Monitoring face posture.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        overlayManager.destroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        private const val TAG = "FaceDetectionService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "face_detection_service_channel"
    }
}
