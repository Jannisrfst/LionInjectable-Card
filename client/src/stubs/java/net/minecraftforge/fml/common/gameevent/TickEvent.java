package net.minecraftforge.fml.common.gameevent;

import net.minecraftforge.fml.common.eventhandler.Event;

public class TickEvent extends Event {

    public enum Phase { START, END }

    public Phase phase;

    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent() {}
    }

    public static class RenderTickEvent extends TickEvent {
        public float renderTickTime;
        public RenderTickEvent() {}
    }
}
