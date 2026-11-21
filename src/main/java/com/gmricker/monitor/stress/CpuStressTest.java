package com.gmricker.monitor.stress;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.LongUnaryOperator;

/**
 * CPU calculation workload with an optional deadline. Starts only on explicit request.
 * Every block has a known answer, so a wrong result under load reveals an unstable processor.
 */
public final class CpuStressTest implements AutoCloseable {
    static final int BLOCK_STEPS = 8192;
    private static final int SEEDS = 64;

    private final LongUnaryOperator block;
    private Run current;
    private boolean closed;

    /** durationMillis is zero before starting, or -1 for an indefinite run. */
    public record Status(boolean running, boolean stopped, int threads, long elapsedMillis, long durationMillis,
                         long verifiedBlocks, boolean unstable) {}

    private static final class Run {
        final long started = System.nanoTime();
        final long durationNanos;
        final long[] seeds = new long[SEEDS];
        final long[] expected = new long[SEEDS];
        final AtomicBoolean stopped = new AtomicBoolean();
        final AtomicBoolean unstable = new AtomicBoolean();
        final AtomicInteger remaining;
        final CountDownLatch finished;
        final Thread[] threads;
        final AtomicLongArray verified;
        volatile long ended;

        Run(int count, long durationNanos) {
            this.durationNanos = durationNanos;
            remaining = new AtomicInteger(count);
            finished = new CountDownLatch(count);
            threads = new Thread[count];
            verified = new AtomicLongArray(count);
        }

        void complete() {
            if (remaining.decrementAndGet() == 0) {
                ended = System.nanoTime();
            }
            finished.countDown();
        }

        long verifiedBlocks() {
            long total = 0;
            for (int index = 0; index < verified.length(); index++) total += verified.get(index);
            return total;
        }
    }

    public CpuStressTest() { this(CpuStressTest::block); }

    CpuStressTest(LongUnaryOperator block) { this.block = block; }

    /** Integer and floating-point chains; IEEE fma/sqrt make the answer identical on every correct CPU. */
    static long block(long seed) {
        long value = seed;
        double a = 1.0 + (seed & 0xffff) * 0x1p-16;
        double b = 0.5;
        for (int step = 0; step < BLOCK_STEPS; step++) {
            value = Long.rotateLeft(value * 6364136223846793005L + 1442695040888963407L, 17);
            a = Math.fma(a, 0.9999999, b);
            b = Math.sqrt(a + b);
        }
        return value ^ Double.doubleToRawLongBits(a) ^ Long.rotateLeft(Double.doubleToRawLongBits(b), 29);
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

    synchronized void startIndefinitely(int threads) {
        startRun(threads, -1);
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
        // Reference answers are computed before the load starts, while the processor is still idle.
        for (int index = 0; index < SEEDS; index++) {
            run.seeds[index] = 0x9E3779B97F4A7C15L * (index + 1);
            run.expected[index] = block.applyAsLong(run.seeds[index]);
        }
        for (int index = 0; index < threads; index++) {
            int worker = index;
            run.threads[index] = new Thread(() -> calculate(run, worker), "monitor-stress-" + (index + 1));
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

    private void calculate(Run run, int worker) {
        int seed = (worker * 7) % SEEDS;
        try {
            while (!run.stopped.get() && !Thread.currentThread().isInterrupted()
                    && (run.durationNanos < 0 || System.nanoTime() - run.started < run.durationNanos)) {
                if (block.applyAsLong(run.seeds[seed]) != run.expected[seed]) {
                    run.unstable.set(true);
                    run.stopped.set(true); // One wrong answer already proves the processor is unstable.
                    break;
                }
                run.verified.lazySet(worker, run.verified.get(worker) + 1);
                seed = (seed + 1) % SEEDS;
            }
        } finally {
            run.complete();
        }
    }

    public synchronized Status status() {
        if (current == null) {
            return new Status(false, false, 0, 0, 0, 0, false);
        }
        boolean running = current.remaining.get() > 0;
        long end = current.ended != 0 ? current.ended : System.nanoTime();
        boolean unstable = current.unstable.get();
        return new Status(running, current.stopped.get() && !unstable, current.threads.length,
                TimeUnit.NANOSECONDS.toMillis(end - current.started),
                current.durationNanos < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(current.durationNanos),
                current.verifiedBlocks(), unstable);
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
