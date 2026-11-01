package com.gmricker.monitor.ingame;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.MSG;
import java.util.concurrent.*;

/** A blocking Win32 message loop: no keyboard polling and no hooks into the game. */
public final class GlobalOverlayHotkey implements AutoCloseable {
    private static final int UPDATE = 0x8001, QUIT = 0x0012, HOTKEY = 0x0312;
    private record Request(OverlayShortcut shortcut, CompletableFuture<Boolean> result) { }
    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final CompletableFuture<Integer> ready = new CompletableFuture<>();
    private final Runnable onToggle;
    private volatile boolean closed;

    public GlobalOverlayHotkey(Runnable onToggle) {
        this.onToggle = onToggle;
        Thread.ofPlatform().daemon(true).name("ingame-hotkey").start(this::run);
    }
    public synchronized CompletableFuture<Boolean> setShortcut(OverlayShortcut shortcut) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException(tr("Atalho encerrado.")));
        var result = new CompletableFuture<Boolean>();
        requests.add(new Request(shortcut, result));
        ready.whenComplete((threadId, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else if (User32.INSTANCE.PostThreadMessage(threadId, UPDATE, new WPARAM(0), new LPARAM(0)) == 0)
                result.completeExceptionally(new IllegalStateException(tr("Não foi possível atualizar o atalho.")));
        });
        return result;
    }
    private void run() {
        int registeredId = 0;
        OverlayShortcut current = null;
        try {
            var api = User32.INSTANCE;
            var message = new MSG();
            api.PeekMessage(message, null, 0, 0, 0); // Create this thread's native message queue.
            ready.complete(Kernel32.INSTANCE.GetCurrentThreadId());
            while (!closed && api.GetMessage(message, null, 0, 0) > 0) {
                if (message.message == UPDATE) {
                    Request request;
                    while (!closed && (request = requests.poll()) != null) {
                        var next = request.shortcut();
                        if (java.util.Objects.equals(next, current)) { request.result().complete(true); continue; }
                        if (next == null) {
                            if (registeredId != 0) api.UnregisterHotKey(null, registeredId);
                            registeredId = 0; current = null;
                            request.result().complete(true);
                            continue;
                        }
                        int nextId = registeredId == 1 ? 2 : 1;
                        if (!api.RegisterHotKey(null, nextId, next.modifiers() | 0x4000, next.virtualKey())) {
                            request.result().complete(false); // Keep the old working binding.
                            continue;
                        }
                        if (registeredId != 0) api.UnregisterHotKey(null, registeredId);
                        registeredId = nextId; current = next;
                        request.result().complete(true);
                    }
                } else if (message.message == HOTKEY && registeredId != 0 && message.wParam.intValue() == registeredId) {
                    onToggle.run();
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            ready.completeExceptionally(failure);
        } finally {
            if (registeredId != 0) User32.INSTANCE.UnregisterHotKey(null, registeredId);
            closed = true;
            Request request;
            while ((request = requests.poll()) != null) request.result().completeExceptionally(new IllegalStateException(tr("Atalho encerrado.")));
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        ready.thenAccept(threadId -> User32.INSTANCE.PostThreadMessage(threadId, QUIT, new WPARAM(0), new LPARAM(0)));
    }
}
