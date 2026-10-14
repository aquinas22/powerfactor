package com.powerfactor.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.powerfactor.storage.core.CellInventory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CellInventoryTest {
    @Test void oneTypeFillsTheBudget() {
        var cell = new CellInventory<String>(1024, 63);
        long in = cell.insert("cobble", 100_000, false);
        assertEquals((1024 - 8) * 8, in);
        assertEquals(1024, cell.usedBytes());
        assertEquals(0, cell.insert("cobble", 1, false));
    }

    @Test void typesReserveBytesAndAreLimited() {
        var cell = new CellInventory<String>(1024, 3);
        for (String k : new String[] {"a", "b", "c"}) assertEquals(1, cell.insert(k, 1, false));
        assertEquals(0, cell.insert("d", 1, false));
        assertEquals(3 * 8 + 1, cell.usedBytes());
    }

    @Test void partialBytesFillBeforeNewBytes() {
        var cell = new CellInventory<String>(9, 63);
        assertEquals(8, cell.insert("x", 100, false));
        assertEquals(9, cell.usedBytes());
        cell.extract("x", 3, false);
        assertEquals(3, cell.room("x"));
    }

    @Test void simulationChangesNothing() {
        var cell = new CellInventory<String>(64, 63);
        assertEquals(10, cell.insert("x", 10, true));
        assertEquals(0, cell.items());
        cell.insert("x", 10, false);
        assertEquals(4, cell.extract("x", 4, true));
        assertEquals(10, cell.count("x"));
    }

    @Test void extractionRemovesEmptyTypes() {
        var cell = new CellInventory<String>(64, 1);
        cell.insert("x", 5, false);
        assertEquals(5, cell.extract("x", 99, false));
        assertEquals(0, cell.types());
        assertEquals(1, cell.insert("y", 1, false));
    }

    @Test void loadKeepsCounts() {
        var cell = new CellInventory<String>(64, 63);
        cell.load(Map.of("a", 5L, "b", 0L));
        assertEquals(1, cell.types());
        assertEquals(5, cell.items());
    }

    @Test void fluidCellsCountEightBucketsPerByte() {
        // 16 bytes: 8 reserved for the one type, 8 left = 64,000 mB.
        var cell = new CellInventory<String>(16, 15, 8_000);
        assertEquals(64_000, cell.insert("water", 100_000, false));
        assertEquals(16, cell.usedBytes());
        assertEquals(0, cell.insert("lava", 1, false), "no bytes left for a second type");
        assertEquals(1_500, cell.extract("water", 1_500, false));
        assertEquals(1_500, cell.room("water"), "a partly used byte still takes the remainder");
    }
}
