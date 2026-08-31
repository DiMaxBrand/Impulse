package eu.siacs.conversations.ui

import androidx.graphics.shapes.RoundedPolygon

/** Full MaterialShapes set (35 shapes) — the complete catalog, not just the handful the chat
 * list's presence-avatar happened to need. Shared between the Developer Options shape catalog
 * and any decorative use of the same shapes (e.g. the notification setup screen). */
val MATERIAL_SHAPE_CATALOG: List<Pair<String, RoundedPolygon>> by lazy {
    listOf(
        "Circle" to MaterialShapeHelpers.circle(),
        "Square" to MaterialShapeHelpers.square(),
        "Slanted" to MaterialShapeHelpers.slanted(),
        "Arch" to MaterialShapeHelpers.arch(),
        "Fan" to MaterialShapeHelpers.fan(),
        "Arrow" to MaterialShapeHelpers.arrow(),
        "Semi-circle" to MaterialShapeHelpers.semiCircle(),
        "Oval" to MaterialShapeHelpers.oval(),
        "Pill" to MaterialShapeHelpers.pill(),
        "Triangle" to MaterialShapeHelpers.triangle(),
        "Diamond" to MaterialShapeHelpers.diamond(),
        "Clam shell" to MaterialShapeHelpers.clamShell(),
        "Pentagon" to MaterialShapeHelpers.pentagon(),
        "Gem" to MaterialShapeHelpers.gem(),
        "Sunny" to MaterialShapeHelpers.sunny(),
        "Very sunny" to MaterialShapeHelpers.verySunny(),
        "Cookie 4" to MaterialShapeHelpers.cookie4Sided(),
        "Cookie 6" to MaterialShapeHelpers.cookie6Sided(),
        "Cookie 7" to MaterialShapeHelpers.cookie7Sided(),
        "Cookie 9" to MaterialShapeHelpers.cookie9Sided(),
        "Cookie 12" to MaterialShapeHelpers.cookie12Sided(),
        "Ghostish" to MaterialShapeHelpers.ghostish(),
        "Clover 4-leaf" to MaterialShapeHelpers.clover4Leaf(),
        "Clover 8-leaf" to MaterialShapeHelpers.clover8Leaf(),
        "Burst" to MaterialShapeHelpers.burst(),
        "Soft burst" to MaterialShapeHelpers.softBurst(),
        "Boom" to MaterialShapeHelpers.boom(),
        "Soft boom" to MaterialShapeHelpers.softBoom(),
        "Flower" to MaterialShapeHelpers.flower(),
        "Puffy" to MaterialShapeHelpers.puffy(),
        "Puffy diamond" to MaterialShapeHelpers.puffyDiamond(),
        "Pixel circle" to MaterialShapeHelpers.pixelCircle(),
        "Pixel triangle" to MaterialShapeHelpers.pixelTriangle(),
        "Bun" to MaterialShapeHelpers.bun(),
        "Heart" to MaterialShapeHelpers.heart(),
    )
}
