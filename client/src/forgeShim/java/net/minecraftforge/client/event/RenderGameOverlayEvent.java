package net.minecraftforge.client.event;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;

public class RenderGameOverlayEvent extends Event {
    public final float            partialTicks;
    public final ScaledResolution resolution;

    public RenderGameOverlayEvent(float partialTicks, ScaledResolution resolution) {
        this.partialTicks = partialTicks;
        this.resolution   = resolution;
    }

    public static class Pre extends RenderGameOverlayEvent {
        public Pre(float partialTicks, ScaledResolution resolution) { super(partialTicks, resolution); }
    }
    public static class Post extends RenderGameOverlayEvent {
        public Post(float partialTicks, ScaledResolution resolution) { super(partialTicks, resolution); }
    }

    public static class Text extends RenderGameOverlayEvent {
        public final ArrayList<String> left;
        public final ArrayList<String> right;
        public Text(RenderGameOverlayEvent parent, ArrayList<String> left, ArrayList<String> right) {
            super(parent != null ? parent.partialTicks : 0f,
                  parent != null ? parent.resolution   : null);
            this.left  = left  != null ? left  : new ArrayList<String>();
            this.right = right != null ? right : new ArrayList<String>();
        }
    }
}
