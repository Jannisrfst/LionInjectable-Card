package com.lionclient;

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
    public static final String MOD_ID  = "lionclient";
    public static final String NAME    = "LionClient";
    public static final String VERSION = "1.1.1";

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
        lion.client.ClientLogger.info("[LionClient] forgeBus=" + System.identityHashCode(forgeBus)
                + " (" + forgeBus.getClass().getName() + ")"
                + " fmlBus=" + System.identityHashCode(fmlBus)
                + " (" + fmlBus.getClass().getName() + ")"
                + " sameBus=" + (forgeBus == fmlBus));
        try {
            forgeBus.register(this);
            lion.client.ClientLogger.info("[LionClient] registered on MinecraftForge.EVENT_BUS");
        } catch (Throwable t) {
            lion.client.ClientLogger.error("[LionClient] forgeBus.register failed", t);
        }
        if (fmlBus != forgeBus) {
            try {
                fmlBus.register(this);
                lion.client.ClientLogger.info("[LionClient] registered on FMLCommonHandler bus");
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[LionClient] fmlBus.register failed", t);
            }
        }
    }

    public void toggleClickGui() {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            lion.client.ClientLogger.info("[LionClient] toggleClickGui: currentScreen=" + mc.currentScreen
                    + " style=" + ClickGuiModule.getGuiStyle()
                    + " classic=" + System.identityHashCode(clickGuiScreen)
                    + " modern=" + System.identityHashCode(modernClickGuiScreen));
        } catch (Throwable ignored) {}
        if (mc.currentScreen == clickGuiScreen || mc.currentScreen == modernClickGuiScreen) {
            mc.displayGuiScreen(null);
            return;
        }
        if (mc.currentScreen == null) {
            try {
                net.minecraft.client.gui.GuiScreen target = ClickGuiModule.getGuiStyle() == ClickGuiModule.GuiStyle.CLASSIC
                        ? clickGuiScreen
                        : modernClickGuiScreen;
                mc.displayGuiScreen(target);
                lion.client.ClientLogger.info("[LionClient] displayGuiScreen called with "
                        + (target != null ? target.getClass().getName() : "null")
                        + " — after: currentScreen=" + mc.currentScreen);
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[LionClient] displayGuiScreen threw", t);
            }
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
}
