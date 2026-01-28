package com.example.myapplication

import android.annotation.SuppressLint
import android.util.Log
import android.util.SizeF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDistanceDetector(
    private val focalLength: Float,
    private val sensorSize: SizeF,
    private val listener: DistanceDetectionListener
) : ImageAnalysis.Analyzer {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val distance = (focalLength * REAL_FACE_WIDTH_MM * image.height) / 
                                 (face.boundingBox.height() * sensorSize.height)
                    val distanceInCm = distance / 10f
                    listener.onDistanceUpdate(distanceInCm)
                } else {
                    listener.onDistanceUpdate(Float.MAX_VALUE)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
                listener.onDistanceUpdate(Float.MAX_VALUE)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    companion object {
        private const val TAG = "FaceDistanceDetector"
        private const val REAL_FACE_WIDTH_MM = 140f // Average real-world face width in mm
    }
}
