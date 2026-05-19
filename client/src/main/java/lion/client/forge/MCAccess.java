package lion.client.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class MCAccess {

    private static volatile boolean initialised;
    private static volatile boolean usable;
    private static String           initError = "(not initialised)";

    public  static Class<?> mcClass;
    private static Method   mc_getMinecraft;
    public  static Field    mc_theWorld;
    public  static Field    mc_thePlayer;
    private static Method   mc_getRenderManager;

    public  static Field    rm_viewerPosX;
    public  static Field    rm_viewerPosY;
    public  static Field    rm_viewerPosZ;

    public  static Field    world_playerEntities;
    public  static Field    world_loadedEntityList;

    public  static Class<?> entityClass;
    public  static Class<?> entityPlayerClass;
    public  static Field    entity_posX, entity_posY, entity_posZ;
    public  static Field    entity_lastTickPosX, entity_lastTickPosY, entity_lastTickPosZ;
    private static Method   entity_isInvisible;

    public  static Class<?> guiScreenClass;
    public  static Method   mc_displayGuiScreen;
    private static Method   mc_addScheduledTask;
    public  static Field    mc_currentScreen;
    public  static Field    mc_displayWidth;
    public  static Field    mc_displayHeight;
    public  static Field    mc_fontRendererObj;
    public  static Class<?> scaledResolutionClass;
    public  static Method   sr_getScaledWidth;
    public  static Method   sr_getScaledHeight;
    public  static Method   fr_drawString;
    public  static Method   fr_getStringWidth;

    private MCAccess() {}

    public static synchronized boolean ensureInit() {
        if (initialised) return usable;
        initialised = true;
        try {
            mcClass = Class.forName("net.minecraft.client.Minecraft");
            mc_getMinecraft     = tryMethod(mcClass, "getMinecraft",     "func_71410_x");
            mc_theWorld         = tryField (mcClass, "theWorld",         "field_71441_e");
            mc_thePlayer        = tryField (mcClass, "thePlayer",        "field_71439_g");
            mc_getRenderManager = tryMethod(mcClass, "getRenderManager", "func_175598_ae");

            if (mc_getMinecraft == null || mc_theWorld == null || mc_thePlayer == null
                    || mc_getRenderManager == null) {
                initError = "Minecraft member lookup failed";
                return false;
            }

            Class<?> rmClass = mc_getRenderManager.getReturnType();
            rm_viewerPosX = tryField(rmClass, "viewerPosX", "field_78725_b");
            rm_viewerPosY = tryField(rmClass, "viewerPosY", "field_78726_c");
            rm_viewerPosZ = tryField(rmClass, "viewerPosZ", "field_78723_d");

            Class<?> worldClass = Class.forName("net.minecraft.world.World");
            world_playerEntities   = tryField(worldClass, "playerEntities",   "field_73010_i");
            world_loadedEntityList = tryField(worldClass, "loadedEntityList", "field_72996_f");

            entityClass = Class.forName("net.minecraft.entity.Entity");
            entityPlayerClass = Class.forName("net.minecraft.entity.player.EntityPlayer");
            entity_posX = tryField(entityClass, "posX", "field_70165_t");
            entity_posY = tryField(entityClass, "posY", "field_70163_u");
            entity_posZ = tryField(entityClass, "posZ", "field_70161_v");
            entity_lastTickPosX = tryField(entityClass, "lastTickPosX", "field_70169_q");
            entity_lastTickPosY = tryField(entityClass, "lastTickPosY", "field_70167_r");
            entity_lastTickPosZ = tryField(entityClass, "lastTickPosZ", "field_70166_s");
            entity_isInvisible  = tryMethod(entityClass, "isInvisible", "func_82150_aj");

            guiScreenClass    = Class.forName("net.minecraft.client.gui.GuiScreen");
            mc_currentScreen  = tryField (mcClass, "currentScreen",   "field_71462_r");
            mc_displayWidth   = tryField (mcClass, "displayWidth",    "field_71443_c");
            mc_displayHeight  = tryField (mcClass, "displayHeight",   "field_71440_d");
            mc_fontRendererObj = tryField(mcClass, "fontRendererObj", "field_71466_p");
            mc_displayGuiScreen = tryMethodWithArgs(mcClass,
                    new String[] { "displayGuiScreen", "func_147108_a" },
                    new Class<?>[] { guiScreenClass });
            mc_addScheduledTask = tryMethodWithArgs(mcClass,
                    new String[] { "addScheduledTask", "func_152344_a" },
                    new Class<?>[] { Runnable.class });

            scaledResolutionClass = Class.forName("net.minecraft.client.gui.ScaledResolution");
            sr_getScaledWidth  = tryMethod(scaledResolutionClass, "getScaledWidth",  "func_78326_a");
            sr_getScaledHeight = tryMethod(scaledResolutionClass, "getScaledHeight", "func_78328_b");

            Class<?> fontRendererClass = Class.forName("net.minecraft.client.gui.FontRenderer");
            fr_drawString = tryMethodWithArgs(fontRendererClass,
                    new String[] { "drawString", "func_175065_a" },
                    new Class<?>[] { String.class, float.class, float.class, int.class, boolean.class });
            if (fr_drawString == null) {
                fr_drawString = tryMethodWithArgs(fontRendererClass,
                        new String[] { "drawString", "func_78276_b" },
                        new Class<?>[] { String.class, int.class, int.class, int.class });
            }
            fr_getStringWidth = tryMethodWithArgs(fontRendererClass,
                    new String[] { "getStringWidth", "func_78256_a" },
                    new Class<?>[] { String.class });

            usable = true;
            return true;
        } catch (Throwable t) {
            initError = "init threw: " + t;
            usable = false;
            return false;
        }
    }

    public static boolean isUsable() { ensureInit(); return usable; }
    public static String  initError() { return initError; }

    public static Object mc() {
        try { return mc_getMinecraft.invoke(null); }
        catch (Throwable t) { return null; }
    }

    public static Object world(Object mc) {
        try { return mc_theWorld.get(mc); }
        catch (Throwable t) { return null; }
    }

    public static Object thePlayer(Object mc) {
        try { return mc_thePlayer.get(mc); }
        catch (Throwable t) { return null; }
    }

    public static Object renderManager(Object mc) {
        try { return mc_getRenderManager.invoke(mc); }
        catch (Throwable t) { return null; }
    }

    public static List<?> playerEntities(Object world) {
        try { return (List<?>) world_playerEntities.get(world); }
        catch (Throwable t) { return java.util.Collections.emptyList(); }
    }

    public static double getDouble(Field f, Object o) {
        try { return f.getDouble(o); }
        catch (Throwable t) { return 0.0; }
    }

    public static boolean isInvisible(Object entity) {
        try { return (Boolean) entity_isInvisible.invoke(entity); }
        catch (Throwable t) { return false; }
    }

    private static Method tryMethod(Class<?> c, String... names) {
        for (String n : names) {
            try {
                Method m = c.getDeclaredMethod(n);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                try {
                    Method m = c.getMethod(n);
                    return m;
                } catch (NoSuchMethodException ignored2) {}
            }
        }
        return null;
    }

    private static Method tryMethodWithArgs(Class<?> c, String[] names, Class<?>[] args) {
        for (String n : names) {
            try {
                Method m = c.getDeclaredMethod(n, args);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                try {
                    Method m = c.getMethod(n, args);
                    return m;
                } catch (NoSuchMethodException ignored2) {}
            }
        }
        return null;
    }

    public static void displayGuiScreen(Object guiScreen) {
        if (mc_displayGuiScreen == null) return;
        try {
            Object mc = mc();
            if (mc != null) mc_displayGuiScreen.invoke(mc, guiScreen);
        } catch (Throwable ignored) {
        }
    }

    public static void runOnClientThread(Runnable task) {
        if (mc_addScheduledTask == null) { task.run(); return; }
        try {
            Object mc = mc();
            if (mc != null) mc_addScheduledTask.invoke(mc, task);
            else task.run();
        } catch (Throwable t) {
            task.run();
        }
    }

    public static Object currentScreen() {
        if (mc_currentScreen == null) return null;
        try { return mc_currentScreen.get(mc()); } catch (Throwable t) { return null; }
    }

    public static int displayWidth() {
        try { return mc_displayWidth.getInt(mc()); } catch (Throwable t) { return 854; }
    }
    public static int displayHeight() {
        try { return mc_displayHeight.getInt(mc()); } catch (Throwable t) { return 480; }
    }

    public static Object fontRenderer() {
        if (mc_fontRendererObj == null) return null;
        try { return mc_fontRendererObj.get(mc()); } catch (Throwable t) { return null; }
    }

    public static int drawString(String text, int x, int y, int color) {
        Object fr = fontRenderer();
        if (fr == null || fr_drawString == null) return 0;
        try {
            if (fr_drawString.getParameterTypes().length == 5) {
                Object r = fr_drawString.invoke(fr, text, (float) x, (float) y, color, Boolean.TRUE);
                return (r instanceof Number) ? ((Number) r).intValue() : 0;
            } else {
                Object r = fr_drawString.invoke(fr, text, x, y, color);
                return (r instanceof Number) ? ((Number) r).intValue() : 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int stringWidth(String text) {
        Object fr = fontRenderer();
        if (fr == null || fr_getStringWidth == null) return 0;
        try {
            Object r = fr_getStringWidth.invoke(fr, text);
            return (r instanceof Number) ? ((Number) r).intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int[] scaledResolution() {
        int[] fallback = { displayWidth(), displayHeight() };
        if (scaledResolutionClass == null) return fallback;
        try {
            Object sr = scaledResolutionClass.getConstructor(mcClass).newInstance(mc());
            int w = (int) sr_getScaledWidth.invoke(sr);
            int h = (int) sr_getScaledHeight.invoke(sr);
            return new int[] { w, h };
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static Field tryField(Class<?> c, String... names) {
        for (String n : names) {
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                for (Class<?> sup = c.getSuperclass(); sup != null && sup != Object.class; sup = sup.getSuperclass()) {
                    try {
                        Field f = sup.getDeclaredField(n);
                        f.setAccessible(true);
                        return f;
                    } catch (NoSuchFieldException ignored2) {}
                }
            }
        }
        return null;
    }

    private static String nm(java.lang.reflect.Member m) {
        return m == null ? "(NULL)" : m.getName();
    }
}
