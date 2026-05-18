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
        if (attached) {
            ClientLogger.info("[LionAgent] already attached — ignoring re-attach (" + args + ")");
            return;
        }
        attached = true;
        INSTR    = inst;

        ClientLogger.info("[LionAgent] attached (args=" + args
                + ", canRetransform=" + inst.isRetransformClassesSupported()
                + ", canRedefine=" + inst.isRedefineClassesSupported() + ")");

        boolean onForge = false;
        boolean onBadlion = false;
        ClassLoader mcCl = null;
        ClassLoader mainCl = null;
        int totalCount = 0;
        int mcNamespaceCount = 0;
        java.util.List<String> mcSamples = new java.util.ArrayList<>();
        try {
            for (Class<?> c : inst.getAllLoadedClasses()) {
                totalCount++;
                if (c == null) continue;
                String n = c.getName();
                if (n.startsWith("net.minecraft.")) {
                    mcNamespaceCount++;
                    if (mcSamples.size() < 20) {
                        ClassLoader cl = c.getClassLoader();
                        mcSamples.add(n + " [loader=" + (cl == null ? "boot"
                                : cl.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(cl))) + "]");
                    }
                }
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

        ClientLogger.info("[LionAgent] scanned " + totalCount + " loaded classes, "
                + mcNamespaceCount + " in net.minecraft.* namespace");
        if (!mcSamples.isEmpty()) {
            ClientLogger.info("[LionAgent] net.minecraft.* samples:");
            for (String s : mcSamples) ClientLogger.info("    " + s);
        }

        boolean launchwrapperDetected = false;
        try {
            Class<?> launchCls = Class.forName("net.minecraft.launchwrapper.Launch");
            java.lang.reflect.Field clField = launchCls.getField("classLoader");
            Object lcl = clField.get(null);
            ClientLogger.info("[LionAgent] launchwrapper probe: Launch.classLoader = "
                    + (lcl == null ? "null" : lcl.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(lcl))));
            if (lcl instanceof ClassLoader) {
                mcCl = (ClassLoader) lcl;
                launchwrapperDetected = true;
                ClientLogger.info("[LionAgent] launchwrapper detected — using "
                        + mcCl.getClass().getName() + " as MC loader (skipping vanilla notch path).");
            }
        } catch (Throwable t) {
            ClientLogger.info("[LionAgent] launchwrapper probe failed: " + t.getClass().getName() + ": " + t.getMessage());
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
                        ClientLogger.info("[LionAgent] launchwrapper detected via loaded-class scan — using "
                                + clName + " (holds " + c.getName() + ") as MC loader.");
                        break;
                    }
                }
            } catch (Throwable t) {
                ClientLogger.info("[LionAgent] LaunchClassLoader scan failed: " + t);
            }
        }

        if (!launchwrapperDetected && mcCl == null && mainCl != null) {
            ClientLogger.warn("[LionAgent] net.minecraft.client.Minecraft is NOT loaded under that name. "
                    + "Main is, so the JVM IS running Minecraft — but the rest of MC is Notch-obfuscated "
                    + "(short names like 'bao', 'ave'). Installing LionVanillaTransformer to rewrite our "
                    + "MCP-named bytecode to notch at load time so LionClient binds to the running MC.");
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
                    ClientLogger.info("[LionAgent] Badlion detected via Class.forName probe on mcCl: " + name);
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (!onBadlion) {
            try {
                String v = System.getProperty("badlion.version");
                if (v != null) {
                    onBadlion = true;
                    ClientLogger.info("[LionAgent] Badlion detected via badlion.version sysprop = " + v);
                }
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
                            ClientLogger.info("[LionAgent] Badlion detected via mcCl URL list: " + u);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (onBadlion && onForge) {
            ClientLogger.info("[LionAgent] Badlion detected alongside Forge classes — forcing ON_FORGE=false. "
                    + "Badlion ships Forge stubs but does not run Forge's native event dispatch; "
                    + "treating like Lunar so runTick injection fires and the tick-wide rotation swap pipeline runs.");
            onForge = false;
        }

        MC_CLASSLOADER = mcCl;
        LionTransformer.ON_FORGE = onForge;
        ClientLogger.info("[LionAgent] MC_CLASSLOADER = "
                + (mcCl == null ? "null"
                        : mcCl.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(mcCl))));
        ClientLogger.info("[LionAgent] detected onForge=" + onForge + ", onBadlion=" + onBadlion
                + ", isVanilla=" + IS_VANILLA
                + " (LionTransformer.class loader=" + LionTransformer.class.getClassLoader() + ")");

        if (IS_VANILLA) {
            try {
                LionVanillaTransformer vt = new LionVanillaTransformer();
                if (vt.isReady()) {
                    VANILLA_TRANSFORMER = vt;
                    inst.addTransformer(vt, true);
                    NOTCH_MAPPING = vt.getMapping();
                    LionTransformer.setVanillaMappings(NOTCH_MAPPING);
                    ClientLogger.info("[LionAgent] LionVanillaTransformer installed.");
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
        if (transformerInstalled) {
            ClientLogger.info("[LionAgent] transformer already installed");
            return;
        }
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
            ClientLogger.info("[LionAgent] transformer registered.");
        } catch (Throwable t) {
            ClientLogger.error("[LionAgent] addTransformer failed", t);
            return;
        }

        if (!inst.isRetransformClassesSupported()) {
            ClientLogger.warn("[LionAgent] retransform not supported — hooks only fire on FUTURE class loads.");
            return;
        }

        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c == null) continue;
            if (!LionTransformer.TARGET_NAMES_DOTTED.contains(c.getName())) continue;
            try {
                inst.retransformClasses(c);
                ClientLogger.info("[LionAgent] retransformed " + c.getName());
            } catch (Throwable t) {
                ClientLogger.error("[LionAgent] retransform " + c.getName() + " failed", t);
            }
        }

        if (IS_VANILLA && VANILLA_TRANSFORMER != null && inst.isRetransformClassesSupported()) {
            int retransformed = 0;
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (c == null) continue;
                String n = c.getName();
                if (!n.startsWith("net.minecraftforge.") && !n.startsWith("com.lionclient.")) continue;
                try {
                    inst.retransformClasses(c);
                    retransformed++;
                } catch (Throwable t) {
                    ClientLogger.error("[LionAgent] vanilla catch-up retransform " + n + " failed", t);
                }
            }
            ClientLogger.info("[LionAgent] vanilla catch-up retransformed " + retransformed + " forge-shim/lionclient classes");
        }
    }

    public static Instrumentation instrumentation() { return INSTR; }
}
