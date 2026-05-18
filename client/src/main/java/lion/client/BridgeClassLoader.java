package lion.client;

import java.net.URL;
import java.net.URLClassLoader;

public final class BridgeClassLoader extends URLClassLoader {

    public BridgeClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public Class<?> defineClassPublic(String name, byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")) {
                return super.loadClass(name, resolve);
            }

            if (name.startsWith("lion.client.")
                    || name.startsWith("com.lionclient.")
                    || name.startsWith("org.objectweb.asm.")) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try { c = findClass(name); }
                    catch (ClassNotFoundException ignored) {}
                }
                if (c != null) {
                    if (resolve) resolveClass(c);
                    return c;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
