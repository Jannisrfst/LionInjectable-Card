package com.lionclient.gui.card;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class GuiGfx {
    private static final int CORNER_SEGMENTS = 8;

    private GuiGfx() {
    }

    public static void roundedRect(float l, float t, float r, float b, float radius, int argb) {
        if (((argb >>> 24) & 255) == 0 || r <= l || b <= t) {
            return;
        }

        float rad = clampRadius(l, t, r, b, radius);
        beginShape();
        setColor(argb);
        fillRounded(l, t, r, b, rad);
        endShape();
    }

    public static void roundedRectGradient(float l, float t, float r, float b, float radius, int argbTop, int argbBottom) {
        if (r <= l || b <= t) {
            return;
        }

        float rad = clampRadius(l, t, r, b, radius);
        float mid = t + ((b - t) / 2.0F);

        beginShape();
        setColor(argbTop);
        fillRounded(l, t, r, mid, rad);
        setColor(argbBottom);
        fillRounded(l, mid, r, b, rad);
        endShape();
    }

    public static void roundedOutline(float l, float t, float r, float b, float radius, float thickness, int argb) {
        if (((argb >>> 24) & 255) == 0 || r <= l || b <= t || thickness <= 0.0F) {
            return;
        }

        float rad = clampRadius(l, t, r, b, radius);
        float half = thickness / 2.0F;

        beginShape();
        setColor(argb);
        drawRing(l, t, r, b, rad, half);
        endShape();
    }

    public static void pill(float l, float t, float r, float b, int argb) {
        roundedRect(l, t, r, b, (b - t) / 2.0F, argb);
    }

    public static void shadow(float l, float t, float r, float b, float radius, int argb) {
        int baseAlpha = (argb >>> 24) & 255;
        if (baseAlpha == 0 || r <= l || b <= t) {
            return;
        }

        int layers = 4;
        float spread = Math.max(radius, 6.0F);
        for (int i = layers; i >= 1; i--) {
            float grow = spread * (i / (float) layers);
            float alpha = baseAlpha * (1.0F - (i / (float) (layers + 1)));
            int layerColor = withAlpha(argb, Math.round(alpha));
            roundedRect(l - grow, t - grow, r + grow, b + grow, radius + grow, layerColor);
        }
    }

    public static void toggleSwitch(Bounds track, float progress, int accent, float alpha) {
        float p = clamp(progress, 0.0F, 1.0F);
        int trackColor = scaleAlpha(mix(CardTheme.TRACK_OFF, accent, p), alpha);
        pill(track.left, track.top, track.right, track.bottom, trackColor);

        float h = track.height();
        float thumbPad = Math.max(1.5F, h * 0.12F);
        float thumbSize = h - (thumbPad * 2.0F);
        float travel = track.width() - thumbSize - (thumbPad * 2.0F);
        float thumbLeft = track.left + thumbPad + (travel * p);
        float thumbTop = track.top + thumbPad;
        int thumbColor = scaleAlpha(mix(CardTheme.THUMB_OFF, 0xFFFFFFFF, p), alpha);
        roundedRect(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize, thumbSize / 2.0F, thumbColor);
    }

    public static void badge(Bounds b, String text, int fill, int textColor, float alpha, FontRenderer fr) {
        pill(b.left, b.top, b.right, b.bottom, scaleAlpha(fill, alpha));
        if (text == null || text.length() == 0) {
            return;
        }

        int textWidth = fr.getStringWidth(text);
        float x = b.left + ((b.width() - textWidth) / 2.0F);
        float y = b.top + ((b.height() - fr.FONT_HEIGHT) / 2.0F);
        fr.drawString(text, Math.round(x), Math.round(y), scaleAlpha(textColor, alpha));
    }

    public static void beginScissor(Bounds b, Minecraft mc) {
        beginScissor(b, mc, 1.0F);
    }

    /**
     * Clips to {@code b}. {@code extraScale} must match any additional matrix scale currently
     * applied on top of the vanilla GUI scale (e.g. the card GUI's own zoom), otherwise the
     * scissor box lands at the wrong position and content bleeds outside the viewport.
     */
    public static void beginScissor(Bounds b, Minecraft mc, float extraScale) {
        ScaledResolution resolution = new ScaledResolution(mc);
        float factor = resolution.getScaleFactor() * extraScale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int left = Math.round(b.left * factor);
        int bottom = Math.round(b.bottom * factor);
        int width = Math.max(0, Math.round(b.width() * factor));
        int height = Math.max(0, Math.round(b.height() * factor));
        GL11.glScissor(left, mc.displayHeight - bottom, width, height);
    }

    public static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void textScaled(FontRenderer fr, String s, float x, float y, int color, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        fr.drawString(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    public static int withAlpha(int rgb, int a) {
        return ((a & 255) << 24) | (rgb & 0x00FFFFFF);
    }

    public static int scaleAlpha(int argb, float s) {
        int baseAlpha = (argb >>> 24) & 255;
        if (baseAlpha == 0) {
            baseAlpha = 255;
        }
        return withAlpha(argb, Math.round(baseAlpha * clamp(s, 0.0F, 1.0F)));
    }

    public static int mix(int a, int b, float t) {
        float amount = clamp(t, 0.0F, 1.0F);
        int aA = (a >>> 24) & 255;
        int aR = (a >>> 16) & 255;
        int aG = (a >>> 8) & 255;
        int aB = a & 255;
        int bA = (b >>> 24) & 255;
        int bR = (b >>> 16) & 255;
        int bG = (b >>> 8) & 255;
        int bB = b & 255;
        int outA = Math.round(aA + ((bA - aA) * amount));
        int outR = Math.round(aR + ((bR - aR) * amount));
        int outG = Math.round(aG + ((bG - aG) * amount));
        int outB = Math.round(aB + ((bB - aB) * amount));
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float easeOut(float v) {
        float inverse = 1.0F - clamp(v, 0.0F, 1.0F);
        return 1.0F - (inverse * inverse * inverse);
    }

    public static float animate(float cur, float target, float speed) {
        return cur + ((target - cur) * clamp(speed, 0.0F, 1.0F));
    }

    private static float clampRadius(float l, float t, float r, float b, float radius) {
        float maxRadius = Math.min(r - l, b - t) / 2.0F;
        return clamp(radius, 0.0F, Math.max(0.0F, maxRadius));
    }

    private static void beginShape() {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endShape() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void setColor(int argb) {
        float a = ((argb >>> 24) & 255) / 255.0F;
        float r = ((argb >>> 16) & 255) / 255.0F;
        float g = ((argb >>> 8) & 255) / 255.0F;
        float bl = (argb & 255) / 255.0F;
        GlStateManager.color(r, g, bl, a);
    }

    // Fills a rounded rect: center quad + 4 edge quads + 4 corner triangle fans, current color.
    private static void fillRounded(float l, float t, float r, float b, float radius) {
        if (radius <= 0.05F) {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(l, b);
            GL11.glVertex2f(r, b);
            GL11.glVertex2f(r, t);
            GL11.glVertex2f(l, t);
            GL11.glEnd();
            return;
        }

        float innerTop = t + radius;
        float innerBottom = b - radius;
        float innerLeft = l + radius;
        float innerRight = r - radius;

        GL11.glBegin(GL11.GL_QUADS);
        // center column
        GL11.glVertex2f(innerLeft, b);
        GL11.glVertex2f(innerRight, b);
        GL11.glVertex2f(innerRight, t);
        GL11.glVertex2f(innerLeft, t);
        // left edge
        GL11.glVertex2f(l, innerBottom);
        GL11.glVertex2f(innerLeft, innerBottom);
        GL11.glVertex2f(innerLeft, innerTop);
        GL11.glVertex2f(l, innerTop);
        // right edge
        GL11.glVertex2f(innerRight, innerBottom);
        GL11.glVertex2f(r, innerBottom);
        GL11.glVertex2f(r, innerTop);
        GL11.glVertex2f(innerRight, innerTop);
        GL11.glEnd();

        drawCornerFan(innerLeft, innerTop, radius, 90.0F, 180.0F);
        drawCornerFan(innerRight, innerTop, radius, 0.0F, 90.0F);
        drawCornerFan(innerLeft, innerBottom, radius, 180.0F, 270.0F);
        drawCornerFan(innerRight, innerBottom, radius, 270.0F, 360.0F);
    }

    private static void drawCornerFan(float cx, float cy, float radius, float startDeg, float endDeg) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            float deg = startDeg + ((endDeg - startDeg) * (i / (float) CORNER_SEGMENTS));
            float rad = (float) Math.toRadians(deg);
            float x = cx + (radius * (float) Math.cos(rad));
            float y = cy - (radius * (float) Math.sin(rad));
            GL11.glVertex2f(x, y);
        }
        GL11.glEnd();
    }

    // Rounded-rect ring (outline) of given half-thickness, built from straight ring segments
    // (top/bottom/left/right bars) plus 4 corner arc bands drawn as triangle strips.
    private static void drawRing(float l, float t, float r, float b, float radius, float half) {
        float innerTop = t + radius;
        float innerBottom = b - radius;
        float innerLeft = l + radius;
        float innerRight = r - radius;

        GL11.glBegin(GL11.GL_QUADS);
        // top bar
        GL11.glVertex2f(innerLeft, t + half);
        GL11.glVertex2f(innerRight, t + half);
        GL11.glVertex2f(innerRight, t - half);
        GL11.glVertex2f(innerLeft, t - half);
        // bottom bar
        GL11.glVertex2f(innerLeft, b + half);
        GL11.glVertex2f(innerRight, b + half);
        GL11.glVertex2f(innerRight, b - half);
        GL11.glVertex2f(innerLeft, b - half);
        // left bar
        GL11.glVertex2f(l - half, innerBottom);
        GL11.glVertex2f(l + half, innerBottom);
        GL11.glVertex2f(l + half, innerTop);
        GL11.glVertex2f(l - half, innerTop);
        // right bar
        GL11.glVertex2f(r - half, innerBottom);
        GL11.glVertex2f(r + half, innerBottom);
        GL11.glVertex2f(r + half, innerTop);
        GL11.glVertex2f(r - half, innerTop);
        GL11.glEnd();

        drawCornerRing(innerLeft, innerTop, radius, half, 90.0F, 180.0F);
        drawCornerRing(innerRight, innerTop, radius, half, 0.0F, 90.0F);
        drawCornerRing(innerLeft, innerBottom, radius, half, 180.0F, 270.0F);
        drawCornerRing(innerRight, innerBottom, radius, half, 270.0F, 360.0F);
    }

    private static void drawCornerRing(float cx, float cy, float radius, float half, float startDeg, float endDeg) {
        float outer = radius + half;
        float inner = radius - half;
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            float deg = startDeg + ((endDeg - startDeg) * (i / (float) CORNER_SEGMENTS));
            float rad = (float) Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            GL11.glVertex2f(cx + (outer * cos), cy - (outer * sin));
            GL11.glVertex2f(cx + (inner * cos), cy - (inner * sin));
        }
        GL11.glEnd();
    }
}
