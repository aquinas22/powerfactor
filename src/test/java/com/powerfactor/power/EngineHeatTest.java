package com.powerfactor.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EngineHeatTest {
    @Test
    void drainedEngineStaysCool() {
        EngineHeat h = new EngineHeat();
        for (int i = 0; i < 2000; i++) {
            assertTrue(h.tick(true, 0.0, EngineHeat.MAX));
        }
        assertEquals(0, h.stage());
    }

    @Test
    void blockedEngineClimbsToRedThenSeizes() {
        EngineHeat h = new EngineHeat();
        int ticks = 0;
        while (h.tick(true, 1.0, EngineHeat.MAX)) {
            ticks++;
            assertTrue(ticks < 5000, "never seized");
        }
        assertTrue(h.seized());
        assertEquals(3, h.stage());
        // Seized: won't run even when drained, until it cools to blue.
        int cooling = 0;
        while (!h.tick(true, 0.0, EngineHeat.MAX)) {
            cooling++;
        }
        assertFalse(h.seized());
        assertTrue(cooling > 200, "cooled too fast: " + cooling);
    }

    @Test
    void redstoneEngineNeverOverheats() {
        EngineHeat h = new EngineHeat();
        for (int i = 0; i < 5000; i++) {
            assertTrue(h.tick(true, 1.0, 600));
        }
        assertEquals(2, h.stage());
    }

    @Test
    void coolsWhenStopped() {
        EngineHeat h = new EngineHeat();
        for (int i = 0; i < 400; i++) h.tick(true, 1.0, EngineHeat.MAX);
        int hot = h.heat();
        for (int i = 0; i < 50; i++) h.tick(false, 1.0, EngineHeat.MAX);
        assertTrue(h.heat() < hot);
    }
}
