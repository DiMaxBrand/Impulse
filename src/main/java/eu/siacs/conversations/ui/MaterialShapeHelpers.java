package eu.siacs.conversations.ui;

import androidx.compose.material3.MaterialShapes;
import androidx.graphics.shapes.RoundedPolygon;

/**
 * Accesses MaterialShapes properties from Java to avoid Kotlin naming conflicts with internal
 * functions. Exposes the full set (35 shapes) MaterialShapes.Companion offers.
 */
public final class MaterialShapeHelpers {
    private MaterialShapeHelpers() {}

    public static RoundedPolygon circle() {
        return MaterialShapes.Companion.getCircle();
    }

    public static RoundedPolygon square() {
        return MaterialShapes.Companion.getSquare();
    }

    public static RoundedPolygon slanted() {
        return MaterialShapes.Companion.getSlanted();
    }

    public static RoundedPolygon arch() {
        return MaterialShapes.Companion.getArch();
    }

    public static RoundedPolygon fan() {
        return MaterialShapes.Companion.getFan();
    }

    public static RoundedPolygon arrow() {
        return MaterialShapes.Companion.getArrow();
    }

    public static RoundedPolygon semiCircle() {
        return MaterialShapes.Companion.getSemiCircle();
    }

    public static RoundedPolygon oval() {
        return MaterialShapes.Companion.getOval();
    }

    public static RoundedPolygon pill() {
        return MaterialShapes.Companion.getPill();
    }

    public static RoundedPolygon triangle() {
        return MaterialShapes.Companion.getTriangle();
    }

    public static RoundedPolygon diamond() {
        return MaterialShapes.Companion.getDiamond();
    }

    public static RoundedPolygon clamShell() {
        return MaterialShapes.Companion.getClamShell();
    }

    public static RoundedPolygon pentagon() {
        return MaterialShapes.Companion.getPentagon();
    }

    public static RoundedPolygon gem() {
        return MaterialShapes.Companion.getGem();
    }

    public static RoundedPolygon sunny() {
        return MaterialShapes.Companion.getSunny();
    }

    public static RoundedPolygon verySunny() {
        return MaterialShapes.Companion.getVerySunny();
    }

    public static RoundedPolygon cookie4Sided() {
        return MaterialShapes.Companion.getCookie4Sided();
    }

    public static RoundedPolygon cookie6Sided() {
        return MaterialShapes.Companion.getCookie6Sided();
    }

    public static RoundedPolygon cookie7Sided() {
        return MaterialShapes.Companion.getCookie7Sided();
    }

    public static RoundedPolygon cookie9Sided() {
        return MaterialShapes.Companion.getCookie9Sided();
    }

    public static RoundedPolygon cookie12Sided() {
        return MaterialShapes.Companion.getCookie12Sided();
    }

    public static RoundedPolygon ghostish() {
        return MaterialShapes.Companion.getGhostish();
    }

    public static RoundedPolygon clover4Leaf() {
        return MaterialShapes.Companion.getClover4Leaf();
    }

    public static RoundedPolygon clover8Leaf() {
        return MaterialShapes.Companion.getClover8Leaf();
    }

    public static RoundedPolygon burst() {
        return MaterialShapes.Companion.getBurst();
    }

    public static RoundedPolygon softBurst() {
        return MaterialShapes.Companion.getSoftBurst();
    }

    public static RoundedPolygon boom() {
        return MaterialShapes.Companion.getBoom();
    }

    public static RoundedPolygon softBoom() {
        return MaterialShapes.Companion.getSoftBoom();
    }

    public static RoundedPolygon flower() {
        return MaterialShapes.Companion.getFlower();
    }

    public static RoundedPolygon puffy() {
        return MaterialShapes.Companion.getPuffy();
    }

    public static RoundedPolygon puffyDiamond() {
        return MaterialShapes.Companion.getPuffyDiamond();
    }

    public static RoundedPolygon pixelCircle() {
        return MaterialShapes.Companion.getPixelCircle();
    }

    public static RoundedPolygon pixelTriangle() {
        return MaterialShapes.Companion.getPixelTriangle();
    }

    public static RoundedPolygon bun() {
        return MaterialShapes.Companion.getBun();
    }

    public static RoundedPolygon heart() {
        return MaterialShapes.Companion.getHeart();
    }
}
