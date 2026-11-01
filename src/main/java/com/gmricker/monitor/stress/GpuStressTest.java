package com.gmricker.monitor.stress;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Owns the GPU context on a dedicated thread, including initialization and cleanup. */
public final class GpuStressTest implements AutoCloseable {
    interface Workload extends AutoCloseable {
        void initialize();
        void render();
        String device();
        @Override void close();
    }

    /** durationMillis is zero before starting, or -1 for an indefinite run. */
    public record Status(boolean running, boolean preparing, boolean stopped,
                         long elapsedMillis, long durationMillis, long frames, String device, String error) {}

    private static final class Run {
        final long durationNanos;
        final AtomicBoolean stopped = new AtomicBoolean();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile boolean running = true;
        volatile long started;
        volatile long ended;
        volatile long frames;
        volatile String device;
        volatile String error;

        Run(long durationNanos) { this.durationNanos = durationNanos; }
    }

    private final Supplier<Workload> factory;
    private Run current;
    private boolean closed;

    public GpuStressTest() { this(OpenGlGpuWorkload::new); }

    GpuStressTest(Supplier<Workload> factory) { this.factory = factory; }

    public synchronized void start(Duration duration) {
        if (duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException(tr("Escolha uma duração de até 10 minutos."));
        }
        startRun(duration.toNanos());
    }

    public synchronized void startIndefinitely() {
        startRun(-1);
    }

    private void startRun(long durationNanos) {
        if (closed) throw new IllegalStateException(tr("O teste já foi encerrado."));
        if (current != null && current.running) throw new IllegalStateException(tr("Já existe um teste de GPU em execução."));
        var run = new Run(durationNanos);
        current = run;
        var worker = new Thread(() -> execute(run), "monitor-gpu-stress");
        worker.setDaemon(true);
        try {
            worker.start();
        } catch (RuntimeException | Error failure) {
            run.running = false;
            run.finished.countDown();
            throw failure;
        }
    }

    private void execute(Run run) {
        Workload workload = null;
        try {
            workload = factory.get();
            workload.initialize();
            run.device = workload.device();
            if (!run.stopped.get()) {
                run.started = System.nanoTime();
                while (!run.stopped.get() && (run.durationNanos < 0 || System.nanoTime() - run.started < run.durationNanos)) {
                    workload.render();
                    run.frames++;
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            run.error = message(failure);
        } finally {
            try {
                if (workload != null) workload.close();
            } catch (RuntimeException | LinkageError failure) {
                if (run.error == null) run.error = message(failure);
            } finally {
                run.ended = System.nanoTime();
                run.running = false;
                run.finished.countDown();
            }
        }
    }

    private static String message(Throwable failure) {
        String text = failure.getMessage();
        return text == null || text.isBlank() ? tr("Não foi possível executar a carga gráfica.") : text;
    }

    public synchronized Status status() {
        if (current == null) return new Status(false, false, false, 0, 0, 0, null, null);
        long elapsed = current.started == 0 ? 0
                : (current.ended == 0 ? System.nanoTime() : current.ended) - current.started;
        return new Status(current.running, current.running && current.started == 0, current.stopped.get(),
                TimeUnit.NANOSECONDS.toMillis(elapsed), current.durationNanos < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(current.durationNanos),
                current.frames, current.device, current.error);
    }

    public synchronized void stop() {
        if (current != null && current.running) current.stopped.set(true);
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
