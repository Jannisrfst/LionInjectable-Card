package lion.client.agent;

import com.lionclient.combat.ClientRotationHelper;
import com.lionclient.event.PrePlayerInteractEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class Hooks {

    private Hooks() {}

    private static final boolean[] PREV_MOUSE_DOWN = new boolean[16];
    private static int prevMouseWheel;

    public static void onMinecraftRunTickStart() {
        try { ClientRotationHelper.get().updateServerRotations(); }
        catch (Throwable t) { logOnce("ClientRotationHelper.updateServerRotations", t); }

        try { ClientRotationHelper.get().onRunTickStart(); }
        catch (Throwable t) { logOnce("ClientRotationHelper.onRunTickStart", t); }

        try {
            MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        } catch (Throwable t) {
            logOnce("runTick(START) post", t);
        }

        try {
            if (ClientRotationHelper.get().tryClaimPrePlayerInteractPost()) {
                MinecraftForge.EVENT_BUS.post(new PrePlayerInteractEvent());
            }
        } catch (Throwable t) {
            logOnce("PrePlayerInteract post", t);
        }

        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                ClientRotationHelper.get().applyTickSwap(mc.thePlayer);
            }
        } catch (Throwable t) { logOnce("applyTickSwap", t); }
    }

    public static void onMinecraftRunTickEnd() {
        try { pollMouseAndPost(); }
        catch (Throwable t) { logOnce("pollMouseAndPost", t); }

        try {
            MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        } catch (Throwable t) {
            logOnce("runTick(END) post", t);
        }

        try { ClientRotationHelper.get().restoreTickSwap(); }
        catch (Throwable t) { logOnce("ClientRotationHelper.restoreTickSwap", t); }

        try { ClientRotationHelper.get().endOfTickReset(); }
        catch (Throwable t) { logOnce("ClientRotationHelper.endOfTickReset", t); }
    }

    public static void onRenderWorldLast(float partialTicks) {
        try {
            MinecraftForge.EVENT_BUS.post(new TickEvent.RenderTickEvent(TickEvent.Phase.END, partialTicks));
        } catch (Throwable t) {
            logOnce("renderTick post", t);
        }

        try {
            Minecraft mc = Minecraft.getMinecraft();
            EntityRenderer er = (mc != null) ? mc.entityRenderer : null;
            if (er != null && mc.displayHeight > 0) {
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try {
                    invokeSetupCameraTransform(er, partialTicks);
                    GlStateManager.enableDepth();
                    MinecraftForge.EVENT_BUS.post(new RenderWorldLastEvent(null, partialTicks));
                } finally {
                    GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                    GlStateManager.matrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                    GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                }
            } else {
                MinecraftForge.EVENT_BUS.post(new RenderWorldLastEvent(null, partialTicks));
            }
        } catch (Throwable t) {
            logOnce("renderWorldLast post", t);
        }
    }

    public static void onClickMouse() {
        try { postMouseEvent(0, true, 0); }
        catch (Throwable t) { logOnce("onClickMouse post", t); }
    }

    public static void onRightClickMouse() {
        try { postMouseEvent(1, true, 0); }
        catch (Throwable t) { logOnce("onRightClickMouse post", t); }
    }

    public static void onClickMouseAttackOverride() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return;
            com.lionclient.feature.module.impl.KillAuraModule ka =
                    com.lionclient.feature.module.impl.KillAuraModule.getInstance();
            if (ka == null || !ka.isEnabled()) return;
            ClientRotationHelper helper = ClientRotationHelper.get();
            helper.onGetMouseOverPre(mc.thePlayer);
            try {
                ka.modifyMouseOverFromGetMouseOver(1.0F);
            } finally {
                helper.onGetMouseOverPost(mc.thePlayer);
            }
        } catch (Throwable t) { logOnce("onClickMouseAttackOverride", t); }
    }

    public static void onWalkingUpdatePre(Object entity) {
        try {
            if (entity instanceof net.minecraft.entity.Entity) {
                ClientRotationHelper.get().updateServerRotations();
                ClientRotationHelper.get().onRunTickStart();
                ClientRotationHelper.get().onWalkingUpdatePre((net.minecraft.entity.Entity) entity);
            }
        } catch (Throwable t) { logOnce("onWalkingUpdatePre", t); }
    }

    public static void onWalkingUpdatePost(Object entity) {
        try {
            if (entity instanceof net.minecraft.entity.Entity) {
                ClientRotationHelper.get().onWalkingUpdatePost((net.minecraft.entity.Entity) entity);
            }
        } catch (Throwable t) { logOnce("onWalkingUpdatePost", t); }
    }

    public static void onLivingUpdatePre(Object entity) {
        try {
            if (entity instanceof net.minecraft.entity.Entity) {
                ClientRotationHelper.get().updateServerRotations();
                ClientRotationHelper.get().onLivingUpdatePre((net.minecraft.entity.Entity) entity);
            }
        } catch (Throwable t) { logOnce("onLivingUpdatePre", t); }
    }

    public static void onLivingUpdatePost(Object entity) {
        try {
            if (entity instanceof net.minecraft.entity.Entity) {
                ClientRotationHelper.get().onLivingUpdatePost((net.minecraft.entity.Entity) entity);
            }
        } catch (Throwable t) { logOnce("onLivingUpdatePost", t); }
    }

    public static void onGetMouseOverPre() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                ClientRotationHelper.get().onGetMouseOverPre(mc.thePlayer);
            }
        } catch (Throwable t) { logOnce("onGetMouseOverPre", t); }
    }

    public static void onGetMouseOverPost(float partialTicks) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return;

            try {
                com.lionclient.feature.module.impl.KillAuraModule killAura =
                        com.lionclient.feature.module.impl.KillAuraModule.getInstance();
                if (killAura != null && killAura.isEnabled()) {
                    killAura.modifyMouseOverFromGetMouseOver(partialTicks);
                }
            } catch (Throwable t) { logOnce("KillAura.modifyMouseOver", t); }

            try {
                com.lionclient.feature.module.impl.AntiFireballModule antiFireball =
                        com.lionclient.feature.module.impl.AntiFireballModule.getInstance();
                if (antiFireball != null && antiFireball.isEnabled()) {
                    antiFireball.modifyMouseOverFromGetMouseOver(partialTicks);
                }
            } catch (Throwable t) { logOnce("AntiFireball.modifyMouseOver", t); }

            ClientRotationHelper.get().onGetMouseOverPost(mc.thePlayer);
        } catch (Throwable t) { logOnce("onGetMouseOverPost", t); }
    }

    public static boolean onSetAngles(Object entity, float yawDelta, float pitchDelta) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || entity != mc.thePlayer) {
                return false;
            }
            com.lionclient.feature.module.impl.FreeLookModule freeLook =
                    com.lionclient.feature.module.impl.FreeLookModule.getInstance();
            if (freeLook != null && freeLook.isActive()) {
                freeLook.applyMouseDelta(yawDelta, pitchDelta);
                return true;
            }
        } catch (Throwable t) {
            logOnce("onSetAngles", t);
        }
        return false;
    }

    public static void onOrientCameraPre() {
        try {
            com.lionclient.feature.module.impl.FreeLookModule freeLook =
                    com.lionclient.feature.module.impl.FreeLookModule.getInstance();
            if (freeLook != null && freeLook.isEnabled()) {
                freeLook.cameraPre();
            }
        } catch (Throwable t) {
            logOnce("onOrientCameraPre", t);
        }
    }

    public static void onOrientCameraPost() {
        try {
            com.lionclient.feature.module.impl.FreeLookModule freeLook =
                    com.lionclient.feature.module.impl.FreeLookModule.getInstance();
            if (freeLook != null && freeLook.isEnabled()) {
                freeLook.cameraPost();
            }
        } catch (Throwable t) {
            logOnce("onOrientCameraPost", t);
        }
    }

    public static boolean shouldCancelVanillaNametag(Object entity) {
        try {
            com.lionclient.feature.module.impl.NametagsModule nametags =
                    com.lionclient.feature.module.impl.NametagsModule.getInstance();
            return nametags != null && nametags.shouldHideVanillaNametags();
        } catch (Throwable t) {
            logOnce("shouldCancelVanillaNametag", t);
            return false;
        }
    }

    public static void onRenderHud(float partialTicks) {
        try {
            ScaledResolution res;
            try { res = new ScaledResolution(Minecraft.getMinecraft()); }
            catch (Throwable t) { res = null; }

            RenderGameOverlayEvent parent = new RenderGameOverlayEvent(partialTicks, res);
            MinecraftForge.EVENT_BUS.post(
                    new RenderGameOverlayEvent.Text(parent,
                            new java.util.ArrayList<String>(),
                            new java.util.ArrayList<String>()));
        } catch (Throwable t) {
            logOnce("renderHud post", t);
        }
    }

    private static void pollMouseAndPost() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.inGameHasFocus || mc.currentScreen != null) {
            for (int i = 0; i < PREV_MOUSE_DOWN.length; i++) PREV_MOUSE_DOWN[i] = false;
            prevMouseWheel = 0;
            return;
        }
        if (!Mouse.isCreated()) return;

        int max = Math.min(Mouse.getButtonCount(), PREV_MOUSE_DOWN.length);
        for (int btn = 0; btn < max; btn++) {
            boolean down;
            try { down = Mouse.isButtonDown(btn); }
            catch (Throwable t) { continue; }
            if (down != PREV_MOUSE_DOWN[btn]) {
                postMouseEvent(btn, down, 0);
                PREV_MOUSE_DOWN[btn] = down;
            }
        }

        int wheel;
        try { wheel = Mouse.getDWheel(); }
        catch (Throwable t) { wheel = 0; }
        if (wheel != 0) postMouseEvent(-1, false, wheel);
    }

    private static void postMouseEvent(int button, boolean state, int dwheel) {
        MouseEvent event = new MouseEvent();
        ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, Integer.valueOf(button), "button");
        ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, Boolean.valueOf(state), "buttonstate");
        if (dwheel != 0) {
            ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, Integer.valueOf(dwheel), "dwheel");
        }
        try { MinecraftForge.EVENT_BUS.post(event); }
        catch (Throwable t) { logOnce("MouseEvent.post", t); }
    }

    private static volatile java.lang.reflect.Method SETUP_CAMERA_TRANSFORM;
    private static void invokeSetupCameraTransform(EntityRenderer er, float partialTicks) throws Throwable {
        java.lang.reflect.Method m = SETUP_CAMERA_TRANSFORM;
        if (m == null) {
            java.util.List<String> names = new java.util.ArrayList<String>();
            names.add("setupCameraTransform");
            names.add("func_78479_a");
            try {
                lion.client.deobf.NotchMapping nm = lion.client.agent.LionAgent.NOTCH_MAPPING;
                if (nm != null) {
                    String notch = nm.lookupMethodName(
                            "net/minecraft/client/renderer/EntityRenderer",
                            "setupCameraTransform", "(FI)V");
                    if (notch != null) names.add(notch);
                }
            } catch (Throwable ignored) {}
            for (String n : names) {
                try {
                    m = er.getClass().getDeclaredMethod(n, float.class, int.class);
                    m.setAccessible(true);
                    SETUP_CAMERA_TRANSFORM = m;
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (m == null) {
                for (String n : names) {
                    try {
                        m = EntityRenderer.class.getDeclaredMethod(n, float.class, int.class);
                        m.setAccessible(true);
                        SETUP_CAMERA_TRANSFORM = m;
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            }
        }
        if (m == null) throw new NoSuchMethodException("setupCameraTransform");
        m.invoke(er, partialTicks, Integer.valueOf(0));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean onChannelRead0(Object networkManager, Object ctx, Object packet) {
        try {
            Object listener = resolvePacketListener(networkManager);
            if (!(listener instanceof net.minecraft.client.network.NetHandlerPlayClient)) {
                return false;
            }

            com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
            if (client == null) {
                return false;
            }

            com.lionclient.feature.module.impl.KnockbackDelayModule kbd =
                    client.getModuleManager().getModule(com.lionclient.feature.module.impl.KnockbackDelayModule.class);
            if (kbd != null
                    && kbd.isEnabled()
                    && !kbd.isHolding()
                    && packet instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
                net.minecraft.network.play.server.S12PacketEntityVelocity motion =
                        (net.minecraft.network.play.server.S12PacketEntityVelocity) packet;
                if (com.lionclient.feature.module.impl.KnockbackDelayModule.cachedPlayerId != -1
                        && motion.getEntityID() == com.lionclient.feature.module.impl.KnockbackDelayModule.cachedPlayerId) {
                    int chance = kbd.getChance().getValue();
                    if (chance >= 100 || (int) (Math.random() * 100) < chance) {
                        kbd.triggerDelay(com.lionclient.feature.module.impl.KnockbackDelayModule.cachedOnGround);
                    }
                }
            }

            com.lionclient.network.KnockbackDelayBuffer buffer = client.getKnockbackDelayBuffer();
            if (buffer.shouldBufferIncoming()) {
                final net.minecraft.network.Packet typedPacket = (net.minecraft.network.Packet) packet;
                final net.minecraft.network.INetHandler finalListener =
                        (net.minecraft.network.INetHandler) listener;
                buffer.bufferIncoming(new Runnable() {
                    @Override
                    public void run() {
                        typedPacket.processPacket(finalListener);
                    }
                });
                return true;
            }
        } catch (Throwable t) {
            logOnce("onChannelRead0", t);
        }
        return false;
    }

    public static void onUpdatePlayerMoveState(Object inputObj) {
        try {
            if (!(inputObj instanceof net.minecraft.util.MovementInput)) {
                return;
            }
            net.minecraft.util.MovementInput input = (net.minecraft.util.MovementInput) inputObj;

            com.lionclient.event.PrePlayerInputEvent event =
                    new com.lionclient.event.PrePlayerInputEvent(
                            input.moveForward, input.moveStrafe, input.jump, input.sneak);
            MinecraftForge.EVENT_BUS.post(event);
            input.moveForward = event.getForward();
            input.moveStrafe = event.getStrafe();
            input.jump = event.isJump();
            input.sneak = event.isSneak();
        } catch (Throwable t) {
            logOnce("onUpdatePlayerMoveState", t);
        }
    }

    public static boolean onSendPacket(Object networkManager, Object packet) {
        try {
            if (!(packet instanceof net.minecraft.network.Packet)) {
                return false;
            }

            com.lionclient.event.SendPacketEvent event =
                    new com.lionclient.event.SendPacketEvent((net.minecraft.network.Packet<?>) packet);
            MinecraftForge.EVENT_BUS.post(event);
            return event.isCanceled();
        } catch (Throwable t) {
            logOnce("onSendPacket", t);
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resolvePacketListener(Object networkManager) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(
                    (Class) net.minecraft.network.NetworkManager.class,
                    networkManager,
                    "field_150744_m",
                    "packetListener"
            );
        } catch (Throwable t) {
            logOnce("resolvePacketListener", t);
            return null;
        }
    }

    private static final java.util.Set<String> LOGGED = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static void logOnce(String where, Throwable t) {
        if (!LOGGED.add(where)) return;
        try { lion.client.ClientLogger.error("[Hooks] " + where + " threw", t); }
        catch (Throwable ignored) {}
    }
}
