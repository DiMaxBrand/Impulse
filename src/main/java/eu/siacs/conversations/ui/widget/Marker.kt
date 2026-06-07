package eu.siacs.conversations.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay

/**
 * An immutable marker overlay.
 */
class Marker : SimpleLocationOverlay {

    private val position: GeoPoint?
    private val icon: Bitmap
    private val mapPoint: Point = Point()

    /**
     * Create a marker overlay which will be drawn at the current Geographical position.
     * @param icon A bitmap icon for the marker
     * @param position The geographic position where the marker will be drawn (if it is inside the view)
     */
    constructor(icon: Bitmap, position: GeoPoint?) : super(icon) {
        this.icon = icon
        this.position = position
    }

    /**
     * Create a marker overlay which will be drawn centered in the view.
     * @param icon A bitmap icon for the marker
     */
    constructor(icon: Bitmap) : this(icon, null)

    override fun draw(c: Canvas, view: MapView, shadow: Boolean) {
        super.draw(c, view, shadow)

        // If no position was set for the marker, draw it centered in the view.
        view.projection.toPixels(if (this.position == null) view.mapCenter else position, mapPoint)

        c.drawBitmap(
            icon,
            (mapPoint.x - icon.width / 2).toFloat(),
            (mapPoint.y - icon.height).toFloat(),
            null
        )
    }
}
