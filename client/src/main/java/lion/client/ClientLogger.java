package lion.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

public final class ClientLogger {

    private static volatile Path logFile;

    private static volatile Path fallbackLog;
    private static final ReentrantLock lock = new ReentrantLock();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    static {
        try {
            String tmp = System.getenv("TEMP");
            if (tmp == null || tmp.isEmpty()) tmp = System.getProperty("java.io.tmpdir");
            if (tmp == null || tmp.isEmpty()) tmp = System.getProperty("user.home");
            if (tmp != null) {
                Path p = Paths.get(tmp, "lion-client.log");
                fallbackLog = p;
                Files.write(p, ("---- new session " + LocalDateTime.now() + " ----\n")
                                .getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Throwable ignored) {
        }
    }

    private ClientLogger() {}

    public static void bootstrap(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            Path p = Paths.get(path);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            logFile = p;
            write("INFO ", "==== Lion client log session started (primary=" + p
                    + " fallback=" + fallbackLog + ") ====");
        } catch (IOException ignored) {
        }
    }

    public static void trace(String msg) { write("TRACE", msg); }
    public static void info (String msg) { write("INFO ", msg); }
    public static void warn (String msg) { write("WARN ", msg); }
    public static void error(String msg) { write("ERROR", msg); }

    public static void error(String msg, Throwable t) {
        StringWriter sw = new StringWriter();
        sw.append(msg).append('\n');
        t.printStackTrace(new PrintWriter(sw));
        write("ERROR", sw.toString());
    }

    private static void write(String level, String msg) {
        String line = "[" + LocalDateTime.now().format(TS) + "][" + level + "] " + msg + System.lineSeparator();
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        lock.lock();
        try {
            if (logFile != null) try {
                Files.write(logFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Throwable ignored) {}
            if (fallbackLog != null) try {
                Files.write(fallbackLog, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Throwable ignored) {}
        } finally {
            lock.unlock();
        }
    }
}
