package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.gmricker.monitor.ingame.OverlayShortcut;
import java.awt.*;
import java.awt.event.KeyEvent;
import javax.swing.*;

final class OverlayShortcutDialog {
    private OverlayShortcutDialog() { }
    static OverlayShortcut choose(Component parent) {
        var dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), tr("Atalho do overlay"), Dialog.ModalityType.APPLICATION_MODAL);
        var result = new OverlayShortcut[1];
        var body = new JPanel(new BorderLayout(0, 12));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        var instruction = new JLabel(tr("<html>Pressione a tecla desejada.<br>Pode combinar Ctrl, Alt e Shift. Esc cancela.</html>"));
        body.add(instruction);
        var cancel = new JButton(tr("Cancelar")); cancel.addActionListener(event -> dialog.dispose());
        body.add(cancel, BorderLayout.SOUTH);
        dialog.setContentPane(body); dialog.pack(); dialog.setResizable(false); dialog.setLocationRelativeTo(parent);
        KeyEventDispatcher dispatcher = event -> {
            if (!dialog.isActive() || event.getID() != KeyEvent.KEY_PRESSED) return false;
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) { dialog.dispose(); return true; }
            var shortcut = OverlayShortcut.fromKey(event.getKeyCode(), event.getModifiersEx());
            if (shortcut.isPresent()) { result[0] = shortcut.get(); dialog.dispose(); return true; }
            if (event.getKeyCode() != KeyEvent.VK_CONTROL && event.getKeyCode() != KeyEvent.VK_ALT && event.getKeyCode() != KeyEvent.VK_SHIFT)
                instruction.setText(tr("<html>Tecla indisponível. Tente outra.<br>Pode combinar Ctrl, Alt e Shift. Esc cancela.</html>"));
            return false;
        };
        var keyboard = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keyboard.addKeyEventDispatcher(dispatcher);
        try { dialog.setVisible(true); }
        finally { keyboard.removeKeyEventDispatcher(dispatcher); dialog.dispose(); }
        return result[0];
    }
}
