package io.github.maxomatic458.conduit.net;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread factory for tunnel workers.
 */
public final class IrohThreads {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private IrohThreads() {}

    public static ThreadFactory factory(String prefix) {

        return runnable -> newThread(prefix, runnable);
    }

    public static Thread newThread(String prefix, Runnable runnable) {

        final Thread thread = new Thread(runnable, "iroh-" + prefix + "-" + COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    public static Thread start(String prefix, Runnable runnable) {

        final Thread thread = newThread(prefix, runnable);
        thread.start();
        return thread;
    }
}
