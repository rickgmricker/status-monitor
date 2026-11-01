package com.gmricker.monitor.ingame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentMonCsvTest {
    @Test void acceptsRealSchemaWithQuotedNamesAndReorderedColumns() {
        var parser = new PresentMonCsv();
        assertTrue(parser.accept("Starting trace...").isEmpty());
        assertTrue(parser.accept("\uFEFFApplication,MsBetweenPresents,ProcessID,TimeInSeconds,SwapChainAddress").isEmpty());
        var frame = parser.accept("\"my, game.exe\",16.667,1234,1.25,0x123").orElseThrow();
        assertEquals(1234, frame.processId());
        assertEquals("0x123", frame.swapChain());
        assertEquals(16.667, frame.frameMs());
        assertEquals(1.25, frame.timeSeconds());
        assertTrue(parser.accept("invalid,NA,1234,1.3,0x123").isEmpty());
        assertTrue(parser.accept("truncated,12").isEmpty());
    }
    @Test void handlesEscapedQuotes() {
        assertEquals(java.util.List.of("a\"b", "c"), PresentMonCsv.split("\"a\"\"b\",c"));
    }
    @Test void acceptsLowercaseMsHeaderFromBundledCollector() {
        var parser = new PresentMonCsv();
        parser.accept("Application,ProcessID,SwapChainAddress,Runtime,TimeInSeconds,msBetweenPresents");
        var frame = parser.accept("<unknown>,2320,0x123,DXGI,0.629908,16.667").orElseThrow();
        assertEquals(2320, frame.processId());
        assertEquals(16.667, frame.frameMs());
    }
}
