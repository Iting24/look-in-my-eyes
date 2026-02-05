package com.example.myapplication

interface PostureListener {
    /**
     * Called when posture analysis is complete.
     *
     * @param issues A set of detected posture issues. An empty set means no issues were detected.
     */
    fun onPostureUpdate(issues: Set<PostureIssue>)
}
