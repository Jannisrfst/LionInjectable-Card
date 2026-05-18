package lion.client.hook;

import java.util.ArrayList;
import java.util.List;

public final class LauncherDetection {

    public enum Kind { VANILLA, FORGE, FABRIC, LUNAR, BADLION, OPTIFINE, UNKNOWN }

    public static final class Result {
        public final Kind kind;
        public final int  confidence;
        public final String notes;
        public Result(Kind k, int c, String n) { kind = k; confidence = c; notes = n; }
    }

    private LauncherDetection() {}

    public static Result detect() {
        List<String> hits = new ArrayList<>();
        Kind kind = Kind.UNKNOWN;
        int conf = 0;

        if (classExists("com.moonsworth.lunar.genesis.Genesis") ||
            classExists("lunar.GenesisLauncher")) {
            kind = Kind.LUNAR; conf = Math.max(conf, 90);
            hits.add("found Lunar Genesis class");
        }
        if (classExists("net.badlion.client.Client") ||
            classExists("net.badlion.client.events.EventManager") ||
            classExists("net.badlion.client.Wrapper") ||
            classExists("net.badlion.client.gui.BLClient")) {
            kind = Kind.BADLION; conf = Math.max(conf, 90);
            hits.add("found Badlion client class");
        }
        if (classExists("net.minecraft.launchwrapper.Launch")) {
            hits.add("found launchwrapper Launch");
            if (kind == Kind.UNKNOWN || kind == Kind.VANILLA) {
                kind = Kind.FORGE;
                conf = Math.max(conf, 70);
            }
        }
        if (classExists("net.minecraftforge.fml.common.Loader") ||
            classExists("net.minecraftforge.fml.loading.FMLLoader") ||
            classExists("cpw.mods.fml.common.Loader")) {
            if (kind == Kind.UNKNOWN) kind = Kind.FORGE;
            conf = Math.max(conf, 80);
            hits.add("found Forge loader");
        }
        if (classExists("net.fabricmc.loader.api.FabricLoader") ||
            classExists("net.fabricmc.loader.impl.FabricLoaderImpl")) {
            if (kind == Kind.UNKNOWN) kind = Kind.FABRIC;
            conf = Math.max(conf, 80);
            hits.add("found Fabric loader");
        }
        if (classExists("optifine.OptiFineTweaker") ||
            classExists("net.optifine.Config")) {
            if (kind == Kind.UNKNOWN) kind = Kind.OPTIFINE;
            conf = Math.max(conf, 60);
            hits.add("found OptiFine class");
        }
        if (classExists("net.minecraft.client.Minecraft") ||
            classExists("net.minecraft.client.main.Main")) {
            if (kind == Kind.UNKNOWN) { kind = Kind.VANILLA; conf = 60; }
            hits.add("found vanilla Minecraft class");
        }

        String[] sysProps = {"lunar.version", "badlion.version", "minecraft.launcher.brand"};
        for (String k : sysProps) {
            String v = System.getProperty(k);
            if (v != null) hits.add(k + "=" + v);
        }

        if (hits.isEmpty()) hits.add("no launcher class fingerprints matched");
        return new Result(kind, conf, String.join("; ", hits));
    }

    private static boolean classExists(String name) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = LauncherDetection.class.getClassLoader();
            Class.forName(name, false, cl);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
