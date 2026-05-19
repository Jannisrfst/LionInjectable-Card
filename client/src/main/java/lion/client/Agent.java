package lion.client;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;

public final class Agent {

    private static volatile boolean booted;

    private Agent() {}

    public static void start(String jarPath, String dllPath) {
        if (booted) return;
        booted = true;

        File jar = new File(jarPath);
        File logDir = (jar.getParentFile() != null)
                ? jar.getParentFile()
                : new File(System.getProperty("java.io.tmpdir"), "LionInjector");
        logDir.mkdirs();
        ClientLogger.bootstrap(new File(logDir, "client.log").getAbsolutePath());

        try {
            System.load(dllPath);
        } catch (Throwable t) {
            ClientLogger.error("System.load failed: " + dllPath, t);
        }

        boolean agentAttached = false;
        try {
            agentAttached = NativeBridge.attachAgent(jar.getAbsolutePath());
        } catch (Throwable t) {
            ClientLogger.error("Early JVMTI agent attach failed", t);
        }

        URL jarUrl;
        try {
            jarUrl = jar.toURI().toURL();
        } catch (Throwable t) {
            ClientLogger.error("jar toURI().toURL() failed", t);
            return;
        }

        ClassLoader mcCl = null;
        if (agentAttached) {
            mcCl = readLionAgentMcClassLoader();
            if (mcCl != null) {
                if (!tryAddURL(mcCl, jarUrl)) {
                    mcCl = new BridgeClassLoader(new URL[] { jarUrl }, mcCl);
                }
            }
        }
        if (mcCl == null) {
            try {
                mcCl = resolveMcClassLoader(jarUrl);
            } catch (Throwable t) {
                ClientLogger.error("resolveMcClassLoader failed", t);
                return;
            }
        }
        if (mcCl == null) {
            ClientLogger.error("Could not find a classloader that can resolve Minecraft. Aborting.", null);
            return;
        }

        try {
            java.lang.reflect.Method registerTransformer =
                    mcCl.getClass().getMethod("registerTransformer", String.class);
            registerTransformer.invoke(mcCl, "lion.client.agent.LionDeobfTransformer");
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            ClientLogger.error("Failed to register LionDeobfTransformer", t);
        }

        if (lion.client.agent.LionAgent.IS_VANILLA) {
            String[] forgeShimEvents = {
                    "net.minecraftforge.client.event.RenderGameOverlayEvent",
                    "net.minecraftforge.client.event.RenderGameOverlayEvent$Pre",
                    "net.minecraftforge.client.event.RenderGameOverlayEvent$Post",
                    "net.minecraftforge.client.event.RenderGameOverlayEvent$Text",
                    "net.minecraftforge.client.event.RenderWorldLastEvent",
                    "net.minecraftforge.client.event.MouseEvent",
                    "net.minecraftforge.event.entity.living.LivingEvent",
                    "net.minecraftforge.event.entity.living.LivingEvent$LivingJumpEvent",
                    "net.minecraftforge.fml.common.gameevent.TickEvent",
                    "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent",
                    "net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent",
                    "net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent",
            };
            int preloaded = 0;
            for (String fqcn : forgeShimEvents) {
                try {
                    Class.forName(fqcn, true, mcCl);
                    preloaded++;
                } catch (Throwable t) {
                    ClientLogger.error("Vanilla pre-load of " + fqcn + " failed", t);
                }
            }
        }

        try {
            Class<?> lionClientCls = Class.forName("com.lionclient.LionClient", true, mcCl);
            Object lionClient = lionClientCls.getConstructor().newInstance();
            lionClientCls.getMethod("bootstrap").invoke(lionClient);

            Class.forName("lion.client.agent.Hooks", true, mcCl);
        } catch (Throwable t) {
            ClientLogger.error("LionClient bootstrap failed", t);
            return;
        }

        if (agentAttached) {
            try {
                Class<?> agentClass = Class.forName("lion.client.agent.LionAgent");
                agentClass.getMethod("installTransformerAndRetransform").invoke(null);
            } catch (Throwable t) {
                ClientLogger.error("installTransformerAndRetransform invocation failed", t);
            }
        }
    }

    private static ClassLoader readLionAgentMcClassLoader() {
        try {
            Class<?> agentClass = Class.forName("lion.client.agent.LionAgent");
            return (ClassLoader) agentClass.getField("MC_CLASSLOADER").get(null);
        } catch (Throwable t) {
            ClientLogger.error("Could not read LionAgent.MC_CLASSLOADER", t);
            return null;
        }
    }

    private static ClassLoader resolveMcClassLoader(URL jarUrl) {
        try {
            Class<?> launchCls = Class.forName("net.minecraft.launchwrapper.Launch");
            Object cl = launchCls.getField("classLoader").get(null);
            if (cl instanceof ClassLoader && canLoadMinecraft((ClassLoader) cl)) {
                ClassLoader launchCL = (ClassLoader) cl;
                if (tryAddURL(launchCL, jarUrl)) {
                    return launchCL;
                }
                return new BridgeClassLoader(new URL[] { jarUrl }, launchCL);
            }
        } catch (Throwable ignored) {}

        java.util.LinkedHashSet<ClassLoader> candidates = new java.util.LinkedHashSet<>();
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root != null && root.getParent() != null) root = root.getParent();
            if (root != null) {
                Thread[] all = new Thread[Math.max(64, root.activeCount() * 4)];
                int n = root.enumerate(all, true);
                for (int i = 0; i < n; i++) {
                    Thread t = all[i];
                    if (t == null) continue;
                    ClassLoader ccl = t.getContextClassLoader();
                    for (ClassLoader cl = ccl; cl != null; cl = cl.getParent()) {
                        candidates.add(cl);
                    }
                }
            }
        } catch (Throwable t) {
            ClientLogger.error("ThreadGroup walk failed", t);
        }
        for (ClassLoader cl = Agent.class.getClassLoader(); cl != null; cl = cl.getParent()) {
            candidates.add(cl);
        }
        try {
            for (ClassLoader cl = ClassLoader.getSystemClassLoader(); cl != null; cl = cl.getParent()) {
                candidates.add(cl);
            }
        } catch (Throwable ignored) {}

        for (ClassLoader cl : candidates) {
            if (!canLoadMinecraft(cl)) continue;
            if (tryAddURL(cl, jarUrl)) {
                return cl;
            }
            return new BridgeClassLoader(new URL[] { jarUrl }, cl);
        }

        String[] mcNames = { "net.minecraft.client.Minecraft", "net.minecraft.client.main.Main" };
        ClassLoader[] seeds = {
                Thread.currentThread().getContextClassLoader(),
                Agent.class.getClassLoader(),
                tryGetSystemCL(),
        };
        for (String name : mcNames) {
            for (ClassLoader seed : seeds) {
                if (seed == null) continue;
                try {
                    Class<?> c = Class.forName(name, false, seed);
                    ClassLoader actual = c.getClassLoader();
                    if (actual == null) continue;
                    if (tryAddURL(actual, jarUrl)) {
                        return actual;
                    }
                    return new BridgeClassLoader(new URL[] { jarUrl }, actual);
                } catch (Throwable ignored) {}
            }
        }

        return null;
    }

    private static ClassLoader tryGetSystemCL() {
        try { return ClassLoader.getSystemClassLoader(); }
        catch (Throwable t) { return null; }
    }

    private static boolean canLoadMinecraft(ClassLoader cl) {
        try { cl.loadClass("net.minecraft.client.Minecraft"); return true; }
        catch (Throwable t) { return false; }
    }

    private static boolean tryAddURL(ClassLoader cl, URL url) {
        try {
            Method addURL = cl.getClass().getMethod("addURL", URL.class);
            addURL.invoke(cl, url);
            return true;
        } catch (Throwable ignored) {}
        try {
            Method addURL = java.net.URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(cl, url);
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean hasRealForgeBus(ClassLoader mcCl) {
        try {
            Class<?> mf = Class.forName("net.minecraftforge.common.MinecraftForge", false, mcCl);
            mcCl.loadClass("net.minecraftforge.common.ForgeVersion");
            return mf != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
