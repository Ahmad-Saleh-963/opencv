package com.example.opencv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opencv.vision.TrackingResult
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun TrackingOverlay(result: TrackingResult) {
    val isLocked = result.detected
    val isInZoom = result.isObjectInZoom
    val deviation = sqrt((result.dx * result.dx + result.dy * result.dy).toDouble())
    val isPerfect = isLocked && deviation < 15

    val statusColor by animateColorAsState(
        targetValue = when {
            isPerfect -> Color(0xFF00FF00)
            isLocked -> Color(0xFFFFD600)
            isInZoom -> Color(0xFF00E5FF)
            else -> Color.White.copy(alpha = 0.3f)
        }, label = "statusColor"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. شبكة التقاطع المركزية مع نقطة المنتصف
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // الخطوط المتقاطعة
            val crosshairAlpha = if (isPerfect) 0.6f else 0.2f
            drawLine(statusColor.copy(alpha = crosshairAlpha), Offset(centerX, 0f), Offset(centerX, size.height), 1.dp.toPx())
            drawLine(statusColor.copy(alpha = crosshairAlpha), Offset(0f, centerY), Offset(size.width, centerY), 1.dp.toPx())
            
            // نقطة المنتصف (الدائرة الصغيرة)
            drawCircle(
                color = statusColor.copy(alpha = 0.8f),
                radius = 4.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }

        // 2. إطار الزوم المركزي (ثابت)
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.Center)
                .border(2.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            val cornerSize = 15.dp
            Box(modifier = Modifier.size(cornerSize).align(Alignment.TopStart).border(3.dp, statusColor, RoundedCornerShape(topStart = 4.dp)))
            Box(modifier = Modifier.size(cornerSize).align(Alignment.TopEnd).border(3.dp, statusColor, RoundedCornerShape(topEnd = 4.dp)))
            Box(modifier = Modifier.size(cornerSize).align(Alignment.BottomStart).border(3.dp, statusColor, RoundedCornerShape(bottomStart = 4.dp)))
            Box(modifier = Modifier.size(cornerSize).align(Alignment.BottomEnd).border(3.dp, statusColor, RoundedCornerShape(bottomEnd = 4.dp)))
        }

        // 3. لوحات المعلومات من كل الجهات (HUD Style)

        // اللوحة العلوية: الحالة واسم الجسم
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp)
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isPerfect) "🎯 تطابق مثالي (100%)" else if (isLocked) "📡 جاري التتبع..." else "🔍 نظام المسح نشط",
                    color = statusColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isLocked) {
                    Text(text = "الجسم: ${result.objectName}", color = Color.Cyan, fontSize = 14.sp)
                }
            }
        }

        // اللوحة اليمنى: الإزاحة الأفقية
        if (isLocked) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("أفقي (X)", color = Color.Gray, fontSize = 10.sp)
                    Text("${result.dx.toInt()}", color = Color.Yellow, fontWeight = FontWeight.Bold)
                }
            }

            // اللوحة اليسرى: الإزاحة الرأسية
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("رأسي (Y)", color = Color.Gray, fontSize = 10.sp)
                    Text("${result.dy.toInt()}", color = Color.Yellow, fontWeight = FontWeight.Bold)
                }
            }

            // اللوحة السفلية: اتجاه التصحيح
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 120.dp) // فوق الأزرار
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نظام التصحيح: ${getDirectionAr(result.dx, result.dy)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

fun getDirectionAr(dx: Float, dy: Float): String {
    val h = if (abs(dx) < 10) "" else if (dx > 0) "يسار" else "يمين"
    val v = if (abs(dy) < 10) "" else if (dy > 0) "أعلى" else "أسفل"
    return if (h == "" && v == "") "مطابق تماماً" else "$v $h".trim()
}