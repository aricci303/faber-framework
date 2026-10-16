package faber.devl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tees everything written to System.out and System.err into a log file, in addition to the normal
 * console — so a run produces its own complete log whatever launched it (an IDE run configuration,
 * Maven, a terminal), with no shell redirection. The file is flushed on every write: a run that is
 * terminated half way still leaves a usable log. IDE consoles often truncate long output; the file does not.
 */
public final class RunLog {
    private static PrintStream origOut, origErr;
    private static OutputStream file;
    private static volatile long lastOutputNanos = System.nanoTime();
    private static volatile boolean watching = false;
    private static Thread watchdog;
    static final String WATCHDOG_THREAD = "devl-watchdog";

    private RunLog() {}

    public static String stamp() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")); }

    /** Starts teeing into dir/name; returns the file. Call {@link #stop()} to restore the original streams. */
    public static synchronized Path start(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path path = dir.resolve(name);
        file = Files.newOutputStream(path);
        origOut = System.out; origErr = System.err;
        System.setOut(new PrintStream(new Tee(origOut), true, "UTF-8"));
        System.setErr(new PrintStream(new Tee(origErr), true, "UTF-8"));
        return path;
    }

    public static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")); }

    /**
     * Starts a daemon watchdog that tells a slow run from a stuck one. When nothing has been written for
     * quietMillis it prints a timestamped notice (the usual cause is a long LLM call, or an idle WAIT of up to 30 s);
     * if the silence reaches three times that, it prints a full thread dump once, with deadlock detection.
     * Its own output does not count as activity.
     */
    public static synchronized void startWatchdog(long pollMillis, long quietMillis) {
        stopWatchdog();
        watching = true; lastOutputNanos = System.nanoTime();
        watchdog = new Thread(() -> {
            boolean dumped = false; long lastNoticeNs = 0;
            while (watching) {
                try { Thread.sleep(pollMillis); } catch (InterruptedException e) { return; }
                long quietNs = System.nanoTime() - lastOutputNanos;
                if (quietNs < quietMillis * 1_000_000L) { dumped = false; lastNoticeNs = 0; continue; }
                if (quietNs - lastNoticeNs >= quietMillis * 1_000_000L) {
                    lastNoticeNs = quietNs;
                    System.out.println("[devl " + now() + "] no output for " + (quietNs / 1_000_000_000L) + "s — waiting on an LLM call or an idle WAIT (up to 30s each)");
                }
                if (!dumped && quietNs >= 3 * quietMillis * 1_000_000L) {
                    dumped = true;
                    System.out.println("[devl " + now() + "] still silent after " + (quietNs / 1_000_000_000L) + "s — thread dump follows\n" + threadDump());
                }
            }
        }, WATCHDOG_THREAD);
        watchdog.setDaemon(true); watchdog.start();
    }

    public static synchronized void stopWatchdog() { watching = false; if (watchdog != null) { watchdog.interrupt(); watchdog = null; } }

    /** All application threads with state and stack, plus a deadlock report if the JVM detects one. */
    public static String threadDump() {
        StringBuilder sb = new StringBuilder("=== THREAD DUMP ===\n");
        java.lang.management.ThreadMXBean mx = java.lang.management.ManagementFactory.getThreadMXBean();
        long[] dead = mx.findDeadlockedThreads();
        if (dead != null) {
            sb.append("!!! DEADLOCK DETECTED among: ");
            for (java.lang.management.ThreadInfo ti : mx.getThreadInfo(dead, true, true)) sb.append(ti.getThreadName()).append(" (blocked on ").append(ti.getLockName()).append(" held by ").append(ti.getLockOwnerName()).append(") ");
            sb.append("\n");
        } else sb.append("(no deadlock detected by the JVM)\n");
        for (var e : Thread.getAllStackTraces().entrySet()) {
            Thread th = e.getKey(); String n = th.getName();
            if (n.equals(WATCHDOG_THREAD) || n.equals("Reference Handler") || n.equals("Finalizer") || n.equals("Signal Dispatcher")
                    || n.startsWith("Common-Cleaner") || n.startsWith("Notification Thread") || n.startsWith("Attach Listener")) continue;
            sb.append("\"").append(n).append("\" ").append(th.getState()).append("\n");
            int shown = 0; for (StackTraceElement f : e.getValue()) { if (shown++ >= 18) { sb.append("    ...\n"); break; } sb.append("    at ").append(f).append("\n"); }
        }
        return sb.append("=== END THREAD DUMP ===").toString();
    }

    public static synchronized void stop() {
        stopWatchdog();
        if (file == null) return;
        System.out.flush(); System.err.flush();
        System.setOut(origOut); System.setErr(origErr);
        try { file.close(); } catch (IOException ignored) {}
        file = null;
    }

    /** Writes to the console stream and, under one lock shared by stdout and stderr, to the log file. */
    private static final class Tee extends OutputStream {
        private final PrintStream console;
        Tee(PrintStream console) { this.console = console; }
        @Override public void write(int b) throws IOException { write(new byte[] { (byte) b }, 0, 1); }
        @Override public void write(byte[] buf, int off, int len) throws IOException {
            if (!Thread.currentThread().getName().equals(WATCHDOG_THREAD)) lastOutputNanos = System.nanoTime();
            console.write(buf, off, len);
            synchronized (RunLog.class) { if (file != null) { file.write(buf, off, len); file.flush(); } }
        }
        @Override public void flush() { console.flush(); }
    }
}
