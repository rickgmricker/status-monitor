package com.gmricker.monitor.collector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectorProtocolTest {
    @Test void translatesServiceErrorsWithoutChangingProtocolOrUnknownDetails() {
        var previous = com.gmricker.monitor.i18n.I18n.current();
        var format = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
        try {
            com.gmricker.monitor.i18n.I18n.setLanguage(com.gmricker.monitor.i18n.Language.ENGLISH);
            assertEquals("Process unavailable or access denied in this session.", assertThrows(IOException.class,
                    () -> CollectorProtocol.expectReady(input("ERR Processo indisponivel ou sem permissao nesta sessao.\n"))).getMessage());
            assertEquals("CAPTURE 1 123\n", new String(CollectorProtocol.captureRequest(123), StandardCharsets.US_ASCII));
            assertEquals("Target denied", assertThrows(IOException.class,
                    () -> CollectorProtocol.expectReady(input("ERR Target denied\n"))).getMessage());
        } finally {
            com.gmricker.monitor.i18n.I18n.setLanguage(previous);
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, format);
        }
    }
    @Test void sendsOnlyAVersionedNumericPid() {
        assertEquals("CAPTURE 1 123\n", new String(CollectorProtocol.captureRequest(123), StandardCharsets.US_ASCII));
        assertEquals("CAPTURE 1 4294967295\n", new String(CollectorProtocol.captureRequest(0xffff_ffffL), StandardCharsets.US_ASCII));
        for (long pid : new long[] {0, -1, 0x1_0000_0000L, Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> CollectorProtocol.captureRequest(pid));
    }
    @Test void consumesHandshakeWithoutEatingTheFirstCsvByte() throws Exception {
        var input = input("OK 1\nApplication,ProcessID\n");
        CollectorProtocol.expectReady(input);
        assertEquals('A', input.read());
    }
    @Test void refusesWrongVersionsTruncatedAndOversizedReplies() {
        for (var text : new String[] {"OK 2\n", "OK 1", "", "X".repeat(513) + "\n"})
            assertThrows(IOException.class, () -> CollectorProtocol.expectReady(input(text)));
        assertEquals("Target denied", assertThrows(IOException.class,
                () -> CollectorProtocol.expectReady(input("ERR Target denied\n"))).getMessage());
    }
    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }
}
