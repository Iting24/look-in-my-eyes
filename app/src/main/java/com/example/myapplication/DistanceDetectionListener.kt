package com.example.myapplication

interface DistanceDetectionListener {
    /**
     * Called when the distance from the face to the screen is estimated.
     *
     * @param distanceInCm The estimated distance in centimeters. If no face is detected,
     * this value will be Float.MAX_VALUE.
     */
    fun onDistanceUpdate(distanceInCm: Float)
}
