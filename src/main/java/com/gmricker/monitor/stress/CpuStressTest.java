package com.gmricker.monitor.stress;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** CPU calculation workload with an optional deadline. Starts only on explicit request. */
public final class CpuStressTest implements AutoCloseable {
    private static volatile long checksum;
    private Run current;
    private boolean closed;

    /** durationMillis is zero before starting, or -1 for an indefinite run. */
    public record Status(boolean running, boolean stopped, int threads, long elapsedMillis, long durationMillis) {}

    private static final class Run {
        final long started = System.nanoTime();
        final long durationNanos;
        final AtomicBoolean stopped = new AtomicBoolean();
        final AtomicInteger remaining;
        final CountDownLatch finished;
        final Thread[] threads;
        volatile long ended;

        Run(int count, long durationNanos) {
            this.durationNanos = durationNanos;
            remaining = new AtomicInteger(count);
            finished = new CountDownLatch(count);
            threads = new Thread[count];
        }

        void complete() {
            if (remaining.decrementAndGet() == 0) {
                ended = System.nanoTime();
            }
            finished.countDown();
        }
    }

    public void start(Duration duration) {
        start(Runtime.getRuntime().availableProcessors(), duration);
    }

    synchronized void start(int threads, Duration duration) {
        if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException(tr("Escolha uma duração de até 10 minutos."));
        }
        startRun(threads, duration.toNanos());
    }

    public synchronized void startIndefinitely() {
        startRun(Runtime.getRuntime().availableProcessors(), -1);
    }

    private void startRun(int threads, long durationNanos) {
        if (closed) {
            throw new IllegalStateException(tr("O teste já foi encerrado."));
        }
        if (current != null && current.remaining.get() > 0) {
            throw new IllegalStateException(tr("Já existe um teste em execução."));
        }
        if (threads < 1 || threads > Runtime.getRuntime().availableProcessors()) {
            throw new IllegalArgumentException(tr("Escolha uma quantidade válida de threads."));
        }
        var run = new Run(threads, durationNanos);
        for (int index = 0; index < threads; index++) {
            run.threads[index] = new Thread(() -> calculate(run), "monitor-stress-" + (index + 1));
            run.threads[index].setDaemon(true);
        }
        current = run;
        int started = 0;
        try {
            for (var thread : run.threads) {
                thread.start();
                started++;
            }
        } catch (RuntimeException | Error failure) {
            stop();
            for (int index = started; index < threads; index++) {
                run.complete();
            }
            throw failure;
        }
    }

    private static void calculate(Run run) {
        long value = Thread.currentThread().threadId();
        try {
            while (!run.stopped.get() && !Thread.currentThread().isInterrupted()
                    && (run.durationNanos < 0 || System.nanoTime() - run.started < run.durationNanos)) {
                for (int step = 0; step < 8192; step++) {
                    value = Long.rotateLeft(value * 6364136223846793005L + 1442695040888963407L, 17);
                }
                checksum = value; // Observable output prevents eliminating the calculation loop.
            }
        } finally {
            run.complete();
        }
    }

    public synchronized Status status() {
        if (current == null) {
            return new Status(false, false, 0, 0, 0);
        }
        boolean running = current.remaining.get() > 0;
        long end = current.ended != 0 ? current.ended : System.nanoTime();
        return new Status(running, current.stopped.get(), current.threads.length,
                TimeUnit.NANOSECONDS.toMillis(end - current.started),
                current.durationNanos < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(current.durationNanos));
    }

    public synchronized void stop() {
        if (current != null && current.remaining.get() > 0) {
            current.stopped.set(true);
            for (var thread : current.threads) {
                thread.interrupt();
            }
        }
    }

    public boolean awaitStopped(Duration timeout) throws InterruptedException {
        Run run;
        synchronized (this) { run = current; }
        return run == null || run.finished.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public synchronized void close() {
        stop();
        closed = true;
    }
}
