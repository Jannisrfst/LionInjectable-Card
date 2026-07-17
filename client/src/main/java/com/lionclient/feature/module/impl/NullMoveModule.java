package com.lionclient.feature.module.impl;

import com.lionclient.event.PrePlayerInputEvent;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

/**
 * Null Move (a.k.a. SOCD / Snap Tap / Null Binds).
 *
 * <p>When two opposing movement keys are held at the same time, vanilla Minecraft
 * cancels them out (A + D or W + S resolve to "no movement"). Null Move instead lets
 * the <em>most recently pressed</em> key win, giving an instant direction change.</p>
 */
public final class NullMoveModule extends Module {

    private final BooleanSetting horizontal = new BooleanSetting("Horizontal (A + D)", true);
    private final BooleanSetting vertical   = new BooleanSetting("Vertical (W + S)", true);

    private boolean forgeRegistered;

    // Physical key state from the previous tick, for edge detection.
    private boolean prevForward;
    private boolean prevBack;
    private boolean prevLeft;
    private boolean prevRight;

    // Which side of each axis was pressed last (true = forward / left).
    private boolean forwardWins = true;
    private boolean leftWins = true;

    public NullMoveModule() {
        super("Null Move",
                "Instantly switch directions when opposing movement keys are held (SOCD / Snap Tap).",
                Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(horizontal);
        addSetting(vertical);
    }

    @Override
    protected void onEnable() {
        resetState();
        registerForge();
    }

    @Override
    protected void onDisable() {
        unregisterForge();
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null || minecraft.thePlayer == null) {
            return;
        }

        boolean forwardDown = isBindDown(minecraft.gameSettings.keyBindForward);
        boolean backDown    = isBindDown(minecraft.gameSettings.keyBindBack);
        boolean leftDown    = isBindDown(minecraft.gameSettings.keyBindLeft);
        boolean rightDown   = isBindDown(minecraft.gameSettings.keyBindRight);

        // On a fresh key-down, that direction becomes the winner for its axis.
        if (forwardDown && !prevForward) forwardWins = true;
        if (backDown && !prevBack)       forwardWins = false;
        if (leftDown && !prevLeft)       leftWins = true;
        if (rightDown && !prevRight)     leftWins = false;

        prevForward = forwardDown;
        prevBack = backDown;
        prevLeft = leftDown;
        prevRight = rightDown;

        boolean sneaking = event.isSneak();

        // Forward / back conflict -> keep the last pressed direction (matches vanilla scaling).
        if (vertical.isEnabled() && forwardDown && backDown) {
            float value = forwardWins ? 1.0F : -1.0F;
            if (sneaking) {
                value *= 0.3F;
            }
            event.setForward(value);
        }

        // Left / right conflict.
        if (horizontal.isEnabled() && leftDown && rightDown) {
            float value = leftWins ? 1.0F : -1.0F;
            if (sneaking) {
                value *= 0.3F;
            }
            event.setStrafe(value);
        }
    }

    private boolean isBindDown(KeyBinding bind) {
        if (bind == null) {
            return false;
        }
        int code = bind.getKeyCode();
        // Only keyboard binds can be polled here; mouse binds (negative codes) fall back to vanilla.
        return code >= 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }

    private void resetState() {
        prevForward = prevBack = prevLeft = prevRight = false;
        forwardWins = leftWins = true;
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
