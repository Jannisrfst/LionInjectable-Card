package com.lionclient.combat.lag;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;

public enum LagDirection {
    INBOUND,
    OUTBOUND;

    public static final Set<LagDirection> ONLY_INBOUND = EnumSet.of(INBOUND);
    public static final Set<LagDirection> ONLY_OUTBOUND = EnumSet.of(OUTBOUND);
    public static final Set<LagDirection> BIDIRECTIONAL = EnumSet.allOf(LagDirection.class);

    @SuppressWarnings("unchecked")
    public void passThroughChannel(Packet<?> packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getNetHandler() == null) {
            return;
        }

        if (this == OUTBOUND) {
            mc.getNetHandler().addToSendQueue(packet);
            return;
        }

        try {
            ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        } catch (Throwable ignored) {
        }
    }
}
