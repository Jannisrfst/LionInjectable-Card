package lion.client.agent;

import lion.client.ClientLogger;
import lion.client.deobf.LionNotchRemapper;
import lion.client.deobf.NotchMapping;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LionVanillaTransformer implements ClassFileTransformer {

    private static final String MAPPING_RESOURCE = "/lion/mappings/mcp-notch.srg";

    private final NotchMapping mapping;
    private final LionNotchRemapper remapper;

    public LionVanillaTransformer() {
        NotchMapping m = NotchMapping.loadFromResource(MAPPING_RESOURCE);
        this.mapping = m;
        this.remapper = (m != null) ? new LionNotchRemapper(m) : null;
        if (m == null) {
            ClientLogger.error("[LionVanilla] " + MAPPING_RESOURCE
                    + " not on classpath — vanilla remap disabled", null);
        }
    }

    public boolean isReady() { return remapper != null; }

    public NotchMapping getMapping() { return mapping; }

    @Override
    public byte[] transform(final ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (classfileBuffer == null || className == null || remapper == null) return null;
        if (!isOurs(className)) return null;
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(0);

            final Map<String, String> methodOverrides = new HashMap<>();
            final Map<String, String> fieldOverrides  = new HashMap<>();
            final String[] currentClassName = new String[1];

            Remapper perClass = new Remapper() {
                @Override public String map(String name) { return remapper.map(name); }
                @Override public String mapMethodName(String owner, String name, String desc) {
                    if (currentClassName[0] != null && currentClassName[0].equals(owner)) {
                        String r = methodOverrides.get(name + " " + desc);
                        if (r != null) return r;
                    }
                    String byOwner = remapper.mapMethodName(owner, name, desc);
                    if (!name.equals(byOwner)) return byOwner;
                    String inherited = lookupInheritedMethod(loader, mapping, owner, name, desc);
                    return inherited != null ? inherited : name;
                }
                @Override public String mapFieldName(String owner, String name, String desc) {
                    if (currentClassName[0] != null && currentClassName[0].equals(owner)) {
                        String r = fieldOverrides.get(name);
                        if (r != null) return r;
                    }
                    String byOwner = remapper.mapFieldName(owner, name, desc);
                    if (!name.equals(byOwner)) return byOwner;
                    String inherited = lookupInheritedField(loader, mapping, owner, name);
                    return inherited != null ? inherited : name;
                }
                @Override public String mapInvokeDynamicMethodName(String name, String desc) {
                    return remapper.mapInvokeDynamicMethodName(name, desc);
                }
            };

            ClassRemapper cr = new ClassRemapper(writer, perClass) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    currentClassName[0] = name;
                    populateOverrideRenames(loader, mapping, superName, interfaces,
                            methodOverrides, fieldOverrides);
                    super.visit(version, access, name, signature, superName, interfaces);
                }
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc,
                                                 String sig, String[] exceptions) {
                    String renamed = methodOverrides.get(name + " " + desc);
                    if (renamed != null) {
                        return super.visitMethod(access, renamed, desc, sig, exceptions);
                    }
                    return super.visitMethod(access, name, desc, sig, exceptions);
                }
            };
            reader.accept(cr, 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            ClientLogger.error("[LionVanilla] remap of " + className + " threw", t);
            return null;
        }
    }

    private static void populateOverrideRenames(ClassLoader loader, NotchMapping mapping,
                                                String superName, String[] interfaces,
                                                Map<String, String> methodOut,
                                                Map<String, String> fieldOut) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        if (superName != null) queue.add(superName);
        if (interfaces != null) {
            for (String i : interfaces) queue.add(i);
        }
        ClassLoader cl = loader != null ? loader : LionVanillaTransformer.class.getClassLoader();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur == null || !visited.add(cur)) continue;
            String mcpOwner = mapping.notchToMcp.getOrDefault(cur, cur);
            Map<String, String> methodsHere = mapping.methodsByOwner.get(mcpOwner);
            if (methodsHere != null) {
                for (Map.Entry<String, String> e : methodsHere.entrySet()) {
                    methodOut.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            Map<String, String> fieldsHere = mapping.fieldsByOwner.get(mcpOwner);
            if (fieldsHere != null) {
                for (Map.Entry<String, String> e : fieldsHere.entrySet()) {
                    fieldOut.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            try {
                String notchCur = mapping.classToNotch.getOrDefault(cur, cur);
                Class<?> cls = Class.forName(notchCur.replace('/', '.'), false, cl);
                Class<?> sup = cls.getSuperclass();
                if (sup != null && sup != Object.class) {
                    queue.add(sup.getName().replace('.', '/'));
                }
                for (Class<?> i : cls.getInterfaces()) {
                    queue.add(i.getName().replace('.', '/'));
                }
            } catch (Throwable ignored) {}
        }
    }

    private static String lookupInheritedField(ClassLoader loader, NotchMapping mapping,
                                                String owner, String name) {
        if (!owner.startsWith("net/minecraft/")) return null;
        ClassLoader cl = loader != null ? loader : LionVanillaTransformer.class.getClassLoader();
        Set<String> visited = new HashSet<>();
        String cur = owner;
        int safety = 32;
        while (cur != null && !cur.equals("java/lang/Object") && visited.add(cur) && safety-- > 0) {
            try {
                String notchCur = mapping.classToNotch.getOrDefault(cur, cur);
                Class<?> cls = Class.forName(notchCur.replace('/', '.'), false, cl);
                Class<?> sup = cls.getSuperclass();
                if (sup == null) return null;
                String notchSup = sup.getName().replace('.', '/');
                String mcpSup = mapping.notchToMcp.getOrDefault(notchSup, notchSup);
                Map<String, String> fieldsHere = mapping.fieldsByOwner.get(mcpSup);
                if (fieldsHere != null) {
                    String hit = fieldsHere.get(name);
                    if (hit != null) return hit;
                }
                cur = mcpSup;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static String lookupInheritedMethod(ClassLoader loader, NotchMapping mapping,
                                                 String owner, String name, String desc) {
        if (!owner.startsWith("net/minecraft/")) return null;
        ClassLoader cl = loader != null ? loader : LionVanillaTransformer.class.getClassLoader();
        Set<String> visited = new HashSet<>();
        String nameDesc = name + " " + desc;
        String cur = owner;
        int safety = 32;
        while (cur != null && !cur.equals("java/lang/Object") && visited.add(cur) && safety-- > 0) {
            try {
                String notchCur = mapping.classToNotch.getOrDefault(cur, cur);
                Class<?> cls = Class.forName(notchCur.replace('/', '.'), false, cl);
                Class<?> sup = cls.getSuperclass();
                if (sup == null) return null;
                String notchSup = sup.getName().replace('.', '/');
                String mcpSup = mapping.notchToMcp.getOrDefault(notchSup, notchSup);
                Map<String, String> methodsHere = mapping.methodsByOwner.get(mcpSup);
                if (methodsHere != null) {
                    String hit = methodsHere.get(nameDesc);
                    if (hit != null) return hit;
                }
                cur = mcpSup;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static boolean isOurs(String internalName) {
        if (internalName.startsWith("com/lionclient/")) return true;
        if (internalName.startsWith("net/minecraftforge/")) return true;
        if (!internalName.startsWith("lion/client/")) return false;
        if (internalName.equals("lion/client/agent/LionAgent")) return false;
        if (internalName.equals("lion/client/agent/LionTransformer")) return false;
        if (internalName.equals("lion/client/agent/LionDeobfTransformer")) return false;
        if (internalName.equals("lion/client/agent/LionVanillaTransformer")) return false;
        if (internalName.startsWith("lion/client/agent/LionDeobfTransformer$")) return false;
        if (internalName.startsWith("lion/client/deobf/")) return false;
        if (internalName.equals("lion/client/ClientLogger")) return false;
        if (internalName.equals("lion/client/NativeBridge")) return false;
        if (internalName.equals("lion/client/BridgeClassLoader")) return false;
        if (internalName.equals("lion/client/Agent")) return false;
        if (internalName.startsWith("lion/client/hook/")) return false;
        return true;
    }
}
