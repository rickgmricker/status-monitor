package com.gmricker.monitor.ingame;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.gmricker.monitor.collector.WindowsCollectorService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Owns one named ETW session; starting capture never elevates or stops another tool's session. */
public final class PresentMonCapture implements AutoCloseable {
    private final Map<String, FrameStatistics> chains = new HashMap<>();
    private final Path executable;
    private final long targetPid;
    private final String session = "StatusMonitor-" + ProcessHandle.current().pid() + "-" + UUID.randomUUID();
    private volatile boolean stopped, running = true;
    private volatile Process process;
    private volatile InputStream serviceStream;
    private volatile Supplier<String> message = () -> tr("Iniciando coleta…");
    private volatile String diagnostic = "";
    private volatile boolean serviceFailed;
    private final AtomicBoolean terminating = new AtomicBoolean();

    public PresentMonCapture(long targetPid) throws IOException {
        this.targetPid = targetPid;
        executable = executablePath();
        Thread.ofPlatform().daemon(false).name("ingame-presentmon").start(this::collect);
    }
    public static Path executablePath() {
        try {
            var location = Path.of(PresentMonCapture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                var startup = location.getParent().resolve("tools/presentmon/PresentMon.exe");
                if (Files.isRegularFile(startup)) return startup;
                var bundled = location.getParent().getParent().resolve("tools/presentmon/PresentMon.exe");
                if (Files.isRegularFile(bundled)) return bundled;
            }
        } catch (Exception ignored) { }
        return Path.of("tools/presentmon/PresentMon.exe").toAbsolutePath();
    }
    private void collect() {
        try {
            var privileged = WindowsCollectorService.openCapture(targetPid);
            if (privileged != null) {
                serviceStream = privileged;
                if (stopped) { privileged.close(); return; }
                message = () -> tr("Aguardando quadros do jogo pelo serviço coletor…");
                try (var reader = new BufferedReader(new InputStreamReader(privileged, StandardCharsets.UTF_8))) {
                    readFrames(reader);
                }
                if (!stopped && !serviceFailed) message = () -> tr("Coleta encerrada pelo serviço coletor.");
                return;
            }
            if (!Files.isRegularFile(executable)) throw new IOException(tr("Coletor PresentMon não encontrado na instalação."));
            var launched = new ProcessBuilder(executable.toString(), "--process_id", "" + targetPid,
                    "--output_stdout", "--no_console_stats", "--v1_metrics", "--session_name", session,
                    "--terminate_on_proc_exit", "--no_track_gpu", "--no_track_input")
                    .directory(executable.getParent().toFile()).start();
            process = launched;
            Thread.ofVirtual().name("ingame-etw-flush").start(() -> {
                try (var trace = new WindowsTraceFlush(session)) {
                    while (!stopped && launched.isAlive()) {
                        trace.flush();
                        Thread.sleep(50);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException | LinkageError unavailable) {
                    // Preserve collection with ETW's default delivery if flushing is unsupported.
                }
            });
            Thread.ofVirtual().name("ingame-diagnostics").start(() -> {
                try (var reader = launched.errorReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) diagnostic = (diagnostic + " " + line.strip()).substring(
                                Math.max(0, diagnostic.length() + 1 + line.strip().length() - 1500));
                    }
                } catch (IOException ignored) { }
            });
            message = () -> tr("Aguardando quadros do jogo…");
            if (stopped) { terminate(launched); return; }
            try (var reader = launched.inputReader(StandardCharsets.UTF_8)) {
                readFrames(reader);
            }
            int code = launched.waitFor();
            if (!stopped) message = () -> code == 0 ? tr("Coleta encerrada: o jogo foi fechado.")
                    : tr("Falha na coleta (") + code + "). " + (diagnostic.isBlank()
                    ? tr("Verifique as permissões de rastreamento do Windows.") : diagnostic);
        } catch (IOException | InterruptedException failure) {
            if (!stopped) message = () -> tr("Não foi possível coletar: ") + failure.getMessage();
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            var privileged = serviceStream;
            if (privileged != null) try { privileged.close(); } catch (IOException ignored) { }
            if (process != null && process.isAlive()) terminate(process);
            running = false;
        }
    }
    private void readFrames(BufferedReader reader) throws IOException {
        var parser = new PresentMonCsv();
        String line;
        while (!stopped && (line = reader.readLine()) != null) {
            if (line.startsWith("#EXIT ")) {
                if (!line.equals("#EXIT 0")) {
                    serviceFailed = true;
                    String code = line.substring(6);
                    message = () -> tr("Falha na coleta pelo serviço (") + code + ").";
                }
                break;
            }
            if (line.equals("#END")) break;
            parser.accept(line).filter(frame -> frame.processId() == targetPid).ifPresent(frame -> {
                synchronized (chains) {
                    if (chains.size() >= 16 && !chains.containsKey(frame.swapChain())) return;
                    chains.computeIfAbsent(frame.swapChain(), key -> new FrameStatistics())
                            .add(frame.timeSeconds(), frame.frameMs());
                }
            });
        }
    }
    public FrameStatistics.Snapshot snapshot() {
        return snapshot(true);
    }
    public FrameStatistics.Snapshot snapshot(boolean updateRates) {
        synchronized (chains) {
            // Separate swap chains: combining them would inflate the game's FPS.
            return chains.values().stream().map(chain -> chain.snapshot(updateRates))
                    .max(Comparator.<FrameStatistics.Snapshot>comparingInt(value -> Double.isFinite(value.currentFps()) ? 1 : 0)
                            .thenComparingInt(FrameStatistics.Snapshot::samples)).orElse(FrameStatistics.Snapshot.EMPTY);
        }
    }
    public boolean isRunning() { return running && !stopped; }
    public String message() { return message.get(); }
    public Supplier<String> messageText() { return message; }
    @Override public void close() {
        stopped = true;
        var privileged = serviceStream;
        if (privileged != null) try { privileged.close(); } catch (IOException ignored) { }
        var active = process;
        if (active != null) Thread.ofPlatform().daemon(false).name("ingame-stop").start(() -> terminate(active));
    }
    private void terminate(Process active) {
        if (!active.isAlive() || !terminating.compareAndSet(false, true)) return;
        try {
            var stop = new ProcessBuilder(executable.toString(), "--session_name", session,
                    "--terminate_existing_session", "--no_console_stats", "--no_csv")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!stop.waitFor(3, TimeUnit.SECONDS)) stop.destroyForcibly();
            if (!active.waitFor(2, TimeUnit.SECONDS)) active.destroyForcibly();
        } catch (IOException | InterruptedException failure) {
            active.destroyForcibly();
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }
}
