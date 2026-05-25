package com.lionclient.combat.lag;

import com.lionclient.event.SendPacketEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class LagHandler {
    private static final LagHandler INSTANCE = new LagHandler();

    private final OutboundTrack outbound = new OutboundTrack();

    private final Set<Packet<?>> packetFastTrack = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>())
    );

    public static LagHandler get() {
        return INSTANCE;
    }

    private LagHandler() {
    }

    public void requestLag(LagRequest request) {
        for (LagDirection direction : request.getDirections()) {
            if (direction == LagDirection.OUTBOUND) {
                outbound.addRequest(request);
            }
        }
    }

    public void releaseExpiredPackets(LagDirection direction, long maxAgeMs) {
        if (direction == LagDirection.OUTBOUND) {
            outbound.releaseExpiredPackets(maxAgeMs);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSendPacket(SendPacketEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getNetHandler() == null) {
            outbound.clear();
            return;
        }

        Packet<?> packet = event.getPacket();
        boolean fastTracked = packetFastTrack.remove(packet);

        if (event.isCanceled()) {
            return;
        }

        if (fastTracked) {
            return;
        }

        if (outbound.tick(packet)) {
            event.setCanceled(true);
        }
    }

    public void onGameTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getNetHandler() == null) {
            outbound.clear();
            return;
        }
        outbound.tick(null);
    }

    void markFastTrack(Packet<?> packet) {
        packetFastTrack.add(packet);
    }

    private final class OutboundTrack {
        private final List<Object> track = new ArrayList<Object>();
        private LagRequest currentlyAwaiting;

        synchronized void addRequest(LagRequest request) {
            track.add(request);
        }

        synchronized void clear() {
            track.clear();
            currentlyAwaiting = null;
        }

        synchronized boolean tick(Packet<?> packet) {
            if (track.isEmpty() && (currentlyAwaiting == null || currentlyAwaiting.getTimeout().isTimedOut())) {
                currentlyAwaiting = null;
                return false;
            }

            if (packet != null) {
                track.add(new PacketNode(packet));
            }

            LagRequest awaiting = currentlyAwaiting;

            try {
                while (awaiting == null || awaiting.getTimeout().isTimedOut()) {
                    Object popped = track.isEmpty() ? null : track.remove(0);
                    if (popped == null) {
                        awaiting = null;
                        break;
                    }

                    if (popped instanceof PacketNode) {
                        Packet<?> p = ((PacketNode) popped).packet;
                        markFastTrack(p);
                        LagDirection.OUTBOUND.passThroughChannel(p);
                    } else if (popped instanceof LagRequest) {
                        awaiting = (LagRequest) popped;
                    }
                }
            } catch (Throwable ignored) {
            }

            currentlyAwaiting = awaiting;
            return true;
        }

        synchronized void releaseExpiredPackets(long maxAgeMs) {
            long cutoff = System.currentTimeMillis() - maxAgeMs;
            List<PacketNode> toRelease = new ArrayList<PacketNode>();
            for (Object node : new ArrayList<Object>(track)) {
                if (node instanceof PacketNode) {
                    PacketNode pkt = (PacketNode) node;
                    if (pkt.queuedAtMs <= cutoff) {
                        toRelease.add(pkt);
                    }
                }
            }
            if (toRelease.isEmpty()) {
                return;
            }
            track.removeAll(toRelease);
            for (PacketNode pkt : toRelease) {
                markFastTrack(pkt.packet);
                LagDirection.OUTBOUND.passThroughChannel(pkt.packet);
            }
        }
    }

    private static final class PacketNode {
        private final Packet<?> packet;
        private final long queuedAtMs;

        PacketNode(Packet<?> packet) {
            this.packet = packet;
            this.queuedAtMs = System.currentTimeMillis();
        }
    }
}
