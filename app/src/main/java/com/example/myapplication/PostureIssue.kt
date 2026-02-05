package com.example.myapplication

enum class PostureIssue(val message: String) {
    HUNCHBACK("偵測到駝背，請坐直"),
    HEAD_TILT("偵測到頭部歪斜，請保持頭部正中"),
    LYING_DOWN("偵測到躺姿，請坐起來使用手機"),
    SIDEWAYS_GLANCE("偵測到斜視，請將臉部正對螢幕")
}
