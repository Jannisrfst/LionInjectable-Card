package lion.client.agent;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LionDeobfTransformer implements IClassTransformer {

    private static final String MAPPING_RESOURCE = "/lion/mappings/mcp-srg.srg";

    private static volatile Map<String, Map<String, String>> mcpToSrgMethods;
    private static volatile Map<String, Map<String, String>> mcpToSrgFields;
    private static volatile Map<String, String> mcpFieldNameToSrgGlobal;
    private static volatile Map<String, String> mcpMethodSigToSrgGlobal;
    private static volatile boolean lookupTried;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || name == null)  return bytes;
        if (!isOurs(name))                  return bytes;
        if (!ensureMapsLoaded())            return bytes;

        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new RemapCV(writer), 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            try { lion.client.ClientLogger.error("[LionDeobf] remap of " + name + " threw", t); }
            catch (Throwable ignored) {}
            return bytes;
        }
    }

    private static boolean isOurs(String name) {
        return name.startsWith("com.lionclient.") || name.startsWith("lion.client.");
    }

    private static boolean ensureMapsLoaded() {
        if (mcpToSrgMethods != null) return true;
        if (lookupTried) return false;
        synchronized (LionDeobfTransformer.class) {
            if (lookupTried) return mcpToSrgMethods != null;
            lookupTried = true;

            Map<String, Map<String, String>> methods = new HashMap<String, Map<String, String>>();
            Map<String, Map<String, String>> fields  = new HashMap<String, Map<String, String>>();

            InputStream in = LionDeobfTransformer.class.getResourceAsStream(MAPPING_RESOURCE);
            if (in == null) {
                try { lion.client.ClientLogger.error("[LionDeobf] " + MAPPING_RESOURCE
                        + " not found on classpath; cannot remap MCP -> SRG", null); }
                catch (Throwable ignored) {}
                return false;
            }

            int methodCount = 0;
            int fieldCount  = 0;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MD: ")) {
                        if (parseMethodLine(line, methods)) methodCount++;
                    } else if (line.startsWith("FD: ")) {
                        if (parseFieldLine(line, fields)) fieldCount++;
                    }
                }
            } catch (IOException e) {
                try { lion.client.ClientLogger.error("[LionDeobf] read of " + MAPPING_RESOURCE + " failed", e); }
                catch (Throwable ignored) {}
                return false;
            } finally {
                try { in.close(); } catch (IOException ignored) {}
            }

            Map<String, String> fieldFallback = buildDedupedFallback(fields);
            Map<String, String> methodFallback = buildDedupedFallback(methods);

            mcpToSrgMethods         = Collections.unmodifiableMap(methods);
            mcpToSrgFields          = Collections.unmodifiableMap(fields);
            mcpFieldNameToSrgGlobal = Collections.unmodifiableMap(fieldFallback);
            mcpMethodSigToSrgGlobal = Collections.unmodifiableMap(methodFallback);

            return true;
        }
    }

    private static Map<String, String> buildDedupedFallback(Map<String, Map<String, String>> perOwner) {
        Map<String, String> agg = new HashMap<String, String>();
        java.util.Set<String> conflicted = new java.util.HashSet<String>();
        for (Map<String, String> inner : perOwner.values()) {
            for (Map.Entry<String, String> e : inner.entrySet()) {
                String existing = agg.get(e.getKey());
                if (existing == null) {
                    agg.put(e.getKey(), e.getValue());
                } else if (!existing.equals(e.getValue())) {
                    conflicted.add(e.getKey());
                }
            }
        }
        for (String k : conflicted) agg.remove(k);
        return agg;
    }

    private static boolean parseMethodLine(String line, Map<String, Map<String, String>> out) {
        String[] parts = line.split(" ");
        if (parts.length < 5) return false;
        String mcpFull = parts[1];
        String mcpDesc = parts[2];
        String srgFull = parts[3];
        int sep1 = mcpFull.lastIndexOf('/');
        int sep2 = srgFull.lastIndexOf('/');
        if (sep1 < 0 || sep2 < 0) return false;
        String owner   = mcpFull.substring(0, sep1);
        String mcpName = mcpFull.substring(sep1 + 1);
        String srgName = srgFull.substring(sep2 + 1);
        Map<String, String> inner = out.get(owner);
        if (inner == null) {
            inner = new HashMap<String, String>();
            out.put(owner, inner);
        }
        inner.put(mcpName + mcpDesc, srgName);
        return true;
    }

    private static boolean parseFieldLine(String line, Map<String, Map<String, String>> out) {
        String[] parts = line.split(" ");
        if (parts.length < 3) return false;
        String mcpFull = parts[1];
        String srgFull = parts[2];
        int sep1 = mcpFull.lastIndexOf('/');
        int sep2 = srgFull.lastIndexOf('/');
        if (sep1 < 0 || sep2 < 0) return false;
        String owner   = mcpFull.substring(0, sep1);
        String mcpName = mcpFull.substring(sep1 + 1);
        String srgName = srgFull.substring(sep2 + 1);
        Map<String, String> inner = out.get(owner);
        if (inner == null) {
            inner = new HashMap<String, String>();
            out.put(owner, inner);
        }
        inner.put(mcpName, srgName);
        return true;
    }

    private static String lookupSrgMethod(String owner, String mcpName, String desc) {
        Map<String, String> ownerMap = mcpToSrgMethods.get(owner);
        if (ownerMap != null) {
            String srgName = ownerMap.get(mcpName + desc);
            if (srgName != null) return srgName;
        }
        String key = mcpName + desc;
        for (String ancestor : hierarchyOf(owner)) {
            Map<String, String> m = mcpToSrgMethods.get(ancestor);
            if (m != null) {
                String srgName = m.get(key);
                if (srgName != null) return srgName;
            }
        }
        return mcpName;
    }

    private static String lookupSrgField(String owner, String mcpName) {
        Map<String, String> ownerMap = mcpToSrgFields.get(owner);
        if (ownerMap != null) {
            String srgName = ownerMap.get(mcpName);
            if (srgName != null) return srgName;
        }
        for (String ancestor : hierarchyOf(owner)) {
            Map<String, String> m = mcpToSrgFields.get(ancestor);
            if (m != null) {
                String srgName = m.get(mcpName);
                if (srgName != null) return srgName;
            }
        }
        return mcpName;
    }

    private static final Map<String, String[]> HIERARCHY_CACHE = new java.util.concurrent.ConcurrentHashMap<String, String[]>();
    private static final String[] EMPTY = new String[0];
    private static String[] hierarchyOf(String owner) {
        String[] cached = HIERARCHY_CACHE.get(owner);
        if (cached != null) return cached;
        String[] result;
        try {
            ClassLoader cl = LionDeobfTransformer.class.getClassLoader();
            Class<?> cls = Class.forName(owner.replace('/', '.'), false, cl);
            java.util.LinkedHashSet<String> chain = new java.util.LinkedHashSet<String>();
            collectHierarchy(cls.getSuperclass(), chain);
            for (Class<?> iface : cls.getInterfaces()) collectHierarchy(iface, chain);
            result = chain.toArray(new String[0]);
        } catch (Throwable t) {
            result = EMPTY;
        }
        HIERARCHY_CACHE.put(owner, result);
        return result;
    }
    private static void collectHierarchy(Class<?> cls, java.util.LinkedHashSet<String> out) {
        while (cls != null && cls != Object.class) {
            if (!out.add(cls.getName().replace('.', '/'))) return;
            for (Class<?> iface : cls.getInterfaces()) collectHierarchy(iface, out);
            cls = cls.getSuperclass();
        }
    }

    private static void collectHierarchyFromName(String startInternalName, java.util.LinkedHashSet<String> out) {
        if (startInternalName == null) return;
        if (!out.add(startInternalName)) return;
        try {
            ClassLoader cl = LionDeobfTransformer.class.getClassLoader();
            Class<?> cls = Class.forName(startInternalName.replace('/', '.'), false, cl);
            collectHierarchy(cls.getSuperclass(), out);
            for (Class<?> iface : cls.getInterfaces()) collectHierarchy(iface, out);
        } catch (Throwable ignored) {}
    }

    private static boolean isMappableOwner(String owner) {
        return owner != null
                && (owner.startsWith("net/minecraft/")
                 || owner.startsWith("net/minecraftforge/")
                 || owner.startsWith("com/lionclient/")
                 || owner.startsWith("lion/client/"));
    }

    private static final class RemapCV extends ClassVisitor {
        private String classOwner;

        private Map<String, String> overrideRenames;

        RemapCV(ClassVisitor cv) { super(Opcodes.ASM5, cv); }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.classOwner = name;
            this.overrideRenames = new HashMap<String, String>();
            collectOverrideRenames(superName, interfaces, overrideRenames);

            java.util.LinkedHashSet<String> chain = new java.util.LinkedHashSet<String>();
            if (superName != null) collectHierarchyFromName(superName, chain);
            if (interfaces != null) {
                for (String iface : interfaces) collectHierarchyFromName(iface, chain);
            }
            HIERARCHY_CACHE.put(name, chain.toArray(new String[0]));
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            String renamed = overrideRenames.get(name + desc);
            String emit = (renamed != null) ? renamed : name;
            return new RemapMV(super.visitMethod(access, emit, desc, sig, ex), classOwner, overrideRenames);
        }
    }

    private static void collectOverrideRenames(String superInternalName, String[] interfaceNames,
                                               Map<String, String> out) {
        java.util.LinkedHashSet<String> visited = new java.util.LinkedHashSet<String>();
        java.util.ArrayDeque<String> queue   = new java.util.ArrayDeque<String>();
        if (superInternalName != null) queue.add(superInternalName);
        if (interfaceNames != null) {
            for (String i : interfaceNames) queue.add(i);
        }
        ClassLoader cl = LionDeobfTransformer.class.getClassLoader();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!visited.add(cur)) continue;
            Map<String, String> m = mcpToSrgMethods.get(cur);
            if (m != null) {
                for (Map.Entry<String, String> e : m.entrySet()) {
                    if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
                }
            }
            try {
                Class<?> cls = Class.forName(cur.replace('/', '.'), false, cl);
                Class<?> sup = cls.getSuperclass();
                if (sup != null && sup != Object.class) {
                    queue.add(sup.getName().replace('.', '/'));
                }
                for (Class<?> iface : cls.getInterfaces()) {
                    queue.add(iface.getName().replace('.', '/'));
                }
            } catch (Throwable ignored) {}
        }
    }

    private static final class RemapMV extends MethodVisitor {
        private final String classOwner;
        private final Map<String, String> overrideRenames;

        RemapMV(MethodVisitor mv, String classOwner, Map<String, String> overrideRenames) {
            super(Opcodes.ASM5, mv);
            this.classOwner = classOwner;
            this.overrideRenames = overrideRenames;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (owner != null) {
                if (owner.equals(classOwner)) {
                    String renamed = overrideRenames.get(name + desc);
                    if (renamed != null) name = renamed;
                } else if (isMappableOwner(owner)) {
                    name = lookupSrgMethod(owner, name, desc);
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (isMappableOwner(owner)) {
                name = lookupSrgField(owner, name);
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }
    }
}
