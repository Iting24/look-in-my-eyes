package com.example.myapplication

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

interface FaceLandmarkerListener {
    fun onError(error: String, errorCode: Int)
    fun onResults(resultBundle: FacePostureDetector.ResultBundle)
}
