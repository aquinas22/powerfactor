package com.powerfactor.power;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BoilerModelTest {
    @Test
    void waterAndFuelLimitSteamAndItsEnergy() {
        BoilerModel boiler = new BoilerModel();
        boiler.load(600, false, 500, 500);
        var out = boiler.tick(true, 8, 0, 32000);
        assertEquals(2, out.waterUsed());
        assertEquals(20, out.steamMade());
        assertEquals(420, out.fuelSpent());
        assertEquals(80, boiler.fuel());
        assertEquals(out.waterUsed() * 10, out.steamMade());
        assertEquals(out.steamMade() * 20 + 20, out.fuelSpent());
    }

    @Test
    void cannotBoilUntilWarmAndNeverOverfillsItsTank() {
        BoilerModel boiler = new BoilerModel();
        boiler.addFuel(128000);
        for (int tick = 0; tick < 249; tick++) assertEquals(0, boiler.tick(true, 1000, 0, 32000).steamMade());
        assertEquals(80, boiler.tick(true, 1000, 0, 32000).steamMade());
        assertEquals(0, boiler.tick(true, 1000, 31999, 32000).steamMade());
    }

    @Test
    void dryBoilerTripsAndRecoversOnlyAfterCooling() {
        BoilerModel boiler = new BoilerModel();
        boiler.addFuel(1000000);
        for (int i = 0; i < 500; i++) boiler.tick(true, 0, 0, 32000);
        assertTrue(boiler.tripped());
        long remaining = boiler.fuel();
        for (int i = 0; i < 200; i++) assertFalse(boiler.tick(false, 1000, 0, 32000).firing());
        assertTrue(boiler.tripped());
        assertEquals(remaining, boiler.fuel());
        for (int i = 0; i < 60; i++) boiler.tick(false, 1000, 0, 32000);
        assertFalse(boiler.tripped());
    }

    @Test
    void pressureGovernorHoldsFuelAndResumesBelowItsLowerThreshold() {
        BoilerModel boiler = new BoilerModel();
        boiler.load(600, false, 100000, 100000);
        assertFalse(boiler.tick(true, 32000, 19200, 32000).firing());
        for (int i = 0; i < 400; i++) assertFalse(boiler.tick(true, 32000, 32000, 32000).firing());
        assertTrue(boiler.pressureHeld());
        assertFalse(boiler.tripped());
        assertEquals(100000, boiler.fuel());
        assertFalse(boiler.tick(true, 32000, 16000, 32000).firing());
        BoilerModel restored = new BoilerModel();
        restored.load(boiler.heat(), boiler.tripped(), boiler.fuel(), boiler.fuelMax(), boiler.pressureHeld());
        assertFalse(restored.tick(true, 32000, 16000, 32000).firing());
        assertTrue(boiler.tick(true, 32000, 12800, 32000).firing());
        assertFalse(boiler.pressureHeld());
    }

    @Test
    void sustainedPartialLoadCyclesPressureWithoutTripping() {
        BoilerModel boiler = new BoilerModel();
        boiler.addFuel(10000000);
        int steam = 0, holds = 0, production = 0;
        for (int i = 0; i < 6000; i++) {
            steam -= Math.min(steam, 40);
            var out = boiler.tick(true, 32000, steam, 32000);
            steam += out.steamMade(); production += out.steamMade();
            if (boiler.pressureHeld()) holds++;
            assertFalse(boiler.tripped());
            assertTrue(steam <= 19280);
        }
        assertTrue(holds > 0 && production > 200000);
    }

    @Test
    void redstonePausePreservesFuelAndCoolsTheVessel() {
        BoilerModel boiler = new BoilerModel();
        boiler.load(600, false, 10000, 10000);
        for (int i = 0; i < 10; i++) assertEquals(BoilerModel.Output.NONE, boiler.tick(false, 8000, 0, 32000));
        assertEquals(10000, boiler.fuel());
        assertTrue(boiler.heat() < 600);
    }
}
