package com.gmricker.monitor.instance;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.UUID;

/** File lock for ownership and filesystem notifications for window activation. */
public final class SingleInstance implements AutoCloseable {
    private static final String REQUEST = "activate.request";
    private final FileChannel channel;
    private final FileLock lock;
    private final WatchService watcher;
    private final Path directory;
    private Runnable activation;
    private boolean pending;
    private boolean closed;
    private String lastRequest;

    private SingleInstance(Path directory, FileChannel channel, FileLock lock, WatchService watcher) {
        this.directory = directory;
        this.channel = channel;
        this.lock = lock;
        this.watcher = watcher;
        var listener = new Thread(this::listen, "monitor-instance-activation");
        listener.setDaemon(true);
        listener.start();
    }

    /** Returns null after notifying the existing owner. No UI should be created in that case. */
    public static SingleInstance start(Path directory) throws IOException {
        directory = directory.toAbsolutePath();
        Files.createDirectories(directory);
        var watcher = directory.getFileSystem().newWatchService();
        FileChannel channel = null;
        FileLock lock = null;
        boolean transferred = false;
        try {
            // Register before taking ownership, so a simultaneous launch cannot lose its request.
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            channel = FileChannel.open(directory.resolve("instance.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException alreadyOwnedInThisJvm) {
                // The same ownership rule applies when invoked twice in one JVM.
            }
            if (lock == null) {
                notifyOwner(directory);
                return null;
            }
            var instance = new SingleInstance(directory, channel, lock, watcher);
            transferred = true;
            return instance;
        } finally {
            if (!transferred) {
                try {
                    if (lock != null) {
                        lock.release();
                    }
                } finally {
                    try {
                        if (channel != null) {
                            channel.close();
                        }
                    } finally {
                        watcher.close();
                    }
                }
            }
        }
    }

    private static void notifyOwner(Path directory) throws IOException {
        var temporary = Files.createTempFile(directory, "activate-", ".tmp");
        try {
            Files.writeString(temporary, UUID.randomUUID().toString());
            try {
                Files.move(temporary, directory.resolve(REQUEST),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, directory.resolve(REQUEST), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void onActivate(Runnable action) {
        boolean deliver;
        synchronized (this) {
            if (closed) {
                return;
            }
            activation = action;
            deliver = pending;
            pending = false;
        }
        if (deliver) {
            action.run();
        }
    }

    private void listen() {
        try {
            while (true) {
                var key = watcher.take();
                boolean requested = key.pollEvents().stream().anyMatch(event ->
                        event.kind() == StandardWatchEventKinds.OVERFLOW
                                || Path.of(REQUEST).equals(event.context()));
                if (requested) {
                    receiveRequest();
                }
                if (!key.reset()) {
                    return;
                }
            }
        } catch (ClosedWatchServiceException closedNormally) {
            // Closing the app unblocks take() without polling.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void receiveRequest() {
        try {
            var path = directory.resolve(REQUEST);
            if (Files.size(path) > 100) {
                return;
            }
            String request = Files.readString(path);
            UUID.fromString(request);
            Runnable action;
            synchronized (this) {
                if (closed || request.equals(lastRequest)) {
                    return;
                }
                lastRequest = request;
                action = activation;
                if (action == null) {
                    pending = true;
                }
            }
            if (action != null) {
                action.run();
            }
        } catch (IOException | IllegalArgumentException unavailable) {
            // A request can be replaced while it is read; a later event carries the new request.
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            watcher.close();
        } catch (IOException ignored) { }
        try {
            lock.release();
        } catch (IOException ignored) { }
        try {
            channel.close();
        } catch (IOException ignored) { }
        // Keep the lock file: deleting it creates races with another process acquiring it.
    }
}
