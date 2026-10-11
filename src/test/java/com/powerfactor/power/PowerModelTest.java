package com.powerfactor.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerModelTest {
    private static final TurbineModel.Spec SPEC = new TurbineModel.Spec(200, 250, 10, 200, 600);

    @Test
    void triangle() {
        PowerTriangle p = new PowerTriangle(200, 150);
        assertEquals(250, p.ce(), 1e-9);
        assertEquals(0.8, p.powerFactor(), 1e-9);
        assertEquals(1.0, PowerTriangle.ZERO.powerFactor());
    }

    @Test
    void eeEatsFeCapacity() {
        assertEquals(250, PowerTriangle.maxFeFor(250, 0), 1e-9);
        assertEquals(200, PowerTriangle.maxFeFor(250, 150), 1e-9);
        assertEquals(0, PowerTriangle.maxFeFor(250, 300), 1e-9);
    }

    @Test
    void producesNothingUntilSynchronized() {
        TurbineModel t = new TurbineModel(SPEC);
        t.addFuel(1_000_000);
        int ticks = 0;
        while (t.tick(true, 1000, 0) == 0) {
            ticks++;
            assertTrue(ticks < 1000, "never synchronized");
        }
        // 57 Hz is 95% of the 200-tick spin-up.
        assertEquals(190, ticks, 1);
        assertTrue(t.breakerClosed());
        assertEquals(200, t.tick(true, 1000, 0));
    }

    @Test
    void followsLoadAndSpendsFuelOnlyForWhatIsDelivered() {
        TurbineModel t = synchronizedTurbine();
        long before = t.fuelEnergy();
        assertEquals(50, t.tick(true, 50, 0));
        assertEquals(before - 50 - SPEC.idleLossFe(), t.fuelEnergy());
        assertEquals(0, t.tick(true, 0, 0));
    }

    @Test
    void reactiveDemandLimitsRealOutput() {
        TurbineModel t = synchronizedTurbine();
        // CE 250 with EE 200 leaves sqrt(250^2 - 200^2) = 150 FE.
        assertEquals(150, t.tick(true, 1000, 200));
        assertEquals(0.6, t.lastOutput().powerFactor(), 1e-9);
    }

    @Test
    void tripsAfterFuelRunsOut() {
        TurbineModel t = new TurbineModel(SPEC);
        t.addFuel(200 * 10 + 300); // enough to synchronize, then a little left over
        for (int i = 0; i < 200; i++) t.tick(true, 0, 0);
        assertTrue(t.breakerClosed());
        // Idle loss burns the last of the steam, then the rotor coasts below 54 Hz.
        int ticks = 0;
        while (t.breakerClosed()) {
            t.tick(true, 1000, 0);
            ticks++;
        }
        assertTrue(ticks > 1 && ticks < 100, "tripped after " + ticks);
        assertFalse(t.breakerClosed());
        assertEquals(0, t.tick(true, 1000, 0));
    }

    @Test
    void governorDroopsUnderLoadAndRecovers() {
        TurbineModel t = synchronizedTurbine();
        for (int i = 0; i < 100; i++) t.tick(true, 1000, 150); // full CE load: 200 FE + 150 EE
        assertTrue(t.hertz() < 58.5 && t.hertz() > 57.5, "full-load droop to about 58.2 Hz, got " + t.hertz());
        for (int i = 0; i < 100; i++) t.tick(true, 0, 0);
        assertEquals(60.0, t.hertz(), 0.01);
    }

    @Test
    void overloadSagsFrequencyFurther() {
        TurbineModel t = synchronizedTurbine();
        for (int i = 0; i < 100; i++) t.tick(true, 1000, 240); // EE leaves only 70 FE of a 1000 FE ask
        assertTrue(t.overloaded());
        assertTrue(t.hertz() < 57.5, "overloaded turbine should sag further, got " + t.hertz());
        assertTrue(t.breakerClosed(), "but stay above the 54 Hz trip");
    }

    @Test
    void absorbsLeadingEe() {
        TurbineModel t = synchronizedTurbine();
        t.tick(true, 1000, -100);
        assertEquals(-100, t.lastOutput().ee(), 1e-9);
        assertEquals(200, (int) t.lastOutput().fe());
    }

    @Test
    void redstoneShutdownClosesSteamValve() {
        TurbineModel t = synchronizedTurbine();
        assertEquals(0, t.tick(false, 1000, 0));
        assertTrue(t.rpm() < TurbineModel.SYNC_RPM);
    }

    private static TurbineModel synchronizedTurbine() {
        TurbineModel t = new TurbineModel(SPEC);
        t.addFuel(1_000_000);
        for (int i = 0; i < 200; i++) t.tick(true, 0, 0);
        assertTrue(t.breakerClosed());
        return t;
    }
}
