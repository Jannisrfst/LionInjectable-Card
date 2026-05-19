package lion.client.agent;

import lion.client.deobf.NotchMapping;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LionTransformer implements ClassFileTransformer {

    private static final String HOOKS = "lion/client/agent/Hooks";

    private static final String MINECRAFT_INT       = "net/minecraft/client/Minecraft";
    private static final String ENTITY_RENDERER_INT = "net/minecraft/client/renderer/EntityRenderer";
    private static final String GUI_INGAME_INT      = "net/minecraft/client/gui/GuiIngame";
    private static final String ENTITY_PLAYER_SP_INT = "net/minecraft/client/entity/EntityPlayerSP";
    private static final String NETWORK_MANAGER_INT  = "net/minecraft/network/NetworkManager";
    private static final String MOVEMENT_INPUT_FROM_OPTIONS_INT = "net/minecraft/util/MovementInputFromOptions";

    private static final String[] MCP_TARGETS = {
            MINECRAFT_INT, ENTITY_RENDERER_INT, GUI_INGAME_INT, ENTITY_PLAYER_SP_INT,
            NETWORK_MANAGER_INT, MOVEMENT_INPUT_FROM_OPTIONS_INT
    };

    private static volatile Set<String> TARGET_NAMES_INTERNAL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(MCP_TARGETS)));

    private static volatile Map<String, String> NOTCH_TO_MCP_CLASS = Collections.emptyMap();

    private static volatile Map<String, String> NOTCH_METHOD_NAMES = Collections.emptyMap();

    public static volatile boolean ON_FORGE;

    public static volatile Set<String> TARGET_NAMES_DOTTED;
    static {
        Set<String> s = new HashSet<>();
        for (String n : TARGET_NAMES_INTERNAL) s.add(n.replace('/', '.'));
        TARGET_NAMES_DOTTED = Collections.unmodifiableSet(s);
    }

    public static synchronized void setVanillaMappings(NotchMapping mapping) {
        if (mapping == null) return;
        Set<String> internal = new HashSet<>(Arrays.asList(MCP_TARGETS));
        Map<String, String> notchToMcp = new HashMap<>();
        for (String mcp : MCP_TARGETS) {
            String notch = mapping.classToNotch.get(mcp);
            if (notch != null) {
                internal.add(notch);
                notchToMcp.put(notch, mcp);
            }
        }
        TARGET_NAMES_INTERNAL = Collections.unmodifiableSet(internal);
        Set<String> dotted = new HashSet<>();
        for (String n : internal) dotted.add(n.replace('/', '.'));
        TARGET_NAMES_DOTTED = Collections.unmodifiableSet(dotted);
        NOTCH_TO_MCP_CLASS  = Collections.unmodifiableMap(notchToMcp);

        Map<String, String> hookSigs = new HashMap<>();
        addHookLookup(hookSigs, mapping, MINECRAFT_INT, "runTick", "()V");
        addHookLookup(hookSigs, mapping, MINECRAFT_INT, "clickMouse", "()V");
        addHookLookup(hookSigs, mapping, MINECRAFT_INT, "rightClickMouse", "()V");
        addHookLookup(hookSigs, mapping, ENTITY_RENDERER_INT, "renderWorldPass", "(IFJ)V");
        addHookLookup(hookSigs, mapping, ENTITY_RENDERER_INT, "getMouseOver", "(F)V");
        addHookLookup(hookSigs, mapping, GUI_INGAME_INT, "renderGameOverlay", "(F)V");
        addHookLookup(hookSigs, mapping, ENTITY_PLAYER_SP_INT, "onUpdateWalkingPlayer", "()V");
        addHookLookup(hookSigs, mapping, ENTITY_PLAYER_SP_INT, "onLivingUpdate", "()V");
        addHookLookup(hookSigs, mapping, MOVEMENT_INPUT_FROM_OPTIONS_INT, "updatePlayerMoveState", "()V");
        NOTCH_METHOD_NAMES = Collections.unmodifiableMap(hookSigs);

    }

    private static void addHookLookup(Map<String, String> out, NotchMapping mapping,
                                      String mcpOwner, String mcpMethod, String desc) {
        String key = mcpOwner + "/" + mcpMethod + " " + desc;
        String notch = mapping.methodToNotch.get(key);
        if (notch != null) out.put(key, notch);
    }

    @Override
    public byte[] transform(final ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !TARGET_NAMES_INTERNAL.contains(className)) return null;
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected ClassLoader getClassLoader() {
                    return loader != null ? loader : super.getClassLoader();
                }
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    try { return super.getCommonSuperClass(type1, type2); }
                    catch (Throwable t) { return "java/lang/Object"; }
                }
            };
            ClassVisitor visitor = chooseVisitor(className, writer);
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            lion.client.ClientLogger.error("[LionTransformer] " + className + " transform threw", t);
            return null;
        }
    }

    private static ClassVisitor chooseVisitor(String name, ClassWriter w) {
        String mcp = NOTCH_TO_MCP_CLASS.getOrDefault(name, name);
        switch (mcp) {
            case MINECRAFT_INT:        return new MinecraftCV(w);
            case ENTITY_RENDERER_INT:  return new EntityRendererCV(w);
            case GUI_INGAME_INT:       return new GuiIngameCV(w);
            case ENTITY_PLAYER_SP_INT: return new EntityPlayerSPCV(w);
            case NETWORK_MANAGER_INT:  return new NetworkManagerCV(w);
            case MOVEMENT_INPUT_FROM_OPTIONS_INT: return new MovementInputFromOptionsCV(w);
            default:                   return w;
        }
    }

    private static boolean nameMatches(String actual, String mcpOwner, String mcpName, String srgName, String desc) {
        if (mcpName.equals(actual) || srgName.equals(actual)) return true;
        String notch = NOTCH_METHOD_NAMES.get(mcpOwner + "/" + mcpName + " " + desc);
        return notch != null && notch.equals(actual);
    }

    private static final class MinecraftCV extends ClassVisitor {
        MinecraftCV(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (!ON_FORGE && nameMatches(name, MINECRAFT_INT, "runTick", "func_71407_l", desc) && "()V".equals(desc)) {
                return new RunTickMV(mv);
            }
            if (nameMatches(name, MINECRAFT_INT, "clickMouse", "func_147116_af", desc) && "()V".equals(desc)) {
                MethodVisitor visitor = new InvokeStaticHeadMV(mv, "onClickMouseAttackOverride");
                if (!ON_FORGE) {
                    visitor = new InvokeStaticHeadMV(visitor, "onClickMouse");
                }
                return visitor;
            }
            if (!ON_FORGE && nameMatches(name, MINECRAFT_INT, "rightClickMouse", "func_147121_ag", desc) && "()V".equals(desc)) {
                return new InvokeStaticHeadMV(mv, "onRightClickMouse");
            }
            return mv;
        }
    }

    private static final class EntityPlayerSPCV extends ClassVisitor {
        EntityPlayerSPCV(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (nameMatches(name, ENTITY_PLAYER_SP_INT, "onUpdateWalkingPlayer", "func_175161_p", desc) && "()V".equals(desc)) {
                return new SwapAroundMV(mv, "onWalkingUpdatePre", "onWalkingUpdatePost");
            }
            if (nameMatches(name, ENTITY_PLAYER_SP_INT, "onLivingUpdate", "func_70636_d", desc) && "()V".equals(desc)) {
                return new SwapAroundMV(mv, "onLivingUpdatePre", "onLivingUpdatePost");
            }
            return mv;
        }
    }

    private static final class SwapAroundMV extends MethodVisitor {
        private final String preName;
        private final String postName;
        SwapAroundMV(MethodVisitor mv, String preName, String postName) {
            super(Opcodes.ASM9, mv);
            this.preName  = preName;
            this.postName = postName;
        }
        @Override
        public void visitCode() {
            super.visitCode();
            super.visitVarInsn(Opcodes.ALOAD, 0);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, preName,
                    "(Ljava/lang/Object;)V", false);
        }
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, postName,
                        "(Ljava/lang/Object;)V", false);
            }
            super.visitInsn(opcode);
        }
    }

    private static final class InvokeStaticHeadMV extends MethodVisitor {
        private final String hookName;
        InvokeStaticHeadMV(MethodVisitor mv, String hookName) {
            super(Opcodes.ASM9, mv);
            this.hookName = hookName;
        }
        @Override
        public void visitCode() {
            super.visitCode();
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, hookName, "()V", false);
        }
    }

    private static final class RunTickMV extends MethodVisitor {
        RunTickMV(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
        @Override
        public void visitCode() {
            super.visitCode();
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onMinecraftRunTickStart", "()V", false);
        }
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onMinecraftRunTickEnd", "()V", false);
            }
            super.visitInsn(opcode);
        }
    }

    private static final class EntityRendererCV extends ClassVisitor {
        EntityRendererCV(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (!ON_FORGE && nameMatches(name, ENTITY_RENDERER_INT, "renderWorldPass", "func_175068_a", desc) && "(IFJ)V".equals(desc)) {
                return new RenderWorldMV(mv,  2);
            }
            if (nameMatches(name, ENTITY_RENDERER_INT, "getMouseOver", "func_78473_a", desc) && "(F)V".equals(desc)) {
                return new GetMouseOverMV(mv);
            }
            return mv;
        }
    }

    private static final class RenderWorldMV extends MethodVisitor {
        private final int partialTicksLocal;
        RenderWorldMV(MethodVisitor mv, int partialTicksLocal) {
            super(Opcodes.ASM9, mv);
            this.partialTicksLocal = partialTicksLocal;
        }
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                super.visitVarInsn(Opcodes.FLOAD, partialTicksLocal);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onRenderWorldLast", "(F)V", false);
            }
            super.visitInsn(opcode);
        }
    }

    private static final class GetMouseOverMV extends MethodVisitor {
        GetMouseOverMV(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
        @Override
        public void visitCode() {
            super.visitCode();
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS,
                    "onGetMouseOverPre", "()V", false);
        }
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                super.visitVarInsn(Opcodes.FLOAD, 1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS,
                        "onGetMouseOverPost", "(F)V", false);
            }
            super.visitInsn(opcode);
        }
    }

    private static final class GuiIngameCV extends ClassVisitor {
        GuiIngameCV(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (!ON_FORGE && nameMatches(name, GUI_INGAME_INT, "renderGameOverlay", "func_175180_a", desc) && "(F)V".equals(desc)) {
                return new RenderOverlayMV(mv);
            }
            return mv;
        }
    }

    private static final class RenderOverlayMV extends MethodVisitor {
        RenderOverlayMV(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                super.visitVarInsn(Opcodes.FLOAD, 1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onRenderHud", "(F)V", false);
            }
            super.visitInsn(opcode);
        }
    }

    private static final class NetworkManagerCV extends ClassVisitor {
        NetworkManagerCV(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if ("channelRead0".equals(name)
                    && "(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V".equals(desc)) {
                return new ChannelRead0MV(mv);
            }
            if (nameMatches(name, NETWORK_MANAGER_INT, "sendPacket", "func_179290_a", desc)
                    && "(Lnet/minecraft/network/Packet;)V".equals(desc)) {
                return new SendPacketMV(mv);
            }
            return mv;
        }
    }

    private static final class ChannelRead0MV extends MethodVisitor {
        ChannelRead0MV(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
        @Override
        public void visitCode() {
            super.visitCode();
            super.visitVarInsn(Opcodes.ALOAD, 0);
            super.visitVarInsn(Opcodes.ALOAD, 1);
            super.visitVarInsn(Opcodes.ALOAD, 2);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onChannelRead0",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            Label continueBody = new Label();
            super.visitJumpInsn(Opcodes.IFEQ, continueBody);
            super.visitInsn(Opcodes.RETURN);
            super.visitLabel(continueBody);
        }
    }

    private static final class SendPacketMV extends MethodVisitor {
        SendPacketMV(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
        @Override
        public void visitCode() {
            super.visitCode();
            super.visitVarInsn(Opcodes.ALOAD, 0);
            super.visitVarInsn(Opcodes.ALOAD, 1);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onSendPacket",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            Label continueBody = new Label();
            super.visitJumpInsn(Opcodes.IFEQ, continueBody);
            super.visitInsn(Opcodes.RETURN);
            super.visitLabel(continueBody);
        }
    }

    private static final class MovementInputFromOptionsCV extends ClassVisitor {
        MovementInputFromOptionsCV(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (nameMatches(name, MOVEMENT_INPUT_FROM_OPTIONS_INT, "updatePlayerMoveState", "func_78898_a", desc)
                    && "()V".equals(desc)) {
                return new UpdatePlayerMoveStateMV(mv);
            }
            return mv;
        }
    }

    private static final class UpdatePlayerMoveStateMV extends MethodVisitor {
        UpdatePlayerMoveStateMV(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onUpdatePlayerMoveState",
                        "(Ljava/lang/Object;)V", false);
            }
            super.visitInsn(opcode);
        }
    }
}
