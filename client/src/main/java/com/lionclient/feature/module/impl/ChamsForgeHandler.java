package com.lionclient.feature.module.impl;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public final class ChamsForgeHandler {
    private final ChamsModule module;
    private boolean modifying;
    private boolean depthWasEnabled;

    public ChamsForgeHandler(ChamsModule module) {
        this.module = module;
    }

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.entityPlayer;
        if (!module.shouldRenderChams(player)) {
            return;
        }
        depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        modifying = true;
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!modifying) {
            return;
        }
        if (depthWasEnabled) {
            GlStateManager.enableDepth();
        }
        modifying = false;
    }
}
