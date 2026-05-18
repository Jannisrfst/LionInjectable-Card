package com.lionclient.feature.module.impl;

import com.lionclient.event.PrePlayerInputEvent;
import com.lionclient.event.SendPacketEvent;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public final class StasisModule extends Module {

    private boolean forgeRegistered;

    public StasisModule() {
        super("Stasis", "Freezes the player on the server's view until disabled.", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        registerForge();
    }

    @Override
    protected void onDisable() {
        unregisterForge();
    }

    @Override
    public void onClientTick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return;
        }

        EntityPlayerSP player = minecraft.thePlayer;
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        if (event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook) {
            return;
        }
        event.setCanceled(true);
    }

    private void registerForge() {
        if (forgeRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        forgeRegistered = true;
    }

    private void unregisterForge() {
        if (!forgeRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        forgeRegistered = false;
    }
}
