package eu.siacs.conversations.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.location.Location
import androidx.core.content.ContextCompat
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay

class MyLocation(ctx: Context, icon: Bitmap, position: Location) : SimpleLocationOverlay(icon) {

    private val position: GeoPoint
    private val accuracy: Float
    private val mapCenterPoint: Point = Point()
    private val fill: Paint
    private val outline: Paint

    init {
        this.fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val accent = ContextCompat.getColor(ctx, R.color.blue500)
        fill.color = accent
        fill.style = Paint.Style.FILL
        this.outline = Paint(Paint.ANTI_ALIAS_FLAG)
        outline.color = accent
        outline.alpha = 50
        outline.style = Paint.Style.FILL
        this.position = GeoPoint(position)
        this.accuracy = position.accuracy
    }

    override fun draw(c: Canvas, view: MapView, shadow: Boolean) {
        super.draw(c, view, shadow)

        view.projection.toPixels(position, mapCenterPoint)
        c.drawCircle(
            mapCenterPoint.x.toFloat(),
            mapCenterPoint.y.toFloat(),
            maxOf(
                (Config.Map.MY_LOCATION_INDICATOR_SIZE + Config.Map.MY_LOCATION_INDICATOR_OUTLINE_SIZE).toFloat(),
                accuracy / TileSystem.GroundResolution(position.latitude, view.zoomLevel).toFloat()
            ),
            this.outline
        )
        c.drawCircle(
            mapCenterPoint.x.toFloat(),
            mapCenterPoint.y.toFloat(),
            Config.Map.MY_LOCATION_INDICATOR_SIZE.toFloat(),
            this.fill
        )
    }
}
