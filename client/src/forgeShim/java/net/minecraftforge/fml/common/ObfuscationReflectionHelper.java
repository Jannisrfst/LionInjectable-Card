package net.minecraftforge.fml.common;

import java.lang.reflect.Field;

public final class ObfuscationReflectionHelper {
    private ObfuscationReflectionHelper() {}

    @SuppressWarnings("unchecked")
    public static <T, E> T getPrivateValue(Class<? super E> classToAccess, E instance, String... fieldNames) {
        Field f = resolve(classToAccess, fieldNames);
        if (f == null) return null;
        try { return (T) f.get(instance); }
        catch (IllegalAccessException e) { return null; }
    }

    public static <T, E> void setPrivateValue(Class<? super T> classToAccess, T instance, E value, String... fieldNames) {
        Field f = resolve(classToAccess, fieldNames);
        if (f == null) return;
        try { f.set(instance, value); } catch (IllegalAccessException ignored) {}
    }

    public static Field findField(Class<?> classToAccess, String... fieldNames) {
        return resolve(classToAccess, fieldNames);
    }

    private static Field resolve(Class<?> cls, String[] names) {
        return net.minecraftforge.fml.relauncher.ReflectionHelper.resolveField(cls, names);
    }
}
