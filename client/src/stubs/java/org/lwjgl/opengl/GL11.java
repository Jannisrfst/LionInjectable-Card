package org.lwjgl.opengl;

public class GL11 {
    public static final int GL_LINES                = 0x0001;
    public static final int GL_LINE_LOOP            = 0x0002;
    public static final int GL_QUADS                = 0x0007;
    public static final int GL_CULL_FACE            = 0x0B44;
    public static final int GL_LIGHTING             = 0x0B50;
    public static final int GL_DEPTH_TEST           = 0x0B71;
    public static final int GL_BLEND                = 0x0BE2;
    public static final int GL_TEXTURE_2D           = 0x0DE1;
    public static final int GL_SRC_ALPHA            = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA  = 0x0303;

    public static void glPushMatrix() {}
    public static void glPopMatrix () {}
    public static void glBegin     (int mode) {}
    public static void glEnd       () {}
    public static void glEnable    (int cap)  {}
    public static void glDisable   (int cap)  {}
    public static void glBlendFunc (int src, int dst) {}
    public static void glDepthMask (boolean enable) {}
    public static void glLineWidth (float w) {}
    public static void glColor4f   (float r, float g, float b, float a) {}
    public static void glVertex2d  (double x, double y) {}
    public static void glVertex3d  (double x, double y, double z) {}
}
