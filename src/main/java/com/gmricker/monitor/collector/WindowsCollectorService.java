package com.gmricker.monitor.collector;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.installer.InstallerPreferences;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** SCM starts a protected, fixed binary. Only the target PID crosses the local pipe. */
public final class WindowsCollectorService {
    public static final String NAME = "GmrickerMonitorCollector";
    private static final String PIPE = "\\\\.\\pipe\\GmrickerMonitorCollector.v1";
    private WindowsCollectorService() {}
    public enum State { NOT_INSTALLED, STOPPED, RUNNING, UNAVAILABLE }
    public static boolean isSupported() { return System.getProperty("os.name", "").startsWith("Windows"); }

    public static State state() {
        if (!isSupported()) return State.UNAVAILABLE;
        try (var manager = new W32ServiceManager(Winsvc.SC_MANAGER_CONNECT);
             var service = manager.openService(NAME, Winsvc.SERVICE_QUERY_STATUS)) {
            return service.queryStatus().dwCurrentState == Winsvc.SERVICE_STOPPED ? State.STOPPED : State.RUNNING;
        } catch (Win32Exception failure) {
            return failure.getErrorCode() == WinError.ERROR_SERVICE_DOES_NOT_EXIST ? State.NOT_INSTALLED : State.UNAVAILABLE;
        } catch (RuntimeException | LinkageError failure) { return State.UNAVAILABLE; }
    }

    /** Null preserves local unprivileged collection when no service is installed. Runs off the EDT. */
    public static InputStream openCapture(long targetPid) throws IOException {
        byte[] request = CollectorProtocol.captureRequest(targetPid);
        if (!isSupported()) return null;
        try (var manager = new W32ServiceManager(Winsvc.SC_MANAGER_CONNECT)) {
            W32Service opened;
            try { opened = manager.openService(NAME, Winsvc.SERVICE_START | Winsvc.SERVICE_QUERY_STATUS); }
            catch (Win32Exception failure) {
                if (failure.getErrorCode() == WinError.ERROR_SERVICE_DOES_NOT_EXIST) return null;
                throw failure;
            }
            try (var service = opened) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
                do {
                    var status = service.queryStatus();
                    if (status.dwCurrentState == Winsvc.SERVICE_STOPPED
                            && !Advapi32.INSTANCE.StartService(service.getHandle(), 0, null)) {
                        int error = Native.getLastError();
                        if (error != WinError.ERROR_SERVICE_ALREADY_RUNNING) throw new Win32Exception(error);
                    }
                    if (status.dwCurrentState == Winsvc.SERVICE_RUNNING) {
                        // Local impersonation is needed for the server's target-process access check.
                        HANDLE pipe = Kernel32.INSTANCE.CreateFile(PIPE, WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                                0, null, WinNT.OPEN_EXISTING, 0x00120000, null);
                        if (!WinBase.INVALID_HANDLE_VALUE.equals(pipe)) {
                            var connection = new PipeInput(pipe);
                            try {
                                var serverPid = new WinDef.ULONGByReference();
                                if (!Kernel32.INSTANCE.GetNamedPipeServerProcessId(pipe, serverPid)
                                        || serverPid.getValue().longValue() != Integer.toUnsignedLong(service.queryStatus().dwProcessId))
                                    throw new IOException(tr("A conexão não pertence ao serviço coletor instalado."));
                                var written = new IntByReference();
                                if (!Kernel32.INSTANCE.WriteFile(pipe, request, request.length, written, null)
                                        || written.getValue() != request.length) throw new IOException(tr("Falha ao solicitar a coleta ao serviço."));
                                CollectorProtocol.expectReady(connection);
                                return connection;
                            } catch (IOException | RuntimeException | Error failure) { connection.close(); throw failure; }
                        }
                    }
                    Thread.sleep(50);
                } while (System.nanoTime() < deadline);
                throw new IOException(tr("O serviço coletor não respondeu. Confira sua instalação nas configurações."));
            }
        } catch (Win32Exception failure) {
            throw new IOException(tr("Não foi possível iniciar o serviço coletor (Windows ") + failure.getErrorCode() + ").", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IOException(tr("Inicialização do coletor interrompida."), failure);
        }
    }

    public static Path payloadPath() {
        try {
            Path location = Path.of(WindowsCollectorService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                for (Path candidate : List.of(location.getParent().resolve("tools/collector"),
                        location.getParent().getParent().resolve("tools/collector")))
                    if (Files.isRegularFile(candidate.resolve("manage-service.ps1"))) return candidate;
            }
        } catch (Exception ignored) { }
        return Path.of("build/collector/payload").toAbsolutePath();
    }

    /** Explicit settings action. Windows requests UAC; no elevation during ordinary capture. */
    public static void manage(boolean install) throws IOException {
        if (InstallerPreferences.managedCollector())
            throw new IOException(tr("O serviço é gerenciado pelo instalador do Status Monitor."));
        Path payload = payloadPath();
        Path script = payload.resolve("manage-service.ps1");
        if (!Files.isRegularFile(script)) throw new IOException(tr("Arquivos do serviço ausentes. Execute a tarefa collectorPayload."));
        var execute = new ShellAPI.SHELLEXECUTEINFO();
        execute.fMask = 0x00000040 | 0x00000400; // SEE_MASK_NOCLOSEPROCESS | SEE_MASK_FLAG_NO_UI
        execute.lpVerb = "runas";
        execute.lpFile = Path.of(System.getenv("SystemRoot"), "System32/WindowsPowerShell/v1.0/powershell.exe").toString();
        execute.lpParameters = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File " + quote(script)
                + " -Action " + (install ? "Install" : "Remove") + " -Payload " + quote(payload);
        execute.nShow = WinUser.SW_HIDE;
        if (!Shell32.INSTANCE.ShellExecuteEx(execute)) {
            int error = Native.getLastError();
            throw new IOException(error == WinError.ERROR_CANCELLED ? tr("Autorização de administrador cancelada.")
                    : tr("Não foi possível abrir o instalador do serviço (Windows ") + error + ").");
        }
        try {
            Kernel32.INSTANCE.WaitForSingleObject(execute.hProcess, WinBase.INFINITE);
            var result = new IntByReference();
            if (!Kernel32.INSTANCE.GetExitCodeProcess(execute.hProcess, result) || result.getValue() != 0)
                throw new IOException(tr("Falha ao ") + (install ? tr("instalar") : tr("remover")) + tr(" o serviço coletor."));
        } finally { Kernel32.INSTANCE.CloseHandle(execute.hProcess); }
    }

    private static String quote(Path path) throws IOException {
        String value = path.toAbsolutePath().toString();
        if (value.contains("\"") || value.contains("\n") || value.contains("\r")) throw new IOException(tr("Caminho inválido para o coletor."));
        return '"' + value + '"';
    }

    public interface CancelApi extends Kernel32 { boolean CancelIoEx(HANDLE file, Pointer overlapped); }

    private static final class PipeInput extends InputStream {
        private final AtomicReference<HANDLE> handle;
        PipeInput(HANDLE pipe) { handle = new AtomicReference<>(pipe); }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(one[0]);
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) return 0;
            HANDLE pipe = handle.get();
            if (pipe == null) return -1;
            byte[] target = offset == 0 ? buffer : new byte[length];
            var count = new IntByReference();
            if (!Kernel32.INSTANCE.ReadFile(pipe, target, length, count, null)) {
                int error = Native.getLastError();
                if (handle.get() == null || error == WinError.ERROR_BROKEN_PIPE || error == WinError.ERROR_OPERATION_ABORTED) return -1;
                throw new IOException(tr("Falha ao ler o serviço coletor (Windows ") + error + ").");
            }
            if (target != buffer) System.arraycopy(target, 0, buffer, offset, count.getValue());
            return count.getValue() == 0 ? -1 : count.getValue();
        }
        @Override public void close() {
            HANDLE pipe = handle.getAndSet(null);
            if (pipe != null) {
                Native.load("kernel32", CancelApi.class, W32APIOptions.DEFAULT_OPTIONS).CancelIoEx(pipe, null);
                Kernel32.INSTANCE.CloseHandle(pipe);
            }
        }
    }
}
