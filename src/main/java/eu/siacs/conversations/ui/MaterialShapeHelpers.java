package eu.siacs.conversations.ui;

import androidx.compose.material3.MaterialShapes;
import androidx.graphics.shapes.RoundedPolygon;

/**
 * Accesses MaterialShapes properties from Java to avoid Kotlin naming conflicts with internal
 * functions.
 */
public final class MaterialShapeHelpers {
    private MaterialShapeHelpers() {}

    public static RoundedPolygon circle() {
        return MaterialShapes.Companion.getCircle();
    }

    public static RoundedPolygon pill() {
        return MaterialShapes.Companion.getPill();
    }

    public static RoundedPolygon semiCircle() {
        return MaterialShapes.Companion.getSemiCircle();
    }

    public static RoundedPolygon diamond() {
        return MaterialShapes.Companion.getDiamond();
    }

    public static RoundedPolygon gem() {
        return MaterialShapes.Companion.getGem();
    }

    public static RoundedPolygon ghostish() {
        return MaterialShapes.Companion.getGhostish();
    }

    public static RoundedPolygon softBurst() {
        return MaterialShapes.Companion.getSoftBurst();
    }

    public static RoundedPolygon slanted() {
        return MaterialShapes.Companion.getSlanted();
    }

    public static RoundedPolygon arrow() {
        return MaterialShapes.Companion.getArrow();
    }
}
