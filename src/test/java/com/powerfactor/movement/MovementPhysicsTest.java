package com.powerfactor.movement;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MovementPhysicsTest {
    @Test void reelShortensCableAndAcceleratesTowardTheAnchor() {
        var step = MovementPhysics.cable(MovementPhysics.Vector.ZERO, new MovementPhysics.Vector(10, 5, 0), 12, true, 1);
        assertTrue(step.velocity().x() > 0 && step.velocity().y() > 0);
        assertEquals(11.1, step.length(), 1e-8);
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
            assertTrue(step.velocity().length() <= MovementPhysics.MAX_FLIGHT_SPEED + 1e-8);
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
        assertTrue(MovementPhysics.glide(new MovementPhysics.Vector(20, -20, 20), 0, 90, false).length() <= MovementPhysics.MAX_FLIGHT_SPEED + 1e-8);
    }
    @Test void reelingKeepsFlybyMomentumAboveTheMotorClosingSpeed() {
        var motion = new MovementPhysics.Vector(0, 0, 4.2);
        var step = MovementPhysics.cable(motion, new MovementPhysics.Vector(20, 0, 0), 20, true, 1);
        assertEquals(4.2, step.velocity().z(), 1e-8);
        assertTrue(step.velocity().x() > .2);
        var alreadyClosing = MovementPhysics.cable(new MovementPhysics.Vector(4.5, 0, 0), new MovementPhysics.Vector(20, 0, 0), 20, true, 1);
        assertEquals(4.5, alreadyClosing.velocity().x(), 1e-8);
    }
    @Test void anUnpoweredTautLineDoesNotManufactureTangentialSpeed() {
        var step = MovementPhysics.cable(new MovementPhysics.Vector(0, 0, 1.4), new MovementPhysics.Vector(10, 0, 0), 10, false, 1);
        assertEquals(1.4, step.velocity().z(), 1e-8);
    }
    @Test void diveThenPullUpTradesAltitudeForSpeedAndSpeedForHeight() {
        var velocity = new MovementPhysics.Vector(0, -.15, 1.5);
        for (int i=0;i<30;i++) velocity = MovementPhysics.glide(velocity, 0, 45, false);
        assertTrue(velocity.z() > 1.5, "dive supplied no forward speed");
        double diveSpeed = velocity.z();
        for (int i=0;i<25;i++) velocity = MovementPhysics.glide(velocity, 0, -30, false);
        assertTrue(velocity.y() > 0, "pull-up supplied no climb");
        assertTrue(velocity.z() < diveSpeed, "climbing failed to spend forward momentum");
    }
    @Test void bankedTurnsPreserveAirspeedAndCannotInstantlyReverseDirection() {
        var before = new MovementPhysics.Vector(0, 0, 2.5);
        var after = MovementPhysics.glide(before, 180, 0, false);
        assertTrue(after.z() > 2.4, "turn instantly reversed velocity");
        assertTrue(Math.hypot(after.x(), after.z()) > 2.48, "bank destroyed momentum");
    }
    @Test void aLowSpeedPullUpStallsInsteadOfCreatingLiftFromNothing() {
        var velocity = MovementPhysics.Vector.ZERO;
        for (int i=0;i<20;i++) velocity = MovementPhysics.glide(velocity, 0, -65, false);
        assertTrue(velocity.y() < -.4);
    }
    @Test void sustainedFlightAndReelingRemainFiniteAndNonFiniteAnglesAreRejected() {
        var velocity = new MovementPhysics.Vector(0, -.1, 2);
        for (int i=0;i<10000;i++) {
            velocity = MovementPhysics.glide(velocity, i*.6, Math.sin(i*.015)*65, i%200>180);
            assertTrue(velocity.length() <= MovementPhysics.MAX_FLIGHT_SPEED+1e-8);
        }
        assertThrows(IllegalArgumentException.class, () -> MovementPhysics.glide(velocityForTest(), Double.NaN, 0, false));
        assertThrows(IllegalArgumentException.class, () -> MovementPhysics.cable(velocityForTest(), velocityForTest(), Double.NaN, true, 1));
    }
    private static MovementPhysics.Vector velocityForTest() { return new MovementPhysics.Vector(0, 0, 1); }
}
