package ph.appbuilders.saklolo.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

fun qrBitmap(contents: String, size: Int = 640): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
