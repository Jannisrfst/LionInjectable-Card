package lion.client.deobf;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class NotchMapping {

    public final Map<String, String> classToNotch;

    public final Map<String, String> notchToMcp;

    public final Map<String, String> fieldToNotch;

    public final Map<String, String> methodToNotch;

    public final Map<String, String> fieldFallback;

    public final Map<String, String> methodFallback;

    public final Map<String, Map<String, String>> methodsByOwner;

    public final Map<String, Map<String, String>> fieldsByOwner;

    private NotchMapping(Map<String, String> classes,
                         Map<String, String> fields,
                         Map<String, String> methods,
                         Map<String, String> fieldFallback,
                         Map<String, String> methodFallback,
                         Map<String, Map<String, String>> methodsByOwner,
                         Map<String, Map<String, String>> fieldsByOwner,
                         Map<String, String> notchToMcp) {
        this.classToNotch    = Collections.unmodifiableMap(classes);
        this.notchToMcp      = Collections.unmodifiableMap(notchToMcp);
        this.fieldToNotch    = Collections.unmodifiableMap(fields);
        this.methodToNotch   = Collections.unmodifiableMap(methods);
        this.fieldFallback   = Collections.unmodifiableMap(fieldFallback);
        this.methodFallback  = Collections.unmodifiableMap(methodFallback);
        this.methodsByOwner  = Collections.unmodifiableMap(methodsByOwner);
        this.fieldsByOwner   = Collections.unmodifiableMap(fieldsByOwner);
    }

    public String lookupMethodName(String mcpOwner, String mcpName, String desc) {
        String key = mcpOwner + "/" + mcpName + " " + desc;
        return methodToNotch.get(key);
    }

    public String lookupFieldName(String mcpOwner, String mcpName) {
        String key = mcpOwner + "/" + mcpName;
        return fieldToNotch.get(key);
    }

    public static NotchMapping loadFromResource(String resource) {
        InputStream in = NotchMapping.class.getResourceAsStream(resource);
        if (in == null) return null;
        try {
            return parse(in);
        } catch (Throwable t) {
            return null;
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static NotchMapping parse(InputStream in) throws Exception {
        Map<String, String> classes = new HashMap<>();
        Map<String, String> fields  = new HashMap<>();
        Map<String, String> methods = new HashMap<>();

        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("CL: ")) {
                String[] p = line.split(" ");
                if (p.length >= 3) classes.put(p[1], p[2]);
            } else if (line.startsWith("FD: ")) {
                String[] p = line.split(" ");
                if (p.length >= 3) {
                    int sep1 = p[1].lastIndexOf('/');
                    int sep2 = p[2].lastIndexOf('/');
                    if (sep1 > 0 && sep2 > 0) {
                        fields.put(p[1], p[2].substring(sep2 + 1));
                    }
                }
            } else if (line.startsWith("MD: ")) {
                String[] p = line.split(" ");
                if (p.length >= 5) {
                    int sep1 = p[1].lastIndexOf('/');
                    int sep2 = p[3].lastIndexOf('/');
                    if (sep1 > 0 && sep2 > 0) {
                        methods.put(p[1] + " " + p[2], p[3].substring(sep2 + 1));
                    }
                }
            }
        }

        Map<String, String> fFallback = dedupedFlatten(fields, true);
        Map<String, String> mFallback = dedupedFlatten(methods, false);

        Map<String, Map<String, String>> methodsByOwner = new HashMap<>();
        for (Map.Entry<String, String> e : methods.entrySet()) {
            String key = e.getKey();
            int space = key.indexOf(' ');
            if (space < 0) continue;
            int slash = key.lastIndexOf('/', space);
            if (slash < 0) continue;
            String owner   = key.substring(0, slash);
            String nameDesc = key.substring(slash + 1);
            methodsByOwner.computeIfAbsent(owner, k -> new HashMap<>()).put(nameDesc, e.getValue());
        }

        Map<String, Map<String, String>> fieldsByOwner = new HashMap<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String key = e.getKey();
            int slash = key.lastIndexOf('/');
            if (slash < 0) continue;
            String owner = key.substring(0, slash);
            String name  = key.substring(slash + 1);
            fieldsByOwner.computeIfAbsent(owner, k -> new HashMap<>()).put(name, e.getValue());
        }

        Map<String, String> notchToMcp = new HashMap<>();
        for (Map.Entry<String, String> e : classes.entrySet()) {
            notchToMcp.put(e.getValue(), e.getKey());
        }

        return new NotchMapping(classes, fields, methods, fFallback, mFallback,
                methodsByOwner, fieldsByOwner, notchToMcp);
    }

    private static Map<String, String> dedupedFlatten(Map<String, String> perOwnerMap, boolean isField) {
        Map<String, String> agg = new HashMap<>();
        java.util.Set<String> conflicted = new java.util.HashSet<>();
        for (Map.Entry<String, String> e : perOwnerMap.entrySet()) {
            String key = e.getKey();
            String flatKey;
            if (isField) {
                int slash = key.lastIndexOf('/');
                if (slash < 0) continue;
                flatKey = key.substring(slash + 1);
            } else {
                int space = key.indexOf(' ');
                if (space < 0) continue;
                int slash = key.lastIndexOf('/', space);
                if (slash < 0) continue;
                flatKey = key.substring(slash + 1);
            }
            String existing = agg.get(flatKey);
            if (existing == null) {
                agg.put(flatKey, e.getValue());
            } else if (!existing.equals(e.getValue())) {
                conflicted.add(flatKey);
            }
        }
        for (String k : conflicted) agg.remove(k);
        return agg;
    }
}
