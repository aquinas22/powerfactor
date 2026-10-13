package com.powerfactor.power;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ArmorPowerTest {
    @Test void absorptionStopsAtBatteryBudget() {
        var result = ArmorPower.absorb(5, 120, 120);
        assertEquals(1, result.amount()); assertEquals(120, result.energy());
        assertEquals(0, ArmorPower.absorb(5, 0, 120).amount());
    }
    @Test void fractionalProtectionPaysWithoutCreatingEnergy() {
        for (int energy = 0; energy < 100; energy++) {
            var result = ArmorPower.absorb(0.08, energy, 120);
            assertTrue(result.energy() <= energy);
            assertTrue(result.amount() * 120 <= result.energy() + 1e-10);
            assertTrue(result.amount() <= 0.08);
        }
        assertEquals(10, ArmorPower.absorb(0.08, 100, 120).energy());
    }
    @Test void invalidAbsorptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ArmorPower.absorb(Double.NaN, 10, 100));
        assertThrows(IllegalArgumentException.class, () -> ArmorPower.absorb(-1, 10, 100));
        assertThrows(IllegalArgumentException.class, () -> ArmorPower.absorb(1, -1, 100));
    }
    @Test void upgradedMovementHasBoundedCostsAndBonuses() {
        assertEquals(0.25, ArmorPower.speedBonus(0), 1e-8);
        assertEquals(0.7, ArmorPower.speedBonus(3), 1e-8);
        assertEquals(0.34, ArmorPower.jumpBonus(3), 1e-8);
        assertEquals(14, ArmorPower.landingLimit(3));
        assertEquals(ArmorPower.walkingCost(3), ArmorPower.walkingCost(100));
        assertTrue(ArmorPower.jumpCost(3) > ArmorPower.jumpCost(0));
    }
}
