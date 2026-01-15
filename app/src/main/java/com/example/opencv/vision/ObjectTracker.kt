package com.example.opencv.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import org.opencv.core.*
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import com.google.android.gms.tasks.Tasks
import kotlin.math.abs

class ObjectTracker {

    private val orb = ORB.create(3000, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20)
    private val matcher = BFMatcher.create(BFMatcher.BRUTEFORCE_HAMMING, false)

    private var refDescriptors: Mat? = null
    private var refObjectCenter: Point? = null
    private var currentObjectName = ""
    private var isLocked = false
    
    private var smoothedDx = 0f
    private var smoothedDy = 0f
    private val smoothingFactor = 0.25f

    private var shouldCaptureNextFrame = false

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .build()
    private val objectDetector = ObjectDetection.getClient(options)

    fun requestLock() {
        shouldCaptureNextFrame = true
    }

    fun reset() {
        refDescriptors?.release()
        refDescriptors = null
        refObjectCenter = null
        shouldCaptureNextFrame = false
        currentObjectName = ""
        isLocked = false
        smoothedDx = 0f
        smoothedDy = 0f
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun analyze(imageProxy: ImageProxy): TrackingResult {
        val mediaImage = imageProxy.image ?: return TrackingResult()
        val imgW = imageProxy.width
        val imgH = imageProxy.height
        
        var isObjectInZoomArea = false
        if (!isLocked) {
            try {
                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val results = Tasks.await(objectDetector.process(inputImage))
                for (obj in results) {
                    val box = obj.boundingBox
                    if (abs(box.centerX() - imgW/2) < 150 && abs(box.centerY() - imgH/2) < 150) {
                        isObjectInZoomArea = true
                        if (shouldCaptureNextFrame) {
                            currentObjectName = translate(obj.labels.firstOrNull()?.text ?: "جسم")
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val rawMat = try { imageProxy.toMat() } catch (_: Exception) { return TrackingResult() }
        val mat = Mat()
        Imgproc.GaussianBlur(rawMat, mat, Size(5.0, 5.0), 0.0)
        rawMat.release()
        
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()

        try {
            orb.detectAndCompute(mat, Mat(), keypoints, descriptors)

            if (shouldCaptureNextFrame && !descriptors.empty()) {
                val allKps = keypoints.toArray()
                val centerKpsIdx = mutableListOf<Int>()
                val lockZone = Rect(imgW/2 - 120, imgH/2 - 120, 240, 240)
                
                for (i in allKps.indices) {
                    if (lockZone.contains(allKps[i].pt)) centerKpsIdx.add(i)
                }

                if (centerKpsIdx.size > 15) {
                    val filteredDescriptors = Mat(centerKpsIdx.size, descriptors.cols(), descriptors.type())
                    val filteredKpsList = mutableListOf<KeyPoint>()
                    for (i in centerKpsIdx.indices) {
                        descriptors.row(centerKpsIdx[i]).copyTo(filteredDescriptors.row(i))
                        filteredKpsList.add(allKps[centerKpsIdx[i]])
                    }
                    refDescriptors?.release()
                    refDescriptors = filteredDescriptors
                    refObjectCenter = calculateMean(filteredKpsList)
                    isLocked = true
                }
                shouldCaptureNextFrame = false
            }

            if (!isLocked || refDescriptors == null || descriptors.empty()) {
                releaseAll(mat, keypoints, descriptors)
                return TrackingResult(detected = isLocked, isObjectInZoom = isObjectInZoomArea, objectName = currentObjectName)
            }

            val knnMatches = mutableListOf<MatOfDMatch>()
            matcher.knnMatch(refDescriptors, descriptors, knnMatches, 2)
            val goodMatches = mutableListOf<DMatch>()
            for (match in knnMatches) {
                val arr = match.toArray()
                if (arr.size >= 2 && arr[0].distance < 0.7f * arr[1].distance) goodMatches.add(arr[0])
                match.release()
            }

            val result = if (goodMatches.size >= 8) {
                val currentKps = keypoints.toArray()
                val matchedKps = goodMatches.map { currentKps[it.trainIdx] }
                val currentCenter = calculateMean(matchedKps)
                
                // حساب الحدود النسبية لنقطة القفل
                var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
                var maxX = Double.MIN_VALUE; var maxY = Double.MIN_VALUE
                matchedKps.forEach {
                    val relX = it.pt.x - refObjectCenter!!.x
                    val relY = it.pt.y - refObjectCenter!!.y
                    if (relX < minX) minX = relX
                    if (relY < minY) minY = relY
                    if (relX > maxX) maxX = relX
                    if (relY > maxY) maxY = relY
                }

                val rawDx = (currentCenter.x - refObjectCenter!!.x).toFloat()
                val rawDy = (currentCenter.y - refObjectCenter!!.y).toFloat()
                
                smoothedDx += (rawDx - smoothedDx) * smoothingFactor
                smoothedDy += (rawDy - smoothedDy) * smoothingFactor

                // الحدود الآن تعتمد على التنعيم أيضاً لتكون سلسة
                val boundaryList = listOf(
                    Offset(minX.toFloat() + (smoothedDx - rawDx), minY.toFloat() + (smoothedDy - rawDy)),
                    Offset(maxX.toFloat() + (smoothedDx - rawDx), minY.toFloat() + (smoothedDy - rawDy)),
                    Offset(maxX.toFloat() + (smoothedDx - rawDx), maxY.toFloat() + (smoothedDy - rawDy)),
                    Offset(minX.toFloat() + (smoothedDx - rawDx), maxY.toFloat() + (smoothedDy - rawDy))
                )
                
                TrackingResult(
                    detected = true,
                    isObjectInZoom = true,
                    dx = smoothedDx,
                    dy = smoothedDy,
                    objectName = currentObjectName,
                    boundaries = boundaryList
                )
            } else {
                TrackingResult(
                    detected = false,
                    isObjectInZoom = false,
                    dx = 0f,
                    dy = 0f,
                    objectName = currentObjectName
                )
            }

            releaseAll(mat, keypoints, descriptors)
            return result

        } catch (_: Exception) {
            releaseAll(mat, keypoints, descriptors)
            return TrackingResult(detected = false)
        }
    }

    private fun translate(name: String) = when(name.lowercase()) {
        "mouse" -> "ماوس ذكي"
        "mobile phone" -> "هاتف محمول"
        "cup" -> "كوب"
        "person" -> "شخص"
        else -> name
    }

    private fun calculateMean(kps: List<KeyPoint>): Point {
        if (kps.isEmpty()) return Point(0.0, 0.0)
        var x = 0.0; var y = 0.0
        kps.forEach { x += it.pt.x; y += it.pt.y }
        return Point(x / kps.size, y / kps.size)
    }

    private fun releaseAll(vararg mats: Mat) {
        for (mat in mats) { if (!mat.empty()) mat.release() }
    }
}