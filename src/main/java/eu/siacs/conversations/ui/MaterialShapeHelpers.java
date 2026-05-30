package eu.siacs.conversations.ui;

import androidx.compose.material3.MaterialShapes;
import androidx.graphics.shapes.RoundedPolygon;

/** Accesses MaterialShapes properties from Java to avoid Kotlin naming conflicts with internal functions. */
final class MaterialShapeHelpers {
    private MaterialShapeHelpers() {}

    static RoundedPolygon circle()     { return MaterialShapes.Companion.getCircle(); }
    static RoundedPolygon pill()       { return MaterialShapes.Companion.getPill(); }
    static RoundedPolygon semiCircle() { return MaterialShapes.Companion.getSemiCircle(); }
    static RoundedPolygon diamond()    { return MaterialShapes.Companion.getDiamond(); }
    static RoundedPolygon gem()        { return MaterialShapes.Companion.getGem(); }
    static RoundedPolygon ghostish()   { return MaterialShapes.Companion.getGhostish(); }
    static RoundedPolygon softBurst()  { return MaterialShapes.Companion.getSoftBurst(); }
}
