package io.github.kiryxaurnsadik.mineparty.client.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.world.phys.Vec3;

/**
 * Remembers the camera matrices of the last rendered frame, so the HUD can turn a world
 * position into a screen position. Markers are drawn as 2D on the HUD: they work at any
 * distance and through walls, and do not depend on the world renderer of a given version.
 */
public final class Projection {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean ready;

    private Projection() {
    }

    public static void capture(Matrix4f view, Matrix4f projection, Vec3 cameraPos) {
        VIEW.set(view);
        PROJ.set(projection);
        camera = cameraPos;
        ready = true;
    }

    public static Vec3 camera() {
        return camera;
    }

    /** @return {x, y} in GUI pixels, or null if behind the camera */
    public static float[] toScreen(Vec3 world, int guiWidth, int guiHeight) {
        if (!ready) {
            return null;
        }
        Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1f);
        VIEW.transform(v);
        PROJ.transform(v);
        if (v.w <= 0.05f) {
            return null;
        }
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        return new float[] {(nx * 0.5f + 0.5f) * guiWidth, (1f - (ny * 0.5f + 0.5f)) * guiHeight};
    }
}
