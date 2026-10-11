package com.powerfactor.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpgradeEffectsTest {
    @Test
    void stockIsNeutral() {
        UpgradeEffects u = UpgradeEffects.NONE;
        assertEquals(1, u.speedMultiplier());
        assertEquals(1, u.powerMultiplier(), 1e-9);
        assertEquals(1, u.reactiveMultiplier());
        assertEquals(0.75, u.tripVolts());
    }

    @Test
    void speedCostsEnergyAndEe() {
        UpgradeEffects u = new UpgradeEffects(4, 0, 0, false, false, 0);
        assertEquals(3, u.speedMultiplier());
        assertTrue(u.energyPerOperation() > 1.5);
        assertEquals(3, u.reactiveMultiplier());
    }

    @Test
    void efficiencyOffsetsSpeed() {
        UpgradeEffects u = new UpgradeEffects(2, 4, 0, false, false, 0);
        assertTrue(u.energyPerOperation() < 0.7, "four efficiency cards should beat two speed cards' cost");
    }

    @Test
    void threeCapacitorsCorrectAStockCrusher() {
        UpgradeEffects u = new UpgradeEffects(0, 0, 3, false, false, 0);
        double net = 15 - u.capacitorEe(1.0);
        double pf = 20 / Math.hypot(20, net);
        assertTrue(pf > 0.98, "pf " + pf);
    }

    @Test
    void vfdIgnoresFrequencyAndModestSag() {
        UpgradeEffects vfd = new UpgradeEffects(0, 0, 0, true, false, 0);
        assertEquals(1.0, vfd.motorSpeed(0.85, 57), 1e-9);
        assertTrue(UpgradeEffects.NONE.motorSpeed(0.85, 57) < 0.7);
        assertEquals(0.25, vfd.reactiveMultiplier(), 1e-9);
    }
}
