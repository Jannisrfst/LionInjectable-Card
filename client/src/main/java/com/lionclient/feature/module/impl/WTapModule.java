package com.lionclient.feature.module.impl;

import com.lionclient.event.SendPacketEvent;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Resets the sprint state after every hit so each sprint-attack deals maximum knockback.
 *
 * <p>In 1.8 combat, attacking while {@code isSprinting()} applies one extra knockback level and
 * then clears the sprint flag. To keep landing max-knockback hits the sprint must be re-engaged
 * before the next swing. This module triggers on the outbound attack packet
 * ({@link C02PacketUseEntity} with {@code ATTACK}) and offers two reset strategies:</p>
 *
 * <ul>
 *   <li>{@code WTAP} &mdash; briefly releases the forward key so movement stops, the sprint drops
 *       naturally, and it re-engages once the key is restored. Visually legit, but loses forward
 *       momentum for a moment.</li>
 *   <li>{@code PACKET} &mdash; sends STOP/START sprinting to the server without releasing any key.
 *       No momentum loss, purely state-based.</li>
 * </ul>
 */
public final class WTapModule extends Module {
    public enum Mode { WTAP, PACKET }

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.WTAP);
    private final NumberSetting releaseTicks = new NumberSetting("Release Ticks", 1, 5, 1, 1);

    private boolean forgeRegistered;

    // WTAP state
    private boolean releasing;
    private int releaseUntilTick;

    // PACKET state
    private boolean pendingResprint;
    private int resprintTick;

    public WTapModule() {
        super("WTap", "Resets your sprint after every hit for maximum knockback.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(releaseTicks);
        releaseTicks.setVisibility(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.WTAP;
            }
        });
    }

    @Override
    protected void onEnable() {
        releasing = false;
        pendingResprint = false;
        registerForge();
    }

    @Override
    protected void onDisable() {
        unregisterForge();
        restoreForwardKey();
        releasing = false;
        pendingResprint = false;
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C02PacketUseEntity)) {
            return;
        }
        if (((C02PacketUseEntity) event.getPacket()).getAction() != C02PacketUseEntity.Action.ATTACK) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null || !player.isSprinting()) {
            return;
        }

        if (mode.getValue() == Mode.WTAP) {
            beginKeyRelease(minecraft, player);
        } else {
            doPacketReset(player);
        }
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) {
            releasing = false;
            pendingResprint = false;
            return;
        }

        if (releasing && player.ticksExisted >= releaseUntilTick) {
            restoreForwardKey();
            releasing = false;
        }

        if (pendingResprint && player.ticksExisted >= resprintTick) {
            player.setSprinting(true);
            pendingResprint = false;
        }
    }

    private void beginKeyRelease(Minecraft minecraft, EntityPlayerSP player) {
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindForward.getKeyCode(), false);
        releasing = true;
        releaseUntilTick = player.ticksExisted + Math.max(1, releaseTicks.getValue());
    }

    private void restoreForwardKey() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings == null) {
            return;
        }

        int forwardKey = minecraft.gameSettings.keyBindForward.getKeyCode();
        boolean physicallyDown = forwardKey < 0
                ? Mouse.isButtonDown(forwardKey + 100)
                : Keyboard.isKeyDown(forwardKey);
        KeyBinding.setKeyBindState(forwardKey, physicallyDown);
    }

    private void doPacketReset(EntityPlayerSP player) {
        // Toggling the sprint flag makes EntityPlayerSP.onLivingUpdate emit the matching
        // STOP_SPRINTING packet this tick and START_SPRINTING once we re-enable it next tick,
        // resetting the server-side sprint state without releasing any movement key.
        player.setSprinting(false);
        pendingResprint = true;
        resprintTick = player.ticksExisted + 1;
    }

    @Override
    public String getHudInfo() {
        return mode.getValue() == Mode.WTAP ? "WTap" : "Packet";
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
