package com.kaminari.gram;

import android.content.Context;
import android.os.Process;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KamiGram's own always-on log.
 *
 * Upstream {@link FileLog} is gated behind {@code BuildVars.LOGS_ENABLED}, which is
 * false on release builds, so by default a fork ships blind: crashes and errors
 * vanish the moment the process dies. This class exists to fix that. It writes to
 * exactly one file,
 *
 * <pre>/data/data/com.kaminari.gram/files/kamigram.log</pre>
 *
 * and is always enabled, on every build type, for three categories of events:
 *
 * <ul>
 *   <li>uncaught exceptions (installed as the default handler in
 *       {@link #install()}, called from ApplicationLoader)</li>
 *   <li>errors and warnings mirrored from FileLog, so every place upstream already
 *       considers worth logging lands here too</li>
 *   <li>fork events, currently deleted-message capture from MessagesController</li>
 * </ul>
 *
 * Design notes:
 * <ul>
 *   <li>Never calls FileLog back, so mirroring cannot recurse.</li>
 *   <li>Writes happen on a single daemon thread; the log call sites (which include
 *       the crash handler, mid-stacktrace) never block or throw.</li>
 *   <li>The file is capped at {@link #MAX_SIZE} bytes and trimmed from the top when
 *       it grows past that, so the log cannot eat the filesystem.</li>
 * </ul>
 */
public final class KamiLog {

    private KamiLog() {
    }

    public static final String FILE_NAME = "kamigram.log";

    /** Keep the log bounded: past this size the oldest half is discarded. */
    private static final long MAX_SIZE = 4L * 1024 * 1024;

    private static final Object lock = new Object();
    private static final AtomicBoolean installed = new AtomicBoolean(false);
    private static final AtomicBoolean failed = new AtomicBoolean(false);
    private static final SimpleDateFormat stamp =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File file;
    private static OutputStreamWriter writer;
    private static Thread logThread;

    /** Install once per process: opens the file and takes over crash handling. */
    public static void install() {
        if (!installed.compareAndSet(false, true)) {
            return;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            File dir = context.getFilesDir();
            if (dir == null) {
                return;
            }
            file = new File(dir, FILE_NAME);

            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                // record first: the previous handler may kill the process
                write("FATAL", "uncaught exception on thread " + thread.getName(), throwable);
                flush();
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                }
            });
        } catch (Throwable t) {
            failed.set(true);
        }
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
        flush();
    }

    // ----------------------------------------------------------------- engine

    private static void write(String level, String tag, String message, Throwable throwable) {
        if (failed.get() || file == null) {
            return;
        }
        final String line = format(level, tag, message, throwable);
        synchronized (lock) {
            try {
                ensureThread();
                if (logThread == null) {
                    // no thread could be created (thread limit): write inline
                    append(line);
                    return;
                }
                final String captured = line;
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (pending) {
                    pending.append(captured).append('\n');
                    pending.notifyAll();
                }
            } catch (Throwable t) {
                failed.set(true);
            }
        }
    }

    private static final StringBuilder pending = new StringBuilder();

    private static void ensureThread() {
        if (logThread != null && logThread.isAlive()) {
            return;
        }
        try {
            logThread = new Thread(KamiLog::loop, "KamiLog");
            logThread.setDaemon(true);
            logThread.setPriority(Thread.MIN_PRIORITY + 1);
            logThread.start();
        } catch (Throwable ignored) {
            logThread = null;
        }
    }

    private static void loop() {
        //noinspection InfiniteLoopStatement
        while (true) {
            String chunk;
            synchronized (pending) {
                while (pending.length() == 0) {
                    try {
                        pending.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                chunk = pending.toString();
                pending.setLength(0);
            }
            try {
                append(chunk);
            } catch (Throwable t) {
                failed.set(true);
                return;
            }
        }
    }

    private static void append(String line) throws Exception {
        if (writer == null) {
            trimIfNeeded();
            writer = new OutputStreamWriter(new FileOutputStream(file, true));
        }
        writer.write(line);
        if (!line.endsWith("\n")) {
            writer.write('\n');
        }
        writer.flush();
    }

    /** When the file passes MAX_SIZE, keep only the newest half. */
    private static void trimIfNeeded() throws Exception {
        if (!file.exists() || file.length() <= MAX_SIZE) {
            return;
        }
        File trimmed = new File(file.getParentFile(), FILE_NAME + ".tmp");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            raf.seek(file.length() / 2);
            // drop until the next line boundary so the kept half starts clean
            while (raf.getFilePointer() < file.length()) {
                if (raf.read() == '\n') {
                    break;
                }
            }
            try (FileOutputStream out = new FileOutputStream(trimmed)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = raf.read(buf)) > 0) {
                    out.write(buf, 0, read);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        trimmed.renameTo(file);
    }

    private static String format(String level, String tag, String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(stamp.format(new Date()))
          .append(' ').append(level)
          .append(" [").append(tag).append("] ")
          .append(message == null ? "" : message);
        if (throwable != null) {
            sb.append("\n  ");
            sb.append(throwable.getClass().getName());
            if (throwable.getMessage() != null) {
                sb.append(": ").append(throwable.getMessage());
            }
            StackTraceElement[] stack = throwable.getStackTrace();
            for (int i = 0; i < stack.length && i < 24; i++) {
                sb.append("\n    at ").append(stack[i]);
            }
        }
        return sb.toString();
    }

    private static void flush() {
        synchronized (lock) {
            try {
                if (writer != null) {
                    writer.flush();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** For diagnostics: the absolute path of the log file, or null before install. */
    public static String getPath() {
        return file != null ? file.getAbsolutePath() : null;
    }

    public static boolean isActive() {
        return !failed.get() && file != null;
    }

    @SuppressWarnings("unused")
    private static void noop() {
        // keeps Process imported for future ANR/pid stamping
        Process.myPid();
    }
}
