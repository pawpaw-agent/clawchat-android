package com.openclaw.clawchat.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码工具类
 * 使用 ZXing 库生成二维码
 */
object QRCodeUtils {
    
    /**
     * 生成二维码 Bitmap
     * 
     * @param content 二维码内容
     * @param size 二维码尺寸（像素）
     * @return Bitmap 或 null（生成失败）
     */
    fun generateQRCode(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                }
            }
            
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            AppLog.e("QRCodeUtils", "Failed to generate QR code: ${e.message}")
            null
        }
    }
    
    /**
     * 生成分享会话二维码
     * 
     * @param sessionKey 会话 ID
     * @param sessionName 会话名称
     * @return 二维码 Bitmap
     */
    fun generateSessionShareQR(sessionKey: String, sessionName: String): Bitmap? {
        val content = """{"type":"clawchat_session","id":"$sessionKey","name":"$sessionName"}"""
        return generateQRCode(content)
    }
}