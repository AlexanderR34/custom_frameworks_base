package com.android.systemui.wallpapers;

/**
 * Obsolete renderer placeholder. Rendering is now handled directly via JellyMesh on Canvas.
 */
public class JellyWallpaperRenderer {
    public JellyWallpaperRenderer() {}
    public void onSurfaceCreated() {}
    public void onSurfaceChanged(int width, int height) {}
    public boolean renderFrame(float dt) { return false; }
    public void onTouchEvent(int action, float x, float y) {}
    public void triggerRipple(float originX, float originY, float strength) {}
    public void setPhysicsParams(float stiffness, float damping, float touchRadius) {}
    public void release() {}
}
