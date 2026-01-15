package com.example.opencv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opencv.camera.CameraPreview
import com.example.opencv.vision.ObjectTracker
import com.example.opencv.vision.TrackingResult
import kotlin.math.abs

@Composable
fun TrackingScreen() {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    var result by remember { mutableStateOf(TrackingResult()) }
    val tracker = remember { ObjectTracker() }

    // تشغيل الاهتزاز عند التطابق التام
    LaunchedEffect(result) {
        if (result.detected && abs(result.dx) < 15 && abs(result.dy) < 15) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            tracker = tracker,
            lifecycleOwner = lifecycleOwner,
            onResult = { result = it }
        )

        TrackingOverlay(result)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { tracker.requestLock() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                modifier = Modifier.height(50.dp)
            ) {
                Text("🎯 قفل الهدف", fontSize = 16.sp)
            }

            Button(
                onClick = { tracker.reset() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                modifier = Modifier.height(50.dp)
            ) {
                Text("🔄 إعادة ضبط", fontSize = 16.sp)
            }
        }
    }
}