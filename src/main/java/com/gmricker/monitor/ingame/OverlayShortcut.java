package com.gmricker.monitor.ingame;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;

/** Win32 virtual key and modifier bits; stored independently of the keyboard layout. */
public record OverlayShortcut(int virtualKey, int modifiers) {
    public static final OverlayShortcut DEFAULT = new OverlayShortcut(0x72, 0); // F3
    public OverlayShortcut {
        if (!supported(virtualKey) || (modifiers & ~7) != 0) throw new IllegalArgumentException(tr("Atalho inválido."));
    }
    private static boolean supported(int key) {
        return (key >= 0x70 && key <= 0x87 && key != 0x7B) // F12 is reserved by Windows.
                || (key >= 'A' && key <= 'Z') || (key >= '0' && key <= '9')
                || (key >= 0x60 && key <= 0x69) || (key >= 0x21 && key <= 0x28)
                || key == 0x20 || key == 0x2D || key == 0x2E || key == 0x08 || key == 0x09 || key == 0x0D;
    }
    public static Optional<OverlayShortcut> fromKey(int javaKey, int modifiersEx) {
        if ((modifiersEx & InputEvent.META_DOWN_MASK) != 0) return Optional.empty();
        int key = javaKey;
        if (javaKey >= KeyEvent.VK_F13 && javaKey <= KeyEvent.VK_F24) key = 0x7C + javaKey - KeyEvent.VK_F13;
        else if (javaKey == KeyEvent.VK_INSERT) key = 0x2D;
        else if (javaKey == KeyEvent.VK_DELETE) key = 0x2E;
        else if (javaKey == KeyEvent.VK_ENTER) key = 0x0D;
        if (!supported(key)) return Optional.empty();
        int mods = ((modifiersEx & InputEvent.ALT_DOWN_MASK) != 0 ? 1 : 0)
                | ((modifiersEx & InputEvent.CTRL_DOWN_MASK) != 0 ? 2 : 0)
                | ((modifiersEx & InputEvent.SHIFT_DOWN_MASK) != 0 ? 4 : 0);
        return Optional.of(new OverlayShortcut(key, mods));
    }
    @Override public String toString() {
        int javaKey = virtualKey;
        if (virtualKey >= 0x7C && virtualKey <= 0x87) javaKey = KeyEvent.VK_F13 + virtualKey - 0x7C;
        else if (virtualKey == 0x2D) javaKey = KeyEvent.VK_INSERT;
        else if (virtualKey == 0x2E) javaKey = KeyEvent.VK_DELETE;
        else if (virtualKey == 0x0D) javaKey = KeyEvent.VK_ENTER;
        return ((modifiers & 2) != 0 ? "Ctrl + " : "") + ((modifiers & 1) != 0 ? "Alt + " : "")
                + ((modifiers & 4) != 0 ? "Shift + " : "") + KeyEvent.getKeyText(javaKey);
    }
}
