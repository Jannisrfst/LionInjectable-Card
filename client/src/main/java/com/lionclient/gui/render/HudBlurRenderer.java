package com.lionclient.gui.render;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Draws a real frosted-glass blur behind arbitrary rounded regions (used by the HUD arraylist).
 *
 * <p>Technique: capture the game's main framebuffer, run a separable Gaussian blur through two
 * half-resolution ping-pong framebuffers with a runtime-compiled GLSL 120 shader, then paint the
 * blurred result into each requested region as a textured rounded quad using screen-space UVs.
 *
 * <p>Everything is wrapped defensively: if framebuffers are disabled, the shader fails to compile,
 * or any GL call throws, the renderer marks itself unavailable and {@link #render} returns
 * {@code false} so the caller can fall back to a plain opaque box. The HUD therefore never breaks,
 * it just loses the blur.
 */
public final class HudBlurRenderer {

    // 5-tap linear-sampled Gaussian (Rastergrid) — needs GL_LINEAR filtering on the source texture.
    private static final String VERTEX_SRC =
        "void main() {\n" +
        "    gl_Position = ftransform();\n" +
        "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
        "}\n";

    private static final String FRAGMENT_SRC =
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_dir;\n" +
        "void main() {\n" +
        "    vec2 uv = gl_TexCoord[0].st;\n" +
        "    vec4 c = texture2D(u_texture, uv) * 0.2270270270;\n" +
        "    c += texture2D(u_texture, uv + u_dir * 1.3846153846) * 0.3162162162;\n" +
        "    c += texture2D(u_texture, uv - u_dir * 1.3846153846) * 0.3162162162;\n" +
        "    c += texture2D(u_texture, uv + u_dir * 3.2307692308) * 0.0702702703;\n" +
        "    c += texture2D(u_texture, uv - u_dir * 3.2307692308) * 0.0702702703;\n" +
        "    c.a = 1.0;\n" +
        "    gl_FragColor = c;\n" +
        "}\n";

    private static final int DOWNSCALE = 2;
    private static final int CORNER_SEGMENTS = 8;

    private static boolean unavailable;
    private static boolean initialized;

    private static int program;
    private static int uTextureLoc;
    private static int uDirLoc;

    private static Framebuffer fboA;
    private static Framebuffer fboB;
    private static int fboWidth;
    private static int fboHeight;

    private HudBlurRenderer() {
    }

    /**
     * Blurs the scene behind each region. Each entry is {@code {left, top, right, bottom, radius}}
     * in GUI-scaled coordinates. {@code strength} (>= 1) scales blur spread and iteration count.
     *
     * @return {@code true} if the blur was painted, {@code false} if unavailable (caller should
     *         draw a solid box instead).
     */
    public static boolean render(List<float[]> regions, int strength) {
        if (unavailable || regions == null || regions.isEmpty()) {
            return false;
        }
        if (!OpenGlHelper.isFramebufferEnabled()) {
            return false; // FBOs off in video settings — not a permanent failure, so don't disable.
        }

        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();
        if (main == null || main.framebufferTexture == 0) {
            return false;
        }

        try {
            ensureInitialized();
            if (unavailable) {
                return false;
            }
            ensureFramebuffers(mc.displayWidth / DOWNSCALE, mc.displayHeight / DOWNSCALE);

            ScaledResolution res = new ScaledResolution(mc);
            int scaledWidth = res.getScaledWidth();
            int scaledHeight = res.getScaledHeight();
            if (scaledWidth <= 0 || scaledHeight <= 0) {
                return false;
            }

            int iterations = Math.max(1, Math.min(4, strength));
            float spread = Math.max(1.0F, strength);
            blurPasses(main, iterations, spread);

            // Restore the main framebuffer + GUI viewport, then paint the regions.
            main.bindFramebuffer(true);
            paintRegions(regions, scaledWidth, scaledHeight);
            return true;
        } catch (Throwable t) {
            // Any failure: give up permanently and fall back to solid boxes forever after.
            unavailable = true;
            try {
                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
            } catch (Throwable ignored) {
            }
            lion.client.ClientLogger.error("[HudBlur] disabled after failure", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ blur passes

    private static void blurPasses(Framebuffer main, int iterations, float spread) {
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL20.glUseProgram(program);
        GL20.glUniform1i(uTextureLoc, 0);

        float texelW = 1.0F / fboWidth;
        float texelH = 1.0F / fboHeight;

        int source = main.framebufferTexture;
        for (int i = 0; i < iterations; i++) {
            // Horizontal: source -> fboA
            fboA.bindFramebuffer(true);
            GL20.glUniform2f(uDirLoc, texelW * spread, 0.0F);
            blit(source);

            // Vertical: fboA -> fboB
            fboB.bindFramebuffer(true);
            GL20.glUniform2f(uDirLoc, 0.0F, texelH * spread);
            blit(fboA.framebufferTexture);

            source = fboB.framebufferTexture; // feed result back in for the next iteration
        }

        GL20.glUseProgram(0);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private static void blit(int texture) {
        GlStateManager.bindTexture(texture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(1.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(0.0F, 1.0F);
        GL11.glEnd();
    }

    // ------------------------------------------------------------------ region painting

    private static void paintRegions(List<float[]> regions, int scaledWidth, int scaledHeight) {
        // The blurred scene lives in fboB. Draw it opaque, clipped to each rounded region, with
        // UVs mapped to screen space (framebuffer texture origin is bottom-left, so v is flipped).
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.bindTexture(fboB.framebufferTexture);

        float invW = 1.0F / scaledWidth;
        float invH = 1.0F / scaledHeight;
        for (float[] region : regions) {
            fillRoundedTextured(region[0], region[1], region[2], region[3], region[4], invW, invH);
        }

        // Leave a neutral 2D state so the subsequent box fills + text render exactly as before.
        GlStateManager.bindTexture(0);
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Rounded rect (center quad + 4 edges + 4 corner fans) with screen-space texture coords. */
    private static void fillRoundedTextured(float l, float t, float r, float b, float radius,
                                            float invW, float invH) {
        if (r <= l || b <= t) {
            return;
        }
        float maxRadius = Math.min(r - l, b - t) / 2.0F;
        float rad = Math.max(0.0F, Math.min(radius, maxRadius));

        GL11.glBegin(GL11.GL_TRIANGLES);
        if (rad <= 0.05F) {
            quad(l, t, r, b, invW, invH);
            GL11.glEnd();
            return;
        }

        float il = l + rad;
        float ir = r - rad;
        float it = t + rad;
        float ib = b - rad;

        // center + edges as quads (split into triangles)
        quad(il, t, ir, b, invW, invH);   // center column (full height)
        quad(l, it, il, ib, invW, invH);   // left edge
        quad(ir, it, r, ib, invW, invH);   // right edge

        cornerFan(il, it, rad, 90.0F, 180.0F, invW, invH);  // top-left
        cornerFan(ir, it, rad, 0.0F, 90.0F, invW, invH);    // top-right
        cornerFan(ir, ib, rad, 270.0F, 360.0F, invW, invH); // bottom-right
        cornerFan(il, ib, rad, 180.0F, 270.0F, invW, invH); // bottom-left
        GL11.glEnd();
    }

    private static void quad(float l, float t, float r, float b, float invW, float invH) {
        tv(l, t, invW, invH); tv(r, t, invW, invH); tv(r, b, invW, invH);
        tv(l, t, invW, invH); tv(r, b, invW, invH); tv(l, b, invW, invH);
    }

    private static void cornerFan(float cx, float cy, float radius, float startDeg, float endDeg,
                                  float invW, float invH) {
        double start = Math.toRadians(startDeg);
        double step = Math.toRadians(endDeg - startDeg) / CORNER_SEGMENTS;
        for (int i = 0; i < CORNER_SEGMENTS; i++) {
            double a0 = start + (step * i);
            double a1 = start + (step * (i + 1));
            tv(cx, cy, invW, invH);
            tv(cx + (float) Math.cos(a0) * radius, cy - (float) Math.sin(a0) * radius, invW, invH);
            tv(cx + (float) Math.cos(a1) * radius, cy - (float) Math.sin(a1) * radius, invW, invH);
        }
    }

    /** Emits one vertex at GUI coord (x,y) with screen-space UV (framebuffer v flipped). */
    private static void tv(float x, float y, float invW, float invH) {
        GL11.glTexCoord2f(x * invW, 1.0F - (y * invH));
        GL11.glVertex2f(x, y);
    }

    // ------------------------------------------------------------------ init / resources

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        int vert = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SRC);
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SRC);
        if (vert == 0 || frag == 0) {
            unavailable = true;
            return;
        }

        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            lion.client.ClientLogger.error("[HudBlur] program link failed: " + GL20.glGetProgramInfoLog(program, 4096));
            unavailable = true;
            return;
        }

        uTextureLoc = GL20.glGetUniformLocation(program, "u_texture");
        uDirLoc = GL20.glGetUniformLocation(program, "u_dir");
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            lion.client.ClientLogger.error("[HudBlur] shader compile failed: " + GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void ensureFramebuffers(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (fboA != null && fboB != null && fboWidth == w && fboHeight == h) {
            return;
        }
        if (fboA != null) {
            fboA.deleteFramebuffer();
        }
        if (fboB != null) {
            fboB.deleteFramebuffer();
        }
        fboA = new Framebuffer(w, h, false);
        fboB = new Framebuffer(w, h, false);
        fboA.setFramebufferFilter(GL11.GL_LINEAR);
        fboB.setFramebufferFilter(GL11.GL_LINEAR);
        fboWidth = w;
        fboHeight = h;
    }
}
