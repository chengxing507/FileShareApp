package com.example.fileshare.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR 码显示组件
 * 使用 ZXing 库将 URL 字符串编码为 QR 码并渲染
 *
 * @param url 要编码的 URL
 * @param modifier Modifier
 * @param size 二维码像素宽高
 */
@Composable
fun QrCodeView(
    url: String,
    modifier: Modifier = Modifier,
    size: Int = 256
) {
    val bitmap = remember(url) {
        generateQrCodeBitmap(url, size)
    }

    if (bitmap != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "二维码 $url",
                modifier = Modifier.size((size * 0.9f).dp)
            )
        }
    }
}

/**
 * 使用 ZXing 生成 QR 码 Bitmap
 */
private fun generateQrCodeBitmap(url: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) Color.Black.hashCode() else Color.White.hashCode()
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}