package eu.siacs.conversations.utils

import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import com.google.common.hash.Hashing
import org.hsluv.HsluvColorConverter
import java.nio.charset.StandardCharsets

object XEP0392Helper {

    private fun angle(nickname: String): Double {
        return try {
            val digest = Hashing.sha1().hashString(nickname, StandardCharsets.UTF_8).asBytes()
            val angle = digest[0].toInt().and(0xFF) + digest[1].toInt().and(0xFF) * 256
            angle / 65536.0 * 360
        } catch (e: Exception) {
            0.0
        }
    }

    @JvmStatic
    @ColorInt
    fun rgbFromNick(name: String): Int = rgbFromAngle(angle(name))

    @JvmStatic
    @ColorInt
    fun rgbFromAngle(angle: Double): Int {
        val converter = HsluvColorConverter()
        converter.hsluv_h = angle
        converter.hsluv_s = 100.0
        converter.hsluv_l = 50.0
        converter.hsluvToRgb()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Color.rgb(converter.rgb_r.toFloat(), converter.rgb_g.toFloat(), converter.rgb_b.toFloat())
        } else {
            Color.rgb(
                (converter.rgb_r * 255 + 0.5f).toInt(),
                (converter.rgb_g * 255 + 0.5f).toInt(),
                (converter.rgb_b * 255 + 0.5f).toInt()
            )
        }
    }
}
