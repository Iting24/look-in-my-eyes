package com.example.myapplication

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FacePostureDetector(
    private val context: Context,
    private val listener: Listener
) {
    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("face_landmarker.task")
        val baseOptions = baseOptionsBuilder.build()
        val optionsBuilder =
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
        val options = optionsBuilder.build()
        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun detect(imageProxy: ImageProxy) {
        val mpImage = imageProxy.toMPImage()
        faceLandmarker?.detect(mpImage)?.let {
            listener.onResult(it)
        }
    }

    interface Listener {
        fun onResult(result: FaceLandmarkerResult)
        fun onError(error: Exception)
    }

    companion object {
        private const val TAG = "FacePostureDetector"
    }
}
