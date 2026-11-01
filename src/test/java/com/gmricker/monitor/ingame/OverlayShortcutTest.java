package com.gmricker.monitor.ingame;

import java.awt.event.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayShortcutTest {
    @Test void mapsFunctionKeysAndModifiersToWindows() {
        assertEquals(OverlayShortcut.DEFAULT, OverlayShortcut.fromKey(KeyEvent.VK_F3, 0).orElseThrow());
        assertEquals("F3", OverlayShortcut.DEFAULT.toString());
        var shortcut = OverlayShortcut.fromKey(KeyEvent.VK_F13, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK).orElseThrow();
        assertEquals(0x7C, shortcut.virtualKey());
        assertEquals(6, shortcut.modifiers());
        assertEquals("Ctrl + Shift + F13", shortcut.toString());
        assertEquals(0x87, OverlayShortcut.fromKey(KeyEvent.VK_F24, 0).orElseThrow().virtualKey());
    }
    @Test void rejectsReservedKeysAndModifierKeysAlone() {
        assertTrue(OverlayShortcut.fromKey(KeyEvent.VK_F12, 0).isEmpty());
        assertTrue(OverlayShortcut.fromKey(KeyEvent.VK_ESCAPE, 0).isEmpty());
        assertTrue(OverlayShortcut.fromKey(KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK).isEmpty());
        assertTrue(OverlayShortcut.fromKey(KeyEvent.VK_F3, InputEvent.META_DOWN_MASK).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new OverlayShortcut(0x72, 8));
    }
    @Test void mapsJavaSpecialKeysThatDifferFromWindows() {
        assertEquals(0x2D, OverlayShortcut.fromKey(KeyEvent.VK_INSERT, 0).orElseThrow().virtualKey());
        assertEquals(0x2E, OverlayShortcut.fromKey(KeyEvent.VK_DELETE, 0).orElseThrow().virtualKey());
        assertEquals(0x0D, OverlayShortcut.fromKey(KeyEvent.VK_ENTER, 0).orElseThrow().virtualKey());
    }
}
