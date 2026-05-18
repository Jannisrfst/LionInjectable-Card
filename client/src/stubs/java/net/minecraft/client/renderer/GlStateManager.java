package net.minecraft.client.renderer;

public class GlStateManager {
    public static void disableTexture2D() {}
    public static void enableTexture2D () {}
    public static void enableBlend     () {}
    public static void disableBlend    () {}
    public static void tryBlendFuncSeparate(int a, int b, int c, int d) {}
    public static void disableDepth   () {}
    public static void enableDepth    () {}
    public static void depthMask      (boolean on) {}
    public static void disableLighting() {}
    public static void enableLighting () {}
    public static void disableCull    () {}
    public static void enableCull     () {}
    public static void color(float r, float g, float b, float a) {}
}
