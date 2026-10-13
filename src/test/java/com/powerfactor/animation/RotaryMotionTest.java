package com.powerfactor.animation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RotaryMotionTest {
    @Test
    void startsGraduallyAndStopsAtItsCurrentPosition() {
        RotaryMotion motion = new RotaryMotion();
        motion.tick(0.09, 0.055, 0.085);
        assertTrue(motion.speed() > 0 && motion.speed() < 0.09);
        for (int i = 0; i < 160; i++) motion.tick(0.09, 0.055, 0.085);
        double running = motion.speed();
        motion.tick(0, 0.055, 0.085);
        assertTrue(motion.speed() > 0 && motion.speed() < running);
        for (int i = 0; i < 160; i++) motion.tick(0, 0.055, 0.085);
        float stopped = motion.phase(1);
        motion.tick(0, 0.055, 0.085);
        assertEquals(stopped, motion.phase(0));
        assertEquals(stopped, motion.phase(1));
    }

    @Test
    void interpolationRemainsContinuousWhenTheAngleWraps() {
        RotaryMotion motion = new RotaryMotion();
        for (int i = 0; i < 1000; i++) {
            motion.tick(0.09, 1, 1);
            assertEquals(0.09, motion.phase(1) - motion.phase(0), 0.000001);
            assertEquals((motion.phase(0) + motion.phase(1)) / 2, motion.phase(0.5F), 0.000001);
        }
    }
}
