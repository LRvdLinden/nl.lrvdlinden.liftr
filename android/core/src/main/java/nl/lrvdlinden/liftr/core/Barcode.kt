package nl.lrvdlinden.liftr.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Barcode types a gym pass can have (same set as Shared/Barcode/BarcodeFormat.swift). */
enum class BarcodeFormat(val raw: String, val displayName: String) {
    EAN13("ean13", "EAN-13"),
    EAN8("ean8", "EAN-8"),
    UPCA("upcA", "UPC-A"),
    CODE128("code128", "Code 128"),
    CODE39("code39", "Code 39"),
    ITF("itf", "ITF"),
    QR("qr", "QR"),
    PDF417("pdf417", "PDF417"),
    AZTEC("aztec", "Aztec");

    val isLinear: Boolean get() = this != QR && this != PDF417 && this != AZTEC

    val zxing: com.google.zxing.BarcodeFormat
        get() = when (this) {
            EAN13 -> com.google.zxing.BarcodeFormat.EAN_13
            EAN8 -> com.google.zxing.BarcodeFormat.EAN_8
            UPCA -> com.google.zxing.BarcodeFormat.UPC_A
            CODE128 -> com.google.zxing.BarcodeFormat.CODE_128
            CODE39 -> com.google.zxing.BarcodeFormat.CODE_39
            ITF -> com.google.zxing.BarcodeFormat.ITF
            QR -> com.google.zxing.BarcodeFormat.QR_CODE
            PDF417 -> com.google.zxing.BarcodeFormat.PDF_417
            AZTEC -> com.google.zxing.BarcodeFormat.AZTEC
        }

    companion object {
        fun from(raw: String?): BarcodeFormat = entries.firstOrNull { it.raw == raw } ?: QR

        /** GS1 check digit for the given digits (without the check digit). */
        fun gs1CheckDigit(digits: String): Int {
            var sum = 0
            digits.reversed().forEachIndexed { i, c -> sum += (c - '0') * (if (i % 2 == 0) 3 else 1) }
            return (10 - sum % 10) % 10
        }

        /** Best format for a number typed by hand. */
        fun guess(value: String): BarcodeFormat {
            if (value.isNotEmpty() && value.all { it in '0'..'9' }) {
                if (value.length == 13 && gs1CheckDigit(value.take(12)) == value.last() - '0') return EAN13
                if (value.length == 8 && gs1CheckDigit(value.take(7)) == value.last() - '0') return EAN8
            }
            return CODE128
        }
    }
}

/** Draws a pass's code with ZXing: black modules on white, at the given size (2D codes stay square). */
object BarcodeRenderer {
    fun render(value: String, format: BarcodeFormat, width: Int, height: Int): Bitmap? {
        if (value.isBlank()) return null
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = if (format.isLinear) 10 else 1
            if (format == BarcodeFormat.QR) hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            val w = if (format.isLinear) width else minOf(width, height)
            val h = if (format.isLinear) height else if (format == BarcodeFormat.PDF417) (w / 3) else w
            val matrix = MultiFormatWriter().encode(value, format.zxing, w, h, hints)
            val bw = matrix.width
            val bh = matrix.height
            val pixels = IntArray(bw * bh)
            for (y in 0 until bh) {
                val offset = y * bw
                for (x in 0 until bw) pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }
}
