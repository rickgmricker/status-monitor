package com.gmricker.monitor.window;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.awt.Frame;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Public methods and UI callbacks run on the Swing event dispatch thread. */
public final class WindowPinController implements AutoCloseable {
    private final JFrame window;
    private final Consumer<String> onFailure;
    private final PinVisibility visibility = new PinVisibility();
    private ScheduledExecutorService watcher;

    public WindowPinController(JFrame window, Consumer<String> onFailure) {
        this.window = window;
        this.onFailure = onFailure;
    }

    public boolean isSupported() {
        return System.getProperty("os.name", "").startsWith("Windows")
                && window.isAlwaysOnTopSupported();
    }

    public void setPinned(boolean pinned) {
        stopWatching();
        if (!pinned) {
            window.setAlwaysOnTop(false);
            apply(visibility.release());
            return;
        }
        if (!isSupported()) {
            onFailure.accept(tr("Fixação com detecção de tela cheia disponível apenas no Windows."));
            return;
        }
        var executor = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "monitor-fullscreen");
            thread.setDaemon(true);
            return thread;
        });
        watcher = executor;
        executor.execute(() -> {
            try {
                var detector = new WindowsFullscreenDetector(window);
                executor.scheduleWithFixedDelay(() -> {
                    try {
                        boolean fullscreen = detector.isFullscreenOnSameMonitor();
                        SwingUtilities.invokeLater(() -> {
                            if (watcher == executor && window.isDisplayable()) {
                                apply(visibility.update(fullscreen, window.isVisible(),
                                        (window.getExtendedState() & Frame.ICONIFIED) != 0));
                                window.setAlwaysOnTop(!fullscreen);
                            }
                        });
                    } catch (RuntimeException | LinkageError failure) {
                        fail(executor, failure);
                    }
                }, 0, 500, TimeUnit.MILLISECONDS);
            } catch (RuntimeException | LinkageError failure) {
                fail(executor, failure);
            }
        });
    }

    private void fail(ScheduledExecutorService source, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (watcher != source) {
                return;
            }
            setPinned(false);
            onFailure.accept(tr("Fixação desativada: ") + failure.getMessage());
        });
    }

    private void apply(PinVisibility.Action action) {
        switch (action) {
            case HIDE -> {
                window.setAlwaysOnTop(false);
                window.setVisible(false);
            }
            case SHOW -> {
                window.setAutoRequestFocus(false);
                window.setVisible(true);
            }
            case NONE -> { }
        }
    }

    private void stopWatching() {
        if (watcher != null) {
            watcher.shutdownNow();
            watcher = null;
        }
    }

    public void suspend() {
        stopWatching();
        visibility.release();
        window.setAlwaysOnTop(false);
    }

    @Override
    public void close() {
        stopWatching();
        visibility.release();
        window.setAlwaysOnTop(false);
    }
}
