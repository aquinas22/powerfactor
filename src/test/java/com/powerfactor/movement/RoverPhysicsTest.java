package com.powerfactor.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoverPhysicsTest {
    @Test void acceleratesToBoundedRoadAndBoostSpeeds() {
        var v = MovementPhysics.Vector.ZERO;
        for (int i = 0; i < 300; i++) v = MovementPhysics.drive(v, 0, 1, 0, false, false, true).velocity();
        assertTrue(v.z() >= 1.2 && v.z() < 1.29);
        for (int i = 0; i < 300; i++) v = MovementPhysics.drive(v, 0, 1, 0, false, true, true).velocity();
        assertTrue(v.z() >= 2.1 && v.z() <= 2.2);
    }
    @Test void brakesBeforeReversingAndStopsQuickly() {
        var v = new MovementPhysics.Vector(0, 0, 1.25);
        v = MovementPhysics.drive(v, 0, -1, 0, false, false, true).velocity();
        assertTrue(v.z() > 0 && v.z() < 1);
        for (int i = 0; i < 30; i++) v = MovementPhysics.drive(v, 0, -1, 0, false, false, true).velocity();
        assertTrue(v.z() < 0 && v.z() >= -0.4);
        for (int i = 0; i < 12; i++) v = MovementPhysics.drive(v, 0, 0, 0, true, false, true).velocity();
        assertTrue(Math.abs(v.z()) < 0.01);
    }
    @Test void tractionSteersWithReverseDirectionAndAirCannotAccelerate() {
        var right = MovementPhysics.drive(new MovementPhysics.Vector(0, 0, 1), 0, 1, 1, false, false, true);
        assertTrue(right.yaw() > 0 && right.velocity().x() < 0);
        var reverse = MovementPhysics.drive(new MovementPhysics.Vector(0, 0, -0.3), 0, -1, 1, false, false, true);
        assertTrue(reverse.yaw() < 0);
        var air = MovementPhysics.drive(MovementPhysics.Vector.ZERO, 0, 1, 1, false, true, false);
        assertEquals(0, air.velocity().z()); assertEquals(0, air.yaw()); assertEquals(-0.08, air.velocity().y());
    }
    @Test void noPowerCoastsAndLateralSlipLosesTraction() {
        var step = MovementPhysics.drive(new MovementPhysics.Vector(0.5, 0, 1), 0, 0, 0, false, false, true);
        assertEquals(0.38, step.velocity().x(), 0.0001); assertEquals(0.985, step.velocity().z(), 0.0001);
    }
}
