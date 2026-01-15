package com.example.opencv.vision

import androidx.compose.ui.geometry.Offset

data class TrackingResult(
    val detected: Boolean = false,
    val isObjectInZoom: Boolean = false,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val objectName: String = "",
    val boundaries: List<Offset> = emptyList()
)