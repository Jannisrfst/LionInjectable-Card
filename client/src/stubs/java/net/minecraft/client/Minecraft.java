package net.minecraft.client;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.RenderManager;
import net.minecraft.world.World;

public class Minecraft {
    public World          theWorld;
    public EntityPlayerSP thePlayer;

    public static Minecraft getMinecraft() { return null; }
    public RenderManager   getRenderManager() { return null; }
}
