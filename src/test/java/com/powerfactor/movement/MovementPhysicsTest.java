package com.powerfactor.movement;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MovementPhysicsTest {
    @Test void reelShortensCableAndAcceleratesTowardTheAnchor() {
        var step = MovementPhysics.cable(MovementPhysics.Vector.ZERO, new MovementPhysics.Vector(10, 5, 0), 12, true, 1);
        assertTrue(step.velocity().x() > 0 && step.velocity().y() > 0);
        assertEquals(11.55, step.length(), 1e-8);
    }
    @Test void tautCablePreservesTangentialMomentumWhileRemovingOutwardMotion() {
        var step = MovementPhysics.cable(new MovementPhysics.Vector(-1, 0, 1), new MovementPhysics.Vector(10, 0, 0), 10, false, 1);
        assertEquals(0, step.velocity().x(), 1e-8);
        assertTrue(step.velocity().z() >= 1);
        assertEquals(10, step.length());
        assertTrue(step.taut());
    }
    @Test void slackCableDoesNotGrantAFreeMotorForce() {
        var velocity = new MovementPhysics.Vector(0.5, -0.2, 0.1);
        var step = MovementPhysics.cable(velocity, new MovementPhysics.Vector(2, 0, 0), 10, false, 1);
        assertEquals(velocity, step.velocity()); assertFalse(step.taut());
    }
    @Test void movementCapsStayFiniteAtExtremeInputs() {
        for (int i = 0; i < 1000; i++) {
            var step = MovementPhysics.cable(new MovementPhysics.Vector(100, 100, 100), new MovementPhysics.Vector(0, 100, 0), 2, true, 1.6);
            assertTrue(step.velocity().length() <= 2.2 + 1e-8);
        }
        assertThrows(IllegalArgumentException.class, () -> new MovementPhysics.Vector(Double.NaN, 0, 0));
    }
    @Test void airbrakeShedsSpeedAndLowAirspeedStalls() {
        var fast = new MovementPhysics.Vector(0, -0.1, 1.5);
        assertTrue(MovementPhysics.glide(fast, 0, 0, true).length() < fast.length());
        assertTrue(MovementPhysics.glide(MovementPhysics.Vector.ZERO, 0, 0, false).y() < 0);
    }
    @Test void divesGainForwardSpeedAndTurningRemainsBounded() {
        var velocity = new MovementPhysics.Vector(0, -0.1, 1);
        assertTrue(MovementPhysics.glide(velocity, 0, 45, false).z() > velocity.z());
        var turned = MovementPhysics.glide(velocity, -90, 0, false);
        assertTrue(turned.x() > 0);
        assertTrue(MovementPhysics.glide(new MovementPhysics.Vector(20, -20, 20), 0, 90, false).length() <= 2.4 + 1e-8);
    }
}
