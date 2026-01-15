package com.example.opencv.vision

import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat

fun ImageProxy.toMat(): Mat {
    val yPlane = planes[0]
    val yBuffer = yPlane.buffer
    val yRowStride = yPlane.rowStride
    
    val mat = Mat(height, width, CvType.CV_8UC1)
    
    if (yRowStride == width) {
        val bytes = ByteArray(yBuffer.remaining())
        yBuffer.get(bytes)
        mat.put(0, 0, bytes)
    } else {
        val rowData = ByteArray(width)
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(rowData)
            mat.put(row, 0, rowData)
        }
    }
    return mat
}