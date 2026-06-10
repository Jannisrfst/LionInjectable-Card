package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

public final class FreeLookModule extends Module {
    public enum Mode { TOGGLE, HOLD }

    private static FreeLookModule instance;

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.TOGGLE);

    private boolean active;
    private int previousPerspective;

    private float cameraYaw;
    private float cameraPitch;

    private float savedYaw;
    private float savedPitch;
    private float savedPrevYaw;
    private float savedPrevPitch;
    private boolean swapped;

    public FreeLookModule() {
        super("FreeLook", "Look around freely in third person without turning your body.", Category.RENDER, Keyboard.KEY_NONE);
        instance = this;
        addSetting(mode);
    }

    public static FreeLookModule getInstance() {
        return instance;
    }

    @Override
    public boolean handlesOwnKeybind() {
        return true;
    }

    @Override
    public void onKeybind(boolean down, boolean pressedEdge) {
        if (!isEnabled()) {
            setActive(false);
            return;
        }
        if (mode.getValue() == Mode.HOLD) {
            setActive(down);
        } else if (pressedEdge) {
            setActive(!active);
        }
    }

    @Override
    protected void onDisable() {
        setActive(false);
    }

    public boolean isActive() {
        return isEnabled() && active;
    }

    private void setActive(boolean value) {
        if (value == active) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (value) {
            if (mc.thePlayer == null || mc.gameSettings == null) {
                return;
            }
            previousPerspective = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 1;
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            active = true;
        } else {
            active = false;
            if (mc.gameSettings != null) {
                mc.gameSettings.thirdPersonView = previousPerspective;
            }
        }
    }

    public void applyMouseDelta(float yawDelta, float pitchDelta) {
        cameraYaw = (float) (cameraYaw + yawDelta * 0.15D);
        cameraPitch = (float) (cameraPitch - pitchDelta * 0.15D);
        cameraPitch = MathHelper.clamp_float(cameraPitch, -90.0F, 90.0F);
    }

    public void cameraPre() {
        if (!isActive()) {
            return;
        }
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) {
            return;
        }
        savedYaw = view.rotationYaw;
        savedPitch = view.rotationPitch;
        savedPrevYaw = view.prevRotationYaw;
        savedPrevPitch = view.prevRotationPitch;
        view.rotationYaw = cameraYaw;
        view.prevRotationYaw = cameraYaw;
        view.rotationPitch = cameraPitch;
        view.prevRotationPitch = cameraPitch;
        swapped = true;
    }

    public void cameraPost() {
        if (!swapped) {
            return;
        }
        swapped = false;
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) {
            return;
        }
        view.rotationYaw = savedYaw;
        view.rotationPitch = savedPitch;
        view.prevRotationYaw = savedPrevYaw;
        view.prevRotationPitch = savedPrevPitch;
    }
}
