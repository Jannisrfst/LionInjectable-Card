package com.lionclient;

import com.lionclient.combat.lag.LagHandler;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.feature.module.impl.ClickGuiModule;
import com.lionclient.feature.module.impl.HudModule;
import com.lionclient.gui.ClickGuiScreen;
import com.lionclient.gui.HudEditorScreen;
import com.lionclient.gui.ModernClickGuiScreen;
import com.lionclient.input.KeybindHandler;
import com.lionclient.network.KnockbackDelayBuffer;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class LionClient {
    public static final String MOD_ID  = "lionclientinjectable";
    public static final String NAME    = "LionClientInjectable";
    public static final String VERSION = "1.0.1";

    static {
        registerFakeModContainer();
    }

    private static LionClient instance;

    private final ModuleManager moduleManager = new ModuleManager();
    private final KnockbackDelayBuffer knockbackDelayBuffer = new KnockbackDelayBuffer();
    private final ClickGuiScreen       clickGuiScreen       = new ClickGuiScreen(moduleManager);
    private final ModernClickGuiScreen modernClickGuiScreen = new ModernClickGuiScreen(moduleManager);

    public static LionClient getInstance() { return instance; }

    public ModuleManager getModuleManager() { return moduleManager; }

    public KnockbackDelayBuffer getKnockbackDelayBuffer() { return knockbackDelayBuffer; }

    public void bootstrap() {
        instance = this;
        KeybindHandler.register(moduleManager);
        net.minecraftforge.fml.common.eventhandler.EventBus forgeBus =
                net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        net.minecraftforge.fml.common.eventhandler.EventBus fmlBus =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().bus();
        try {
            forgeBus.register(this);
        } catch (Throwable t) {
            lion.client.ClientLogger.error("[LionClient] forgeBus.register failed", t);
        }
        if (fmlBus != forgeBus) {
            try {
                fmlBus.register(this);
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[LionClient] fmlBus.register failed", t);
            }
        }
        try {
            forgeBus.register(LagHandler.get());
        } catch (Throwable t) {
            lion.client.ClientLogger.error("[LionClient] LagHandler register failed", t);
        }
    }

    public void toggleClickGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == clickGuiScreen || mc.currentScreen == modernClickGuiScreen) {
            mc.displayGuiScreen(null);
            return;
        }
        if (mc.currentScreen == null) {
            net.minecraft.client.gui.GuiScreen target = ClickGuiModule.getGuiStyle() == ClickGuiModule.GuiStyle.CLASSIC
                    ? clickGuiScreen
                    : modernClickGuiScreen;
            mc.displayGuiScreen(target);
        }
    }

    public void refreshClickGuiStyle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != clickGuiScreen && mc.currentScreen != modernClickGuiScreen) return;
        mc.displayGuiScreen(ClickGuiModule.getGuiStyle() == ClickGuiModule.GuiStyle.CLASSIC
                ? clickGuiScreen
                : modernClickGuiScreen);
    }

    public void openHudEditor() {
        HudModule hud = HudModule.getInstance();
        if (hud == null) return;
        Minecraft.getMinecraft().displayGuiScreen(new HudEditorScreen(hud));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        moduleManager.onClientTick(event);
        if (event.phase == TickEvent.Phase.START) {
            try { com.lionclient.combat.ClientRotationHelper.get().updateServerRotations(); } catch (Throwable ignored) {}
            try { com.lionclient.combat.ClientRotationHelper.get().onRunTickStart(); } catch (Throwable ignored) {}
            try {
                if (com.lionclient.combat.ClientRotationHelper.get().tryClaimPrePlayerInteractPost()) {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.lionclient.event.PrePlayerInteractEvent());
                }
            } catch (Throwable ignored) {}
            return;
        }
        moduleManager.onClientTick();
        try { LagHandler.get().onGameTick(); } catch (Throwable ignored) {}
        try { knockbackDelayBuffer.onClientTick(); } catch (Throwable ignored) {}
        try { com.lionclient.combat.ClientRotationHelper.get().restoreTickSwap(); } catch (Throwable ignored) {}
        try { com.lionclient.combat.ClientRotationHelper.get().endOfTickReset(); } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        moduleManager.onPlayerTick(event);
        if (event.phase == TickEvent.Phase.START && event.player == Minecraft.getMinecraft().thePlayer) {
            try { com.lionclient.combat.ClientRotationHelper.get().applyTickSwap(event.player); } catch (Throwable ignored) {}
        }
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        moduleManager.onMouseEvent(event);
    }

    @SubscribeEvent
    public void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        moduleManager.onPlayerJump(event);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        moduleManager.onRenderTick(event);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        moduleManager.onRenderWorld(event);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        moduleManager.onRenderOverlay(event);
    }

    @SuppressWarnings("unchecked")
    private static void registerFakeModContainer() {
        try {
            Class<?> loaderCls    = Class.forName("net.minecraftforge.fml.common.Loader");
            Class<?> containerCls = Class.forName("net.minecraftforge.fml.common.ModContainer");
            Object   loader       = loaderCls.getMethod("instance").invoke(null);

            final java.io.File src = resolveOurJarFile();

            final Object fake = java.lang.reflect.Proxy.newProxyInstance(
                    LionClient.class.getClassLoader(),
                    new Class<?>[]{ containerCls },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getSource":            return src;
                            case "getModId":             return MOD_ID;
                            case "getName":              return NAME;
                            case "getVersion":           return VERSION;
                            case "isImmutable":          return true;
                            case "getOwnedPackages":     return java.util.Collections.emptyList();
                            case "getRequirements":      return java.util.Collections.emptySet();
                            case "getDependencies":      return java.util.Collections.emptyList();
                            case "getDependants":        return java.util.Collections.emptyList();
                            case "getCustomModProperties":  return java.util.Collections.emptyMap();
                            case "getSharedModDescriptor":  return java.util.Collections.emptyMap();
                            default:
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class || rt == Boolean.class) return false;
                                if (rt == int.class    || rt == Integer.class)  return 0;
                                return null;
                        }
                    });

            try {
                java.util.List<Object> list =
                        (java.util.List<Object>) loaderCls.getMethod("getActiveModList").invoke(loader);
                if (!list.contains(fake)) list.add(fake);
            } catch (Throwable ignored) {}

            java.lang.reflect.Field modControllerField = loaderCls.getDeclaredField("modController");
            modControllerField.setAccessible(true);
            Object modController = modControllerField.get(loader);
            if (modController == null) {
                lion.client.ClientLogger.error("[FakeMod] modController is null on Loader", null);
                return;
            }

            try {
                java.lang.reflect.Field activeContainerField =
                        modController.getClass().getDeclaredField("activeContainer");
                activeContainerField.setAccessible(true);
                activeContainerField.set(modController, fake);
                Object verify = activeContainerField.get(modController);
                lion.client.ClientLogger.info("[FakeMod] activeContainer set, verify=" + (verify == fake));
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[FakeMod] set activeContainer failed", t);
            }

            try {
                java.lang.reflect.Field packageOwnersField =
                        modController.getClass().getDeclaredField("packageOwners");
                packageOwnersField.setAccessible(true);
                Object packageOwners = packageOwnersField.get(modController);
                if (packageOwners != null) {
                    java.lang.reflect.Method put = null;
                    for (java.lang.reflect.Method m : packageOwners.getClass().getMethods()) {
                        if ("put".equals(m.getName()) && m.getParameterTypes().length == 2) {
                            put = m;
                            break;
                        }
                    }
                    if (put != null) {
                        java.util.Set<String> pkgs = scanJarPackages(src);
                        for (String pkg : pkgs) put.invoke(packageOwners, pkg, fake);
                        lion.client.ClientLogger.info("[FakeMod] added " + pkgs.size() + " packages to packageOwners");
                    }
                }
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[FakeMod] populate packageOwners failed", t);
            }
        } catch (Throwable t) {
            try { lion.client.ClientLogger.error("[FakeMod] registerFakeModContainer failed", t); }
            catch (Throwable ignored) {}
        }
    }

    private static java.io.File resolveOurJarFile() {
        try {
            java.net.URL url = LionClient.class.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return null;
            String s = url.toString();
            if (s.startsWith("jar:")) {
                s = s.substring(4);
                int bang = s.indexOf("!/");
                if (bang >= 0) s = s.substring(0, bang);
            }
            if (s.startsWith("file:")) {
                try { return new java.io.File(new java.net.URI(s)); }
                catch (Throwable ignored) {
                    String path = s.substring(5);
                    while (path.startsWith("/")) path = path.substring(1);
                    return new java.io.File(java.net.URLDecoder.decode(path, "UTF-8"));
                }
            }
            return new java.io.File(s);
        } catch (Throwable t) {
            try { lion.client.ClientLogger.error("[FakeMod] resolveOurJarFile failed", t); }
            catch (Throwable ignored) {}
            return null;
        }
    }

    private static java.util.Set<String> scanJarPackages(java.io.File jarFile) {
        java.util.Set<String> packages = new java.util.HashSet<>();
        if (jarFile == null || !jarFile.isFile()) return packages;
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class")) continue;
                int slash = name.lastIndexOf('/');
                if (slash <= 0) continue;
                String pkg = name.substring(0, slash).replace('/', '.');
                if (pkg.startsWith("com.lionclient") || pkg.startsWith("lion.client")) {
                    packages.add(pkg);
                }
            }
        } catch (Throwable ignored) {}
        return packages;
    }
}
