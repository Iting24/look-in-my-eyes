package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2

class PostureAnalyzer(
    private val context: Context,
    private val listener: PostureListener
) : ImageAnalysis.Analyzer {

    private val backgroundExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private var poseLandmarker: PoseLandmarker? = null
    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupMediaPipe()
    }

    private fun setupMediaPipe() {
        backgroundExecutor.execute {
            try {
                // Setup Pose Landmarker
                val poseBaseOptions = BaseOptions.builder().setAssetManagerPath("pose_landmarker_full.task").build()
                val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(poseBaseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onPoseResult)
                    .setErrorListener { error -> Log.e(TAG, "Pose Landmarker Error: ${error.message}") }
                    .build()
                poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)

                // Setup Face Landmarker
                val faceBaseOptions = BaseOptions.builder().setAssetManagerPath("face_landmarker.task
").build()
                val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(faceBaseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onFaceResult)
                    .setErrorListener { error -> Log.e(TAG, "Face Landmarker Error: ${error.message}") }
                    .build()
                faceLandmarker = FaceLandmarker.createFromOptions(context, faceOptions)

            } catch (e: Exception) {
                Log.e(TAG, "Error setting up MediaPipe: ${e.message}")
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer) }
        imageProxy.close()

        // MediaPipe expects upright images
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            // Front camera requires horizontal flip
            postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            matrix,
            true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val frameTimestamp = System.currentTimeMillis()

        // Run both detectors asynchronously
        poseLandmarker?.detectAsync(mpImage, frameTimestamp)
        faceLandmarker?.detectAsync(mpImage, frameTimestamp)
    }

    private fun onPoseResult(result: PoseLandmarkerResult, input: MPImage) {
        val issues = mutableSetOf<PostureIssue>()

        result.landmarks().firstOrNull()?.let { landmarks ->
            // Hunchback detection
            val leftShoulder = landmarks[11]
            val leftHip = landmarks[23]
            if (leftShoulder.y() > leftHip.y() + HUNCHBACK_THRESHOLD) {
                issues.add(PostureIssue.HUNCHBACK)
            }

            // Lying down detection
            val rightShoulder = landmarks[12]
            val shoulderAngle = Math.toDegrees(atan2((rightShoulder.y() - leftShoulder.y()), (rightShoulder.x() - leftShoulder.x()).toDouble()))
            if (abs(shoulderAngle) > LYING_DOWN_ANGLE_THRESHOLD) {
                 issues.add(PostureIssue.LYING_DOWN)
            }
        }

        // This listener is called first, we'll pass its issues to the face listener to aggregate.
        currentPoseIssues = issues
    }

    private var currentPoseIssues = mutableSetOf<PostureIssue>()

    private fun onFaceResult(result: FaceLandmarkerResult, input: MPImage) {
        val allIssues = currentPoseIssues.toMutableSet() // Start with issues from pose detection

        result.faceLandmarks().firstOrNull()?.let { landmarks ->
            // Head tilt detection
            val leftEye = landmarks[33]
            val rightEye = landmarks[263]
            val eyeAngle = Math.toDegrees(atan2((rightEye.y() - leftEye.y()), (rightEye.x() - leftEye.x()).toDouble()))
            if (abs(eyeAngle) > HEAD_TILT_ANGLE_THRESHOLD) {
                allIssues.add(PostureIssue.HEAD_TILT)
            }

            // Sideways glance detection
            val nose = landmarks[1]
            val imageWidth = input.width
            val noseXNormalized = nose.x()
            if (noseXNormalized < SIDEWAYS_GLANCE_THRESHOLD_MIN || noseXNormalized > SIDEWAYS_GLANCE_THRESHOLD_MAX) {
                allIssues.add(PostureIssue.SIDEWAYS_GLANCE)
            }
        }
        
        // Report all combined issues
        listener.onPostureUpdate(allIssues)
    }

    fun close() {
        backgroundExecutor.shutdown()
        poseLandmarker?.close()
        faceLandmarker?.close()
    }

    companion object {
        private const val TAG = "PostureAnalyzer"
        private const val HUNCHBACK_THRESHOLD = 0.1f // Shoulder Y is 10% lower than hip Y
        private const val HEAD_TILT_ANGLE_THRESHOLD = 15.0 // degrees
        private const val LYING_DOWN_ANGLE_THRESHOLD = 45.0 // degrees from horizontal
        private const val SIDEWAYS_GLANCE_THRESHOLD_MIN = 0.25f // 25% from left edge
        private const val SIDEWAYS_GLANCE_THRESHOLD_MAX = 0.75f // 75% from right edge
    }
}