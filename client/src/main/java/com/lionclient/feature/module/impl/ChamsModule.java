package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class ChamsModule extends Module {
    private final BooleanSetting seeInvis = new BooleanSetting("See Invis", false);
    private final BooleanSetting includeSelf = new BooleanSetting("Include Self", false);

    private boolean registered;
    private Object forgeHandler;
    private boolean rendering;

    public ChamsModule() {
        super("Chams", "Renders other players through walls using their normal skin.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(seeInvis);
        addSetting(includeSelf);
    }

    @Override
    protected void onEnable() {
        if (registered) {
            return;
        }
        if (hasForgeRenderPlayerEvent()) {
            forgeHandler = new ChamsForgeHandler(this);
            MinecraftForge.EVENT_BUS.register(forgeHandler);
        } else {
            MinecraftForge.EVENT_BUS.register(this);
        }
        registered = true;
    }

    @Override
    protected void onDisable() {
        if (!registered) {
            return;
        }
        if (forgeHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(forgeHandler);
            forgeHandler = null;
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
        }
        registered = false;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (rendering) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        if (rm == null) {
            return;
        }

        float partialTicks = event.partialTicks;
        rendering = true;

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        try {
            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            for (Object obj : mc.theWorld.playerEntities) {
                if (!(obj instanceof EntityPlayer)) {
                    continue;
                }
                EntityPlayer player = (EntityPlayer) obj;
                if (!shouldRenderChams(player)) {
                    continue;
                }
                try {
                    rm.renderEntitySimple(player, partialTicks);
                } catch (Throwable ignored) {
                }
            }
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            if (!blendWasEnabled) {
                GlStateManager.disableBlend();
            }
            if (cullWasEnabled) {
                GlStateManager.enableCull();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
            rendering = false;
        }
    }

    public boolean shouldRenderChams(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return false;
        }
        if (player == mc.thePlayer && !includeSelf.isEnabled()) {
            return false;
        }
        if (player.isInvisible() && !seeInvis.isEnabled()) {
            return false;
        }
        if (AntiBotModule.shouldIgnore(player)) {
            return false;
        }
        return true;
    }

    private static boolean hasForgeRenderPlayerEvent() {
        try {
            Class.forName("net.minecraftforge.client.event.RenderPlayerEvent$Pre", false,
                    ChamsModule.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
