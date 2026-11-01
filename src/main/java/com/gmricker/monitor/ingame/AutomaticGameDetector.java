package com.gmricker.monitor.ingame;

import java.util.function.*;

/** Only inspects the foreground fullscreen window; engine inspection is cached briefly. */
public final class AutomaticGameDetector {
    private final Supplier<GameWindows.Target> foreground;
    private final Predicate<GameWindows.Target> game;
    private final LongSupplier clock;
    private long cachedPid = -1, expires;
    private boolean cachedGame;

    public AutomaticGameDetector() {
        foreground = GameWindows::foregroundFullscreenWindow;
        game = target -> GameDetection.detect(target.pid(), target.windowClass());
        clock = System::nanoTime;
    }
    AutomaticGameDetector(Supplier<GameWindows.Target> foreground, LongPredicate game, LongSupplier clock) {
        this.foreground = foreground; this.game = target -> game.test(target.pid()); this.clock = clock;
    }
    public GameWindows.Target detect() {
        var candidate = foreground.get();
        if (candidate == null) return null;
        long now = clock.getAsLong();
        if (candidate.pid() != cachedPid || now >= expires) {
            cachedGame = game.test(candidate);
            cachedPid = candidate.pid();
            expires = now + 2_000_000_000L;
        }
        return cachedGame ? candidate : null;
    }
}
