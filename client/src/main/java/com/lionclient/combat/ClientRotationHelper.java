package com.lionclient.combat;

import com.lionclient.event.ClientRotationEvent;
import com.lionclient.event.PrePlayerInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ClientRotationHelper {
    private static final ClientRotationHelper INSTANCE = new ClientRotationHelper();

    private final Minecraft minecraft = Minecraft.getMinecraft();

    private Float serverYaw;
    private Float serverPitch;
    private boolean setRotations;
    private boolean rotationsUpdatedThisTick;

    private boolean silentRotation;
    private boolean silentMoveFix;
    private float savedYaw;
    private float savedPitch;
    private float savedPrevYaw;
    private float savedPrevPitch;

    public boolean swappedForMouseOver;
    private boolean swappedForWalkingUpdate;

    private float savedYawForLiving;
    private float savedPitchForLiving;
    private float savedPrevYawForLiving;
    private float savedPrevPitchForLiving;
    private boolean swappedForLivingUpdate;

    private float savedYawForMouseOver;
    private float savedPitchForMouseOver;
    private float savedPrevYawForMouseOver;
    private float savedPrevPitchForMouseOver;

    private float savedYawForTickSwap;
    private float savedPitchForTickSwap;
    private float savedPrevYawForTickSwap;
    private float savedPrevPitchForTickSwap;
    private boolean swappedForTickSwap;

    private ClientRotationHelper() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static ClientRotationHelper get() {
        return INSTANCE;
    }

    public static float unwrapYaw(float yaw, float previousYaw) {
        return previousYaw + ((((yaw - previousYaw + 180.0F) % 360.0F) + 360.0F) % 360.0F - 180.0F);
    }

    public void onRunTickStart() {
        if (minecraft.thePlayer != null && setRotations && !silentRotation) {
            if (serverYaw != null && !serverYaw.isNaN()) {
                float yaw = serverYaw.floatValue();
                minecraft.thePlayer.prevRotationYaw       = minecraft.thePlayer.rotationYaw;
                minecraft.thePlayer.rotationYaw           = yaw;
                minecraft.thePlayer.rotationYawHead       = yaw;
                minecraft.thePlayer.renderYawOffset       = yaw;
                minecraft.thePlayer.prevRotationYawHead   = yaw;
                minecraft.thePlayer.prevRenderYawOffset   = yaw;
            }
            if (serverPitch != null && !serverPitch.isNaN()) {
                float pitch = serverPitch.floatValue();
                minecraft.thePlayer.prevRotationPitch = minecraft.thePlayer.rotationPitch;
                minecraft.thePlayer.rotationPitch     = pitch;
            }
        }

        if (minecraft.thePlayer != null) {
            KillAuraRotationUtils.serverRotations[0] = minecraft.thePlayer.rotationYaw;
            KillAuraRotationUtils.serverRotations[1] = minecraft.thePlayer.rotationPitch;
        }
    }

    public void endOfTickReset() {
        serverYaw = null;
        serverPitch = null;
        setRotations = false;
        silentRotation = false;
        silentMoveFix = false;
        rotationsUpdatedThisTick = false;
        swappedForMouseOver = false;
        swappedForWalkingUpdate = false;
        swappedForLivingUpdate = false;
        prePlayerInteractPosted = false;
    }

    public void requestSilentMoveFix() {
        silentMoveFix = true;
    }

    public boolean isSilentMoveFixRequested() {
        return silentMoveFix;
    }

    public void applyTickSwap(Entity entity) {
        if (entity == null || entity != minecraft.thePlayer) return;
        if (!setRotations || !silentRotation) return;
        if (serverYaw == null || serverYaw.isNaN()) return;
        if (swappedForTickSwap) return;

        float yaw = serverYaw.floatValue();
        float pitch = (serverPitch != null && !serverPitch.isNaN())
                ? serverPitch.floatValue()
                : entity.rotationPitch;

        savedYawForTickSwap = entity.rotationYaw;
        savedPrevYawForTickSwap = entity.prevRotationYaw;
        savedPitchForTickSwap = entity.rotationPitch;
        savedPrevPitchForTickSwap = entity.prevRotationPitch;

        entity.rotationYaw = yaw;
        entity.prevRotationYaw = yaw;
        entity.rotationPitch = pitch;
        entity.prevRotationPitch = pitch;

        swappedForTickSwap = true;
    }

    public void restoreTickSwap() {
        if (!swappedForTickSwap || minecraft.thePlayer == null) return;
        minecraft.thePlayer.rotationYaw = savedYawForTickSwap;
        minecraft.thePlayer.prevRotationYaw = savedPrevYawForTickSwap;
        minecraft.thePlayer.rotationPitch = savedPitchForTickSwap;
        minecraft.thePlayer.prevRotationPitch = savedPrevPitchForTickSwap;
        swappedForTickSwap = false;
    }

    public void updateSilentYaw(float yaw, float pitch) {
        serverYaw = Float.valueOf(yaw);
        serverPitch = Float.valueOf(pitch);
        setRotations = true;
        silentRotation = true;
    }

    private boolean prePlayerInteractPosted;
    public boolean tryClaimPrePlayerInteractPost() {
        if (prePlayerInteractPosted) return false;
        prePlayerInteractPosted = true;
        return true;
    }

    public void onLivingUpdatePre(Entity entity) {
        if (entity == null || entity != minecraft.thePlayer || !setRotations || !silentRotation) {
            return;
        }
        if (serverYaw == null || serverYaw.isNaN()) return;

        float yaw   = serverYaw.floatValue();
        float pitch = (serverPitch != null && !serverPitch.isNaN())
                ? serverPitch.floatValue()
                : entity.rotationPitch;

        savedYawForLiving      = entity.rotationYaw;
        savedPrevYawForLiving  = entity.prevRotationYaw;
        savedPitchForLiving    = entity.rotationPitch;
        savedPrevPitchForLiving = entity.prevRotationPitch;

        entity.rotationYaw     = yaw;
        entity.prevRotationYaw = yaw;
        entity.rotationPitch     = pitch;
        entity.prevRotationPitch = pitch;

        swappedForLivingUpdate = true;
    }

    public void onLivingUpdatePost(Entity entity) {
        if (!swappedForLivingUpdate || entity == null) return;

        entity.rotationYaw      = savedYawForLiving;
        entity.prevRotationYaw  = savedPrevYawForLiving;
        entity.rotationPitch     = savedPitchForLiving;
        entity.prevRotationPitch = savedPrevPitchForLiving;

        swappedForLivingUpdate = false;
    }

    public void onGetMouseOverPre(Entity entity) {
        if (entity == null || entity != minecraft.thePlayer || !setRotations || !silentRotation) {
            return;
        }
        if (serverYaw == null || serverYaw.isNaN()) return;

        float yaw   = serverYaw.floatValue();
        float pitch = (serverPitch != null && !serverPitch.isNaN())
                ? serverPitch.floatValue()
                : entity.rotationPitch;

        savedYawForMouseOver      = entity.rotationYaw;
        savedPrevYawForMouseOver  = entity.prevRotationYaw;
        savedPitchForMouseOver    = entity.rotationPitch;
        savedPrevPitchForMouseOver = entity.prevRotationPitch;

        entity.rotationYaw       = yaw;
        entity.prevRotationYaw   = yaw;
        entity.rotationPitch     = pitch;
        entity.prevRotationPitch = pitch;

        swappedForMouseOver = true;
    }

    public void onGetMouseOverPost(Entity entity) {
        if (!swappedForMouseOver || entity == null) return;

        entity.rotationYaw      = savedYawForMouseOver;
        entity.prevRotationYaw  = savedPrevYawForMouseOver;
        entity.rotationPitch     = savedPitchForMouseOver;
        entity.prevRotationPitch = savedPrevPitchForMouseOver;

        swappedForMouseOver = false;
    }

    public boolean isSilentRotationRequested() {
        return silentRotation;
    }

    public void clearRequestedRotations() {
        serverYaw = null;
        serverPitch = null;
        setRotations = false;
    }

    public void updateServerRotations() {
        if (minecraft.thePlayer == null || rotationsUpdatedThisTick) {
            return;
        }

        rotationsUpdatedThisTick = true;
        if (Float.isNaN(KillAuraRotationUtils.serverRotations[0])) {
            KillAuraRotationUtils.serverRotations[0] = minecraft.thePlayer.rotationYaw;
            KillAuraRotationUtils.serverRotations[1] = minecraft.thePlayer.rotationPitch;
        }

        ClientRotationEvent event = new ClientRotationEvent(serverYaw, serverPitch);
        MinecraftForge.EVENT_BUS.post(event);
        serverYaw = event.yaw;
        serverPitch = event.pitch;
        if (event.silent) {
            silentRotation = true;
        }
        if (serverYaw == null && serverPitch == null) {
            return;
        }

        float baseYaw = Float.isNaN(KillAuraRotationUtils.serverRotations[0]) ? minecraft.thePlayer.rotationYaw : KillAuraRotationUtils.serverRotations[0];
        float basePitch = Float.isNaN(KillAuraRotationUtils.serverRotations[1]) ? minecraft.thePlayer.rotationPitch : KillAuraRotationUtils.serverRotations[1];
        float[] fixed = KillAuraRotationUtils.fixRotation(
            serverYaw == null ? minecraft.thePlayer.rotationYaw : serverYaw.floatValue(),
            serverPitch == null ? minecraft.thePlayer.rotationPitch : serverPitch.floatValue(),
            baseYaw,
            basePitch
        );
        if (serverYaw != null) {
            serverYaw = Float.valueOf(fixed[0]);
        }
        if (serverPitch != null) {
            serverPitch = Float.valueOf(fixed[1]);
        }
        if ((serverYaw != null && !serverYaw.isNaN() && serverYaw.floatValue() != minecraft.thePlayer.rotationYaw)
            || (serverPitch != null && !serverPitch.isNaN() && serverPitch.floatValue() != minecraft.thePlayer.rotationPitch)) {
            setRotations = true;
        }
    }

    public void onWalkingUpdatePre(Entity entity) {
        if (entity == null || minecraft.thePlayer == null || entity != minecraft.thePlayer) {
            return;
        }

        if (setRotations) {
            float yaw = serverYaw != null && !serverYaw.isNaN() ? serverYaw.floatValue() : entity.rotationYaw;
            float pitch = serverPitch != null && !serverPitch.isNaN() ? serverPitch.floatValue() : entity.rotationPitch;
            beginSwap(entity, yaw, pitch, true);
            swappedForWalkingUpdate = true;
            KillAuraRotationUtils.serverRotations[0] = yaw;
            KillAuraRotationUtils.serverRotations[1] = pitch;
            return;
        }

        KillAuraRotationUtils.serverRotations[0] = entity.rotationYaw;
        KillAuraRotationUtils.serverRotations[1] = entity.rotationPitch;
    }

    public boolean isActive() {
        return setRotations && (serverYaw != null || serverPitch != null);
    }

    public Float getServerYaw() {
        return serverYaw;
    }

    public Float getServerPitch() {
        return serverPitch;
    }

    public void beginSwap(Entity entity, float yaw, float pitch, boolean swapPitch) {
        savedYaw = entity.rotationYaw;
        savedPrevYaw = entity.prevRotationYaw;
        savedPitch = entity.rotationPitch;
        savedPrevPitch = entity.prevRotationPitch;

        entity.rotationYaw = yaw;
        entity.prevRotationYaw = yaw;
        if (swapPitch) {
            entity.rotationPitch = pitch;
            entity.prevRotationPitch = pitch;
        }
    }

    public void endSwap(Entity entity) {
        entity.rotationYaw = savedYaw;
        entity.prevRotationYaw = savedPrevYaw;
        entity.rotationPitch = savedPitch;
        entity.prevRotationPitch = savedPrevPitch;
    }

    public void onWalkingUpdatePost(Entity entity) {
        if (!swappedForWalkingUpdate || entity == null) {
            return;
        }

        endSwap(entity);
        swappedForWalkingUpdate = false;
    }

    @SubscribeEvent
    public void onPlayerInput(PrePlayerInputEvent event) {
        if (!silentMoveFix || !canFixMovement()) {
            return;
        }

        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }

        float visualYaw = resolveVisualYaw();
        float sneakMultiplier = event.isSneak() ? 0.3F : 1.0F;
        double intendedAngle = Math.toDegrees(getDirection(visualYaw, forward, strafe));
        float closestForward = forward;
        float closestStrafe = strafe;
        double closestDifference = Double.MAX_VALUE;

        for (float predictedForwardRaw = -1.0F; predictedForwardRaw <= 1.0F; predictedForwardRaw += 1.0F) {
            for (float predictedStrafeRaw = -1.0F; predictedStrafeRaw <= 1.0F; predictedStrafeRaw += 1.0F) {
                if (predictedForwardRaw == 0.0F && predictedStrafeRaw == 0.0F) {
                    continue;
                }

                float predictedForward = predictedForwardRaw * sneakMultiplier;
                float predictedStrafe = predictedStrafeRaw * sneakMultiplier;
                double predictedAngle = Math.toDegrees(getDirection(serverYaw.floatValue(), predictedForward, predictedStrafe));
                double difference = Math.abs(MathHelper.wrapAngleTo180_double(intendedAngle - predictedAngle));
                if (difference < closestDifference) {
                    closestDifference = difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        event.setForward(closestForward);
        event.setStrafe(closestStrafe);
    }

    private float resolveVisualYaw() {
        if (swappedForTickSwap) {
            return savedYawForTickSwap;
        }
        if (swappedForLivingUpdate) {
            return savedYawForLiving;
        }
        if (swappedForWalkingUpdate) {
            return savedYaw;
        }
        return minecraft.thePlayer != null ? minecraft.thePlayer.rotationYaw : 0.0F;
    }

    private boolean canFixMovement() {
        return setRotations && serverYaw != null && !serverYaw.isNaN();
    }

    private static double getDirection(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0D) {
            rotationYaw += 180.0F;
        }

        float forward = 1.0F;
        if (moveForward < 0.0D) {
            forward = -0.5F;
        } else if (moveForward > 0.0D) {
            forward = 0.5F;
        }

        if (moveStrafing > 0.0D) {
            rotationYaw -= 90.0F * forward;
        }
        if (moveStrafing < 0.0D) {
            rotationYaw += 90.0F * forward;
        }

        return Math.toRadians(rotationYaw);
    }
}
