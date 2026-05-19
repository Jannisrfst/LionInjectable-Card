package lion.client.agent;

import lion.client.ClientLogger;

import java.lang.instrument.Instrumentation;

public final class LionAgent {

    private static volatile Instrumentation INSTR;
    private static volatile boolean attached;
    private static volatile boolean transformerInstalled;

    public static volatile ClassLoader MC_CLASSLOADER;

    public static volatile boolean IS_VANILLA;

    public static volatile lion.client.deobf.NotchMapping NOTCH_MAPPING;

    private static volatile LionVanillaTransformer VANILLA_TRANSFORMER;

    private static volatile boolean vanillaRemapperInstalled;

    private LionAgent() {}

    public static void premain(String args, Instrumentation inst)  { attach(args, inst); }
    public static void agentmain(String args, Instrumentation inst) { attach(args, inst); }

    private static synchronized void attach(String args, Instrumentation inst) {
        if (attached) return;
        attached = true;
        INSTR    = inst;

        boolean onForge = false;
        boolean onBadlion = false;
        ClassLoader mcCl = null;
        ClassLoader mainCl = null;
        try {
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (c == null) continue;
                String n = c.getName();
                if (mcCl == null && "net.minecraft.client.Minecraft".equals(n)) {
                    mcCl = c.getClassLoader();
                }
                if (mainCl == null && "net.minecraft.client.main.Main".equals(n)) {
                    mainCl = c.getClassLoader();
                }
                if (!onForge && ("net.minecraftforge.common.ForgeVersion".equals(n)
                        || "net.minecraftforge.fml.common.Loader".equals(n))) {
                    onForge = true;
                }
                if (!onBadlion && n.startsWith("net.badlion.client.")) {
                    onBadlion = true;
                }
            }
        } catch (Throwable t) {
            ClientLogger.error("[LionAgent] loaded-class scan failed", t);
        }

        boolean launchwrapperDetected = false;
        try {
            Class<?> launchCls = Class.forName("net.minecraft.launchwrapper.Launch");
            java.lang.reflect.Field clField = launchCls.getField("classLoader");
            Object lcl = clField.get(null);
            if (lcl instanceof ClassLoader) {
                mcCl = (ClassLoader) lcl;
                launchwrapperDetected = true;
            }
        } catch (Throwable ignored) {
        }

        if (!launchwrapperDetected) {
            try {
                for (Class<?> c : inst.getAllLoadedClasses()) {
                    if (c == null) continue;
                    ClassLoader cl = c.getClassLoader();
                    if (cl == null) continue;
                    String clName = cl.getClass().getName();
                    if (clName.endsWith(".LaunchClassLoader") || clName.endsWith("$LaunchClassLoader")) {
                        mcCl = cl;
                        launchwrapperDetected = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (!launchwrapperDetected && mcCl == null && mainCl != null) {
            mcCl = mainCl;
            IS_VANILLA = true;
        }

        if (!onBadlion && mcCl != null) {
            String[] badlionProbeClasses = {
                    "net.badlion.client.Client",
                    "net.badlion.client.Wrapper",
                    "net.badlion.client.events.EventManager",
                    "net.badlion.client.gui.BLClient",
            };
            for (String name : badlionProbeClasses) {
                try {
                    Class.forName(name, false, mcCl);
                    onBadlion = true;
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (!onBadlion) {
            try {
                String v = System.getProperty("badlion.version");
                if (v != null) onBadlion = true;
            } catch (Throwable ignored) {}
        }
        if (!onBadlion && mcCl instanceof java.net.URLClassLoader) {
            try {
                java.net.URL[] urls = ((java.net.URLClassLoader) mcCl).getURLs();
                if (urls != null) {
                    for (java.net.URL u : urls) {
                        if (u == null) continue;
                        String s = u.toString().toLowerCase(java.util.Locale.ROOT);
                        if (s.contains("badlion") || s.contains("blclient")) {
                            onBadlion = true;
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (onBadlion && onForge) {
            onForge = false;
        }

        MC_CLASSLOADER = mcCl;
        LionTransformer.ON_FORGE = onForge;

        if (IS_VANILLA) {
            try {
                LionVanillaTransformer vt = new LionVanillaTransformer();
                if (vt.isReady()) {
                    VANILLA_TRANSFORMER = vt;
                    inst.addTransformer(vt, true);
                    NOTCH_MAPPING = vt.getMapping();
                    LionTransformer.setVanillaMappings(NOTCH_MAPPING);
                } else {
                    ClientLogger.error("[LionAgent] LionVanillaTransformer mapping unavailable; "
                            + "vanilla bootstrap will fail.", null);
                }
            } catch (Throwable t) {
                ClientLogger.error("[LionAgent] LionVanillaTransformer install failed", t);
            }
        }
    }

    public static synchronized void installTransformerAndRetransform() {
        Instrumentation inst = INSTR;
        if (inst == null) {
            ClientLogger.warn("[LionAgent] installTransformerAndRetransform called before attach");
            return;
        }
        if (transformerInstalled) return;
        transformerInstalled = true;

        if (IS_VANILLA && NOTCH_MAPPING != null) {
            try {
                LionTransformer.setVanillaMappings(NOTCH_MAPPING);
            } catch (Throwable t) {
                ClientLogger.error("[LionAgent] LionTransformer.setVanillaMappings failed", t);
            }
        }

        try {
            inst.addTransformer(new LionTransformer(), true);
        } catch (Throwable t) {
            ClientLogger.error("[LionAgent] addTransformer failed", t);
            return;
        }

        if (!inst.isRetransformClassesSupported()) return;

        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c == null) continue;
            if (!LionTransformer.TARGET_NAMES_DOTTED.contains(c.getName())) continue;
            try {
                inst.retransformClasses(c);
            } catch (Throwable t) {
                ClientLogger.error("[LionAgent] retransform " + c.getName() + " failed", t);
            }
        }

        if (IS_VANILLA && VANILLA_TRANSFORMER != null) {
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (c == null) continue;
                String n = c.getName();
                if (!n.startsWith("net.minecraftforge.") && !n.startsWith("com.lionclient.")) continue;
                try {
                    inst.retransformClasses(c);
                } catch (Throwable t) {
                    ClientLogger.error("[LionAgent] vanilla catch-up retransform " + n + " failed", t);
                }
            }
        }
    }

    public static Instrumentation instrumentation() { return INSTR; }
}
