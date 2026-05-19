package net.minecraftforge.fml.common.eventhandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

    private static final Map<Class<?>, List<Method>> SUBSCRIBER_CACHE = new HashMap<>();

    private final List<Object> listeners = new CopyOnWriteArrayList<>();

    public void register(Object target) {
        if (target == null) return;
        synchronized (listeners) {
            if (listeners.contains(target)) return;
            listeners.add(target);
        }
    }

    public void unregister(Object target) {
        if (target == null) return;
        listeners.remove(target);
    }

    public boolean post(Event event) {
        if (event == null) return false;
        Class<?> eventCls = event.getClass();
        for (Object listener : listeners) {
            List<Method> methods = methodsFor(listener.getClass());
            for (Method m : methods) {
                if (!m.getParameterTypes()[0].isAssignableFrom(eventCls)) continue;
                if (event.isCanceled() && !m.getAnnotation(SubscribeEvent.class).receiveCanceled()) continue;
                try {
                    m.invoke(listener, event);
                } catch (Throwable ignored) {
                }
            }
        }
        return event.isCanceled();
    }

    private static List<Method> methodsFor(Class<?> cls) {
        synchronized (SUBSCRIBER_CACHE) {
            List<Method> cached = SUBSCRIBER_CACHE.get(cls);
            if (cached != null) return cached;
            List<Method> out = new ArrayList<>();
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getAnnotation(SubscribeEvent.class) == null) continue;
                    if (m.getParameterTypes().length != 1) continue;
                    if (!Event.class.isAssignableFrom(m.getParameterTypes()[0])) continue;
                    m.setAccessible(true);
                    out.add(m);
                }
            }
            SUBSCRIBER_CACHE.put(cls, out);
            return out;
        }
    }
}
