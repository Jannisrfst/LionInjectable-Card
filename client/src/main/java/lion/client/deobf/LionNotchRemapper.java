package lion.client.deobf;

import org.objectweb.asm.commons.Remapper;

public final class LionNotchRemapper extends Remapper {

    private final NotchMapping map;

    public LionNotchRemapper(NotchMapping map) {
        this.map = map;
    }

    @Override
    public String map(String internalName) {
        if (internalName == null) return null;
        String notch = map.classToNotch.get(internalName);
        return notch != null ? notch : internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.charAt(0) == '<') return name;
        if (!owner.startsWith("net/minecraft/")) return name;

        String key = owner + "/" + name + " " + descriptor;
        String notch = map.methodToNotch.get(key);
        if (notch != null) return notch;

        String flatKey = name + " " + descriptor;
        notch = map.methodFallback.get(flatKey);
        return notch != null ? notch : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        if (!owner.startsWith("net/minecraft/")) return name;
        String key = owner + "/" + name;
        String notch = map.fieldToNotch.get(key);
        if (notch != null) return notch;
        notch = map.fieldFallback.get(name);
        return notch != null ? notch : name;
    }

    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        return name;
    }
}
