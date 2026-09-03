package com.kaminari.gram;

import android.content.Context;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * KamiGram's always-on log.
 *
 * Upstream {@link org.telegram.messenger.FileLog} is gated behind
 * {@code BuildVars.LOGS_ENABLED}, which is false on release builds, so by default
 * a fork ships blind: crashes and errors vanish when the process dies. This class
 * fixes that. It writes to exactly one file,
 *
 * <pre>/data/data/com.kaminari.gram/files/kamigram.log</pre>
 *
 * on every build type, for three categories of events:
 *
 * <ul>
 *   <li>uncaught exceptions, via the handler installed in {@link #install()}</li>
 *   <li>errors and fatals mirrored from FileLog, so everything upstream already
 *       considers worth logging lands here too</li>
 *   <li>fork events, currently Extraordikami's deleted-message capture</li>
 * </ul>
 *
 * <h3>Why writes are synchronous</h3>
 * An earlier revision queued lines onto a daemon thread. That is wrong for the
 * primary use case: when the crash handler runs, the process is about to die, and
 * a queued line is lost if the writer thread is not scheduled first. Every write
 * therefore appends and flushes inline under one lock. This is safe because the
 * call sites are errors, not hot paths - upstream only reaches FileLog.e on an
 * actual failure.
 *
 * The writer is deliberately fail-silent: a logging fault must never mask, or
 * become, the fault being logged. After the first failure the class disables
 * itself instead of retrying.
 */
public final class KamiLog {

    private KamiLog() {
    }

    public static final String FILE_NAME = "kamigram.log";

    /** Past this size the oldest half is dropped, so the log cannot grow unbounded. */
    private static final long MAX_SIZE = 4L * 1024 * 1024;

    private static final Object lock = new Object();
    private static final SimpleDateFormat stamp =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static volatile boolean disabled;
    private static File file;
    private static OutputStreamWriter writer;
    private static long written;

    /** Opens the log and takes over crash handling. Idempotent. */
    public static void install() {
        synchronized (lock) {
            if (file != null || disabled) {
                return;
            }
            try {
                Context context = ApplicationLoader.applicationContext;
                if (context == null) {
                    return; // called too early; ApplicationLoader retries
                }
                File dir = context.getFilesDir();
                if (dir == null) {
                    disabled = true;
                    return;
                }
                file = new File(dir, FILE_NAME);
                written = file.exists() ? file.length() : 0;
            } catch (Throwable t) {
                disabled = true;
                return;
            }
        }

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // record first: the previous handler usually kills the process
            write("FATAL", "app", "uncaught exception on thread " + thread.getName(), throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });

        // Deliberately does not touch BuildVars here: its static initialiser
        // installs an uncaught handler of its own, and triggering that from
        // inside install() would nest the two handlers in load order.
        write("INFO", "KamiLog", "log opened", null);
    }

    // ------------------------------------------------------------------- API

    public static void i(String tag, String message) {
        write("INFO", tag, message, null);
    }

    public static void w(String tag, String message) {
        write("WARN", tag, message, null);
    }

    public static void e(String tag, String message) {
        write("ERROR", tag, message, null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        write("ERROR", tag, message, throwable);
    }

    public static void fatal(String message, Throwable throwable) {
        write("FATAL", "app", message, throwable);
    }

    public static String getPath() {
        return file != null ? file.getAbsolutePath() : null;
    }

    public static boolean isActive() {
        return !disabled && file != null;
    }

    // ----------------------------------------------------------------- engine

    private static void write(String level, String tag, String message, Throwable throwable) {
        if (disabled || file == null) {
            return;
        }
        final String line = format(level, tag, message, throwable);
        synchronized (lock) {
            try {
                if (written > MAX_SIZE) {
                    trim();
                }
                if (writer == null) {
                    writer = new OutputStreamWriter(new FileOutputStream(file, true));
                }
                writer.write(line);
                writer.write('\n');
                writer.flush();
                written += line.length() + 1;
            } catch (Throwable t) {
                // never let logging throw into the caller, and never retry
                disabled = true;
                closeQuietly();
            }
        }
    }

    /** Keeps only the newest half of the file, starting at a line boundary. */
    private static void trim() throws Exception {
        closeQuietly();
        File tmp = new File(file.getParentFile(), FILE_NAME + ".tmp");
        try (RandomAccessFile in = new RandomAccessFile(file, "r");
             FileOutputStream out = new FileOutputStream(tmp)) {
            long length = in.length();
            in.seek(length / 2);
            while (in.getFilePointer() < length && in.read() != '\n') {
                // advance to the next line boundary so the kept half starts clean
            }
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
        }
        if (tmp.renameTo(file)) {
            written = file.length();
        } else {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            written = 0;
        }
    }

    private static void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (Throwable ignored) {
            }
            writer = null;
        }
    }

    private static String format(String level, String tag, String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(256);
        synchronized (stamp) { // SimpleDateFormat is not thread-safe
            sb.append(stamp.format(new Date()));
        }
        sb.append(' ').append(level)
          .append(" [").append(tag).append("] ")
          .append(message == null ? "" : message);
        Throwable t = throwable;
        int depth = 0;
        while (t != null && depth < 4) {
            sb.append(depth == 0 ? "\n  " : "\n  caused by ");
            sb.append(t.getClass().getName());
            if (t.getMessage() != null) {
                sb.append(": ").append(t.getMessage());
            }
            StackTraceElement[] stack = t.getStackTrace();
            for (int i = 0; i < stack.length && i < 24; i++) {
                sb.append("\n    at ").append(stack[i]);
            }
            Throwable cause = t.getCause();
            t = cause == t ? null : cause;
            depth++;
        }
        return sb.toString();
    }
}
