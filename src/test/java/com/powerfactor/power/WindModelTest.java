package com.powerfactor.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindModelTest {
    @Test
    void powerCurveIsCubicBetweenCutInAndRated() {
        assertEquals(0, WindModel.powerFraction(2.9));
        assertEquals(0, WindModel.powerFraction(WindModel.CUT_IN), 1e-9);
        assertEquals(1, WindModel.powerFraction(WindModel.RATED), 1e-9);
        assertEquals(1, WindModel.powerFraction(20), 1e-9);
        // Doubling the wind speed is far more than double the power.
        assertTrue(WindModel.powerFraction(10) > 4 * WindModel.powerFraction(5));
    }

    @Test
    void stormCutOutWaitsForTheWindToDrop() {
        WindModel m = new WindModel();
        assertEquals(WindModel.Mode.RUNNING, m.update(10, true, 1));
        assertEquals(WindModel.Mode.CUT_OUT, m.update(26, true, 1));
        // Still gusty: stays stopped.
        assertEquals(WindModel.Mode.CUT_OUT, m.update(22, true, 1000));
        // Calm, but not for long enough yet.
        assertEquals(WindModel.Mode.CUT_OUT, m.update(15, true, WindModel.RESTART_TICKS - 1));
        // A gust resets the wait.
        assertEquals(WindModel.Mode.CUT_OUT, m.update(21, true, 1));
        assertEquals(WindModel.Mode.CUT_OUT, m.update(15, true, WindModel.RESTART_TICKS - 1));
        assertEquals(WindModel.Mode.RUNNING, m.update(15, true, 1));
    }

    @Test
    void lowWindAndObstruction() {
        WindModel m = new WindModel();
        assertEquals(WindModel.Mode.LOW_WIND, m.update(2, true, 1));
        assertEquals(WindModel.Mode.STOPPED, m.update(10, false, 1));
        assertEquals(WindModel.Mode.RUNNING, m.update(10, true, 1));
    }

    @Test
    void pitchSpillsWindAboveRatedAndFeathersWhenStopped() {
        assertEquals(0, WindModel.pitch(WindModel.Mode.RUNNING, 8));
        assertTrue(WindModel.pitch(WindModel.Mode.RUNNING, 18) > 10);
        assertEquals(WindModel.FEATHER, WindModel.pitch(WindModel.Mode.CUT_OUT, 30));
        assertEquals(WindModel.IDLE_PITCH, WindModel.pitch(WindModel.Mode.LOW_WIND, 1));
        assertEquals(WindModel.MAX_RPM, WindModel.rpm(14), 1e-9);
        assertEquals(0, WindModel.rpm(2));
    }
}
