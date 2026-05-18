package net.minecraftforge.client.event;

import net.minecraftforge.fml.common.eventhandler.Event;

public class RenderWorldLastEvent extends Event {
    public final float partialTicks;
    public RenderWorldLastEvent(Object ctx, float partialTicks) {
        this.partialTicks = partialTicks;
    }
}
