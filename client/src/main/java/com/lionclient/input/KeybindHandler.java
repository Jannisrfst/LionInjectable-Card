package com.lionclient.input;

import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.gui.ClickGuiScreen;
import com.lionclient.gui.ModernClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class KeybindHandler {

    private final ModuleManager moduleManager;
    private final boolean[]     prevDown = new boolean[Keyboard.KEYBOARD_SIZE];

    private KeybindHandler(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public static void register(ModuleManager moduleManager) {
        net.minecraftforge.fml.common.eventhandler.EventBus bus =
                FMLCommonHandler.instance().bus();
        bus.register(new KeybindHandler(moduleManager));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (!shouldDispatch(mc)) {
            for (int i = 0; i < prevDown.length; i++) prevDown[i] = false;
            return;
        }

        for (Module module : moduleManager.getModules()) {
            int kc = module.getKeyCode();
            if (kc == Keyboard.KEY_NONE || kc < 0 || kc >= prevDown.length) continue;
            boolean down = Keyboard.isKeyDown(kc);
            if (down && !prevDown[kc]) {
                module.toggle();
            }
            prevDown[kc] = down;
        }
    }

    private static boolean shouldDispatch(Minecraft mc) {
        if (mc == null) return false;
        Object screen = mc.currentScreen;
        if (screen == null) return true;
        if (screen instanceof ClickGuiScreen)       return true;
        if (screen instanceof ModernClickGuiScreen) return true;
        return false;
    }
}
