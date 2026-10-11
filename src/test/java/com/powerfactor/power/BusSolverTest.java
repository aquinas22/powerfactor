package com.powerfactor.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BusSolverTest {
    private static final double RATING = 512;

    @Test
    void idleBusIsNominal() {
        BusSolver.Result r = BusSolver.solve(RATING, 0, 0, 0, 0, 1.0);
        assertEquals(1.0, r.volts(), 1e-9);
        assertEquals(0, r.lossFe(), 1e-9);
        assertEquals(1.0, r.powerFactor());
    }

    @Test
    void poorPowerFactorSagsVoltageAndWastesFe() {
        // Same 300 FE of work, at PF 1.0 vs about 0.6 (with a generator covering the EE).
        BusSolver.Result good = BusSolver.solve(RATING, 300, 0, 0, 1000, 1.0);
        BusSolver.Result poor = BusSolver.solve(RATING, 300, 400, 0, 1000, 1.0);
        assertEquals(0.6, poor.powerFactor(), 0.01);
        assertTrue(poor.volts() < good.volts() - 0.05, "EE should sag voltage: " + good.volts() + " vs " + poor.volts());
        assertTrue(poor.lossFe() > 2.5 * good.lossFe(), "losses should rise with current squared");
        assertTrue(poor.lagging());
    }

    @Test
    void capacitorsRestoreVoltage() {
        BusSolver.Result uncorrected = BusSolver.solve(RATING, 300, 200, 0, 1000, 1.0);
        BusSolver.Result corrected = BusSolver.solve(RATING, 300, 200, 200, 1000, 1.0);
        assertEquals(0, corrected.netEe(), 1e-9);
        assertEquals(1.0, corrected.powerFactor(), 1e-9);
        assertTrue(corrected.volts() > uncorrected.volts());
        assertTrue(corrected.lossFe() < uncorrected.lossFe());
    }

    @Test
    void overCorrectionLeadsAndRaisesVoltage() {
        BusSolver.Result r = BusSolver.solve(RATING, 100, 0, 300, 0, 1.0);
        assertFalse(r.lagging());
        assertTrue(r.volts() > 1.05, "leading EE with nothing to absorb it should push voltage up: " + r.volts());
    }

    @Test
    void enginesAloneCannotSupplyEe() {
        // No synchronous generator: motor EE goes unsupplied and voltage collapses.
        BusSolver.Result r = BusSolver.solve(RATING, 100, 150, 0, 0, 1.0);
        assertTrue(r.volts() < 0.8, "volts " + r.volts());
        BusSolver.Result withTurbine = BusSolver.solve(RATING, 100, 150, 0, 1000, 1.0);
        assertTrue(withTurbine.volts() > 0.95);
    }

    @Test
    void motorSpeedFollowsVoltageSquaredAndFrequency() {
        assertEquals(1.0, BusSolver.motorSpeed(1.0, 60), 1e-9);
        assertEquals(0.81, BusSolver.motorSpeed(0.9, 60), 1e-9);
        assertEquals(0.95, BusSolver.motorSpeed(1.0, 57), 1e-9);
    }
}
