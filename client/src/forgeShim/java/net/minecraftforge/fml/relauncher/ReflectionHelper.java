package net.minecraftforge.fml.relauncher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class ReflectionHelper {
    private ReflectionHelper() {}

    public static Field findField(Class<?> cls, String... fieldNames) {
        Field f = resolveField(cls, fieldNames);
        if (f != null) return f;
        throw new UnableToFindFieldException("None of " + java.util.Arrays.toString(fieldNames)
                + " found on " + cls.getName());
    }

    public static Method findMethod(Class<?> cls, Object instance, String[] methodNames, Class<?>... params) {
        Method m = resolveMethod(cls, methodNames, params);
        if (m != null) return m;
        throw new UnableToFindMethodException(methodNames, null);
    }

    @SuppressWarnings("unchecked")
    public static <T, E> T getPrivateValue(Class<? super E> cls, E instance, String... fieldNames) {
        try { return (T) findField(cls, fieldNames).get(instance); }
        catch (IllegalAccessException e) { return null; }
    }

    public static <T, E> void setPrivateValue(Class<? super T> cls, T instance, E value, String... fieldNames) {
        try { findField(cls, fieldNames).set(instance, value); } catch (Exception ignored) {}
    }

    public static Field resolveField(Class<?> cls, String[] names) {
        for (String n : names) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(n);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
        }
        Object mapping = readNotchMapping();
        if (mapping == null) return null;
        Map<String, String> notchToMcp     = readMappingField(mapping, "notchToMcp");
        Map<String, String> fieldToNotch   = readMappingField(mapping, "fieldToNotch");
        Map<String, String> fieldFallback  = readMappingField(mapping, "fieldFallback");
        if (notchToMcp == null || fieldToNotch == null) return null;
        for (String n : names) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                String notchOwner = c.getName().replace('.', '/');
                String mcpOwner = notchToMcp.containsKey(notchOwner) ? notchToMcp.get(notchOwner) : notchOwner;
                String notchName = fieldToNotch.get(mcpOwner + "/" + n);
                if (notchName == null && fieldFallback != null) notchName = fieldFallback.get(n);
                if (notchName == null) continue;
                try {
                    Field f = c.getDeclaredField(notchName);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
        }
        return null;
    }

    static Method resolveMethod(Class<?> cls, String[] names, Class<?>[] params) {
        for (String n : names) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Method m = c.getDeclaredMethod(n, params);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {}
            }
        }
        Object mapping = readNotchMapping();
        if (mapping == null) return null;
        Map<String, String> notchToMcp      = readMappingField(mapping, "notchToMcp");
        Map<String, String> methodToNotch   = readMappingField(mapping, "methodToNotch");
        Map<String, String> methodFallback  = readMappingField(mapping, "methodFallback");
        if (notchToMcp == null || methodToNotch == null) return null;
        String desc = buildMethodDescriptor(params);
        for (String n : names) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                String notchOwner = c.getName().replace('.', '/');
                String mcpOwner = notchToMcp.containsKey(notchOwner) ? notchToMcp.get(notchOwner) : notchOwner;
                String hit = findMethodNotchName(mcpOwner, n, desc, methodToNotch);
                if (hit == null && methodFallback != null) {
                    hit = methodFallback.get(n + " " + desc);
                }
                if (hit == null) continue;
                try {
                    Method m = c.getDeclaredMethod(hit, params);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {}
            }
        }
        return null;
    }

    private static String findMethodNotchName(String mcpOwner, String mcpName, String desc, Map<String, String> methodToNotch) {
        String exact = methodToNotch.get(mcpOwner + "/" + mcpName + " " + desc);
        if (exact != null) return exact;
        String prefix = mcpOwner + "/" + mcpName + " ";
        for (Map.Entry<String, String> e : methodToNotch.entrySet()) {
            if (e.getKey().startsWith(prefix)) return e.getValue();
        }
        return null;
    }

    private static String buildMethodDescriptor(Class<?>[] params) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params) sb.append(typeDescriptor(p));
        sb.append(")V");
        return sb.toString();
    }

    private static String typeDescriptor(Class<?> c) {
        if (c == void.class)    return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class)    return "B";
        if (c == char.class)    return "C";
        if (c == short.class)   return "S";
        if (c == int.class)     return "I";
        if (c == long.class)    return "J";
        if (c == float.class)   return "F";
        if (c == double.class)  return "D";
        if (c.isArray())        return "[" + typeDescriptor(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private static Object readNotchMapping() {
        try {
            Class<?> lionAgent = Class.forName("lion.client.agent.LionAgent");
            return lionAgent.getField("NOTCH_MAPPING").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readMappingField(Object mapping, String fieldName) {
        try {
            return (Map<String, String>) mapping.getClass().getField(fieldName).get(mapping);
        } catch (Throwable t) {
            return null;
        }
    }

    public static class UnableToFindFieldException extends RuntimeException {
        public UnableToFindFieldException(String msg) { super(msg); }
    }
    public static class UnableToFindMethodException extends RuntimeException {
        public UnableToFindMethodException(String[] names, Exception cause) { super(java.util.Arrays.toString(names), cause); }
    }
}
