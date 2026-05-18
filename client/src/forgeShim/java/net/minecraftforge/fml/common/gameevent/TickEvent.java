package net.minecraftforge.fml.common.gameevent;

import net.minecraftforge.fml.common.eventhandler.Event;

public class TickEvent extends Event {

    public enum Type { CLIENT, SERVER, PLAYER, WORLD, RENDER }
    public enum Phase { START, END }
    public enum Side { CLIENT, SERVER }

    public final Type  type;
    public final Side  side;
    public final Phase phase;

    public TickEvent(Type type, Side side, Phase phase) {
        this.type  = type;
        this.side  = side;
        this.phase = phase;
    }

    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) { super(Type.CLIENT, Side.CLIENT, phase); }
    }

    public static class RenderTickEvent extends TickEvent {
        public final float renderTickTime;
        public RenderTickEvent(Phase phase, float renderTickTime) {
            super(Type.RENDER, Side.CLIENT, phase);
            this.renderTickTime = renderTickTime;
        }
    }

    public static class PlayerTickEvent extends TickEvent {
        public PlayerTickEvent(Phase phase) { super(Type.PLAYER, Side.CLIENT, phase); }
    }

    public static class ServerTickEvent extends TickEvent {
        public ServerTickEvent(Phase phase) { super(Type.SERVER, Side.SERVER, phase); }
    }

    public static class WorldTickEvent extends TickEvent {
        public WorldTickEvent(Phase phase) { super(Type.WORLD, Side.CLIENT, phase); }
    }
}
