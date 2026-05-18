package net.minecraftforge.fml.common;

import net.minecraftforge.fml.common.eventhandler.EventBus;

public class FMLCommonHandler {
    private static final FMLCommonHandler INSTANCE = new FMLCommonHandler();
    public static FMLCommonHandler instance() { return INSTANCE; }
    public EventBus bus() { return net.minecraftforge.common.MinecraftForge.EVENT_BUS; }
}
