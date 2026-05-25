package com.lionclient.feature.module.impl;

import com.lionclient.combat.KillAuraRotationUtils;
import com.lionclient.combat.lag.LagDirection;
import com.lionclient.combat.lag.LagHandler;
import com.lionclient.combat.lag.LagRequest;
import com.lionclient.combat.lag.ModuleBackedTimeout;
import com.lionclient.event.PrePlayerInteractEvent;
import com.lionclient.event.SendPacketEvent;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class AutoBlockModule extends Module {
    private static final String VISIBLE_FOR_USERNAME = "LionClient";

    private static AutoBlockModule instance;
    public static AutoBlockModule getInstance() { return instance; }

    @Override
    public boolean isVisible() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getSession() == null) {
            return false;
        }
        return VISIBLE_FOR_USERNAME.equals(mc.getSession().getUsername());
    }

    private final DecimalSetting range = new DecimalSetting("Range", 2.0D, 6.0D, 0.1D, 4.0D);
    private final NumberSetting maxHurtTimeMs = new NumberSetting("Maximum Hurt Time (ms)", 50, 500, 50, 200);
    private final NumberSetting maxHoldMs = new NumberSetting("Maximum Hold Time (ms)", 50, 500, 50, 150);

    private final NumberSetting lagChance = new NumberSetting("Lag Chance (%)", 0, 100, 5, 100);
    private final NumberSetting lagMaxDuration = new NumberSetting("Lag Max Duration (ms)", 50, 500, 50, 200);
    private final BooleanSetting preventDelayAttacks = new BooleanSetting("Prevent delaying attacks", true);
    private final BooleanSetting blockAgainImmediately = new BooleanSetting("Block again immediately", true);

    private final BooleanSetting requireLmb = new BooleanSetting("Require Left mouse", true);
    private final BooleanSetting requireRmb = new BooleanSetting("Require right mouse", false);
    private final BooleanSetting onlyWhenDamaged = new BooleanSetting("Damaged", false);
    private final BooleanSetting ignoreTeammates = new BooleanSetting("Ignore teammates", true);

    private boolean forgeRegistered;
    private boolean isBlocking;
    private boolean manualBlock;
    private int blockStartTick = -1;
    private EntityPlayer currentTarget;
    private int lastSelfHurtTime;

    private boolean isLagging;
    private int lagStartTick = -1;
    private LagRequest outboundLag;

    private int tickCounter;

    public AutoBlockModule() {
        super("Auto Block", "Lag-mode auto block that bundles unblock with the next attack.", Category.COMBAT, Keyboard.KEY_NONE);
        instance = this;
        addSetting(range);
        addSetting(maxHurtTimeMs);
        addSetting(maxHoldMs);
        addSetting(lagChance);
        addSetting(lagMaxDuration);
        addSetting(preventDelayAttacks);
        addSetting(blockAgainImmediately);
        addSetting(requireLmb);
        addSetting(requireRmb);
        addSetting(onlyWhenDamaged);
        addSetting(ignoreTeammates);
    }

    @Override
    protected void onEnable() {
        tickCounter = 0;
        resetState(false);
        registerForge();
    }

    @Override
    protected void onDisable() {
        unregisterForge();
        resetState(true);
    }

    private static int msToTicks(double ms) {
        if (ms <= 0.0D) return 0;
        return (int) Math.ceil(ms / 50.0D);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!nullCheck(mc)) return;
        if (mc.currentScreen != null && (isBlocking || isLagging)) {
            resetState(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent event) {
        if (!isLagging || !preventDelayAttacks.isEnabled()) return;
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
        if (((C02PacketUseEntity) event.getPacket()).getAction() != C02PacketUseEntity.Action.ATTACK) return;

        releaseLag();
        Minecraft mc = Minecraft.getMinecraft();
        if (blockAgainImmediately.isEnabled() && holdingSword(mc)) {
            startBlocking(tickCounter, mc);
        }
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!nullCheck(mc) || mc.thePlayer.isDead || mc.currentScreen != null) {
            resetState(true);
            return;
        }

        int selfHurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = selfHurtTime > lastSelfHurtTime;
        lastSelfHurtTime = selfHurtTime;

        if (!holdingSword(mc)) {
            resetState(false);
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;

        double rangeValue = range.getValue();
        currentTarget = findTarget(mc, rangeValue * rangeValue, ignoreTeammates.isEnabled());
        boolean killAuraAttacking = isKillAuraAttacking();
        boolean rmbDown = Mouse.isButtonDown(1);
        boolean lmbDown = Mouse.isButtonDown(0) || killAuraAttacking;
        boolean autoEngaged = lmbDown && currentTarget != null;

        if (!rmbDown && !autoEngaged) {
            resetState(true);
            return;
        }

        if (!lmbDown) {
            if (isLagging) releaseLag();
            if (!isBlocking) {
                startBlocking(currentTick, mc);
                manualBlock = true;
            }
            return;
        }

        if (manualBlock) {
            stopBlocking(true, mc);
            manualBlock = false;
        }

        boolean hasTarget = currentTarget != null;
        boolean conditionsMet = hasTarget && checkConditions(lmbDown, rmbDown);

        if (isLagging) {
            int lagMaxTicks = msToTicks(lagMaxDuration.getValue());
            boolean lagExpired = lagMaxTicks > 0 && lagStartTick >= 0 && currentTick - lagStartTick >= lagMaxTicks;

            if (lagExpired || !conditionsMet) {
                releaseLag();
                if (lagExpired && blockAgainImmediately.isEnabled() && conditionsMet) {
                    startBlocking(currentTick, mc);
                }
            }
        }

        if (!conditionsMet) {
            stopBlocking(true, mc);
            return;
        }

        if (!isBlocking && !isLagging) {
            boolean shouldStart;
            if (onlyWhenDamaged.isEnabled()) {
                shouldStart = shouldPredictiveBlock(mc);
            } else {
                shouldStart = true;
            }
            if (shouldStart) {
                startBlocking(currentTick, mc);
            }
        }

        if (isBlocking) {
            int maxHoldTicks = msToTicks(maxHoldMs.getValue());
            boolean timeExpired = maxHoldTicks > 0 && blockStartTick >= 0 && currentTick - blockStartTick >= maxHoldTicks;
            boolean shouldStop = timeExpired;
            if (onlyWhenDamaged.isEnabled() && hurtAgain) {
                shouldStop = true;
            }
            if (shouldStop) {
                if (shouldStartLag()) {
                    startLag(currentTick);
                }
                stopBlocking(true, mc);
            }
        }
    }

    private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
        if (requireLmb.isEnabled() && !lmbDown) return false;
        if (requireRmb.isEnabled() && !rmbDown) return false;
        return true;
    }

    private boolean shouldPredictiveBlock(Minecraft mc) {
        int ourHurtTime = mc.thePlayer.hurtTime;
        int triggerTick = (int) Math.round(maxHurtTimeMs.getValue() / 50.0D);
        triggerTick = Math.max(1, Math.min(10, triggerTick));
        return ourHurtTime == triggerTick;
    }

    private void startBlocking(int currentTick, Minecraft mc) {
        if (!holdingSword(mc)) return;
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, true);
        KeyBinding.onTick(keyCode);
        isBlocking = true;
        blockStartTick = currentTick;
    }

    private void stopBlocking(boolean forceRelease, Minecraft mc) {
        if (!isBlocking && !forceRelease) return;
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, false);
        isBlocking = false;
        blockStartTick = -1;
    }

    private boolean shouldStartLag() {
        int chance = lagChance.getValue();
        if (chance <= 0) return false;
        if (chance >= 100) return true;
        return Math.random() * 100.0D < chance;
    }

    private void startLag(int currentTick) {
        if (isLagging) return;
        int lagReferenceTick = blockStartTick >= 0 ? blockStartTick : currentTick;
        int lagMaxTicks = msToTicks(lagMaxDuration.getValue());
        if (lagMaxTicks > 0 && currentTick - lagReferenceTick >= lagMaxTicks) {
            return;
        }
        outboundLag = new LagRequest(LagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        LagHandler.get().requestLag(outboundLag);
        isLagging = true;
        lagStartTick = lagReferenceTick;
    }

    private void releaseLag() {
        if (!isLagging) return;
        if (outboundLag != null) {
            outboundLag.getTimeout().forceTimeOut();
            outboundLag = null;
        }
        isLagging = false;
        lagStartTick = -1;
    }

    public boolean isActive() {
        return isEnabled() && (isBlocking || isLagging);
    }

    private void resetState(boolean releaseUseKey) {
        Minecraft mc = Minecraft.getMinecraft();
        releaseLag();
        stopBlocking(releaseUseKey, mc);
        manualBlock = false;
        if (Mouse.isButtonDown(1) && mc.currentScreen == null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
        currentTarget = null;
        lastSelfHurtTime = 0;
    }

    private static boolean nullCheck(Minecraft mc) {
        return mc != null && mc.thePlayer != null && mc.theWorld != null;
    }

    private static boolean holdingSword(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return false;
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }

    private boolean isKillAuraAttacking() {
        KillAuraModule ka = KillAuraModule.getInstance();
        if (ka == null || !ka.isEnabled()) return false;
        return currentTarget != null;
    }

    private EntityPlayer findTarget(Minecraft mc, double maxDistanceSq, boolean ignoreTeammates) {
        EntityPlayer mouseOver = mouseOverTarget(mc, maxDistanceSq, ignoreTeammates);
        if (mouseOver != null) return mouseOver;
        return closestTarget(mc, maxDistanceSq, ignoreTeammates);
    }

    private EntityPlayer mouseOverTarget(Minecraft mc, double maxDistanceSq, boolean ignoreTeammates) {
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null) return null;
        Entity entity = mop.entityHit;
        if (!(entity instanceof EntityPlayer)) return null;
        EntityPlayer player = (EntityPlayer) entity;
        return isValidTarget(mc, player, maxDistanceSq, ignoreTeammates) ? player : null;
    }

    private EntityPlayer closestTarget(Minecraft mc, double maxDistanceSq, boolean ignoreTeammates) {
        if (mc.theWorld == null) return null;
        EntityPlayer closest = null;
        double closestSq = Double.MAX_VALUE;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (!isValidTarget(mc, p, maxDistanceSq, ignoreTeammates)) continue;
            double dSq = KillAuraRotationUtils.distanceSqFromEyeToClosestOnAABB(p);
            if (dSq < closestSq) {
                closestSq = dSq;
                closest = p;
            }
        }
        return closest;
    }

    private boolean isValidTarget(Minecraft mc, EntityPlayer player, double maxDistanceSq, boolean ignoreTeammates) {
        if (player == null || player == mc.thePlayer || player.isDead || player.deathTime != 0) return false;
        if (player.getHealth() <= 0.0F) return false;
        if (AntiBotModule.shouldIgnore(player)) return false;
        if (ignoreTeammates && isSameTeam(mc, player)) return false;
        double dSq = KillAuraRotationUtils.distanceSqFromEyeToClosestOnAABB(player);
        return dSq <= maxDistanceSq;
    }

    private boolean isSameTeam(Minecraft mc, EntityPlayer other) {
        if (mc.thePlayer == null) return false;
        Team mine = mc.thePlayer.getTeam();
        Team theirs = other.getTeam();
        if (mine == null || theirs == null) return false;
        return mine.isSameTeam(theirs);
    }

    private void registerForge() {
        if (forgeRegistered) return;
        MinecraftForge.EVENT_BUS.register(this);
        forgeRegistered = true;
    }

    private void unregisterForge() {
        if (!forgeRegistered) return;
        MinecraftForge.EVENT_BUS.unregister(this);
        forgeRegistered = false;
    }
}
