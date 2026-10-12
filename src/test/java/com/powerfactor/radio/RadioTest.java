package com.powerfactor.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RadioTest {
    private static short[] sine(double f, int n, double amp) {
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            s[i] = (short) Math.round(Math.sin(2 * Math.PI * f * i / RadioFilter.RATE) * amp * 32767);
        }
        return s;
    }

    private static double rms(short[] s, int from) {
        double sum = 0;
        for (int i = from; i < s.length; i++) {
            sum += (s[i] / 32768.0) * (s[i] / 32768.0);
        }
        return Math.sqrt(sum / (s.length - from));
    }

    @Test
    void passesVoiceBandAndCutsBassAndTreble() {
        double voice = rms(new RadioFilter(1).process(sine(1000, 9600, 0.3), 0), 2000);
        double bass = rms(new RadioFilter(1).process(sine(60, 9600, 0.3), 0), 2000);
        double treble = rms(new RadioFilter(1).process(sine(9000, 9600, 0.3), 0), 2000);
        assertTrue(voice > 0.15, "1 kHz should come through, rms " + voice);
        assertTrue(bass < voice / 5, "60 Hz hum should be cut, rms " + bass);
        assertTrue(treble < voice / 5, "9 kHz should be cut, rms " + treble);
    }

    @Test
    void staticGrowsWithDistance() {
        double near = rms(new RadioFilter(2).process(new short[9600], 0), 0);
        double far = rms(new RadioFilter(2).process(new short[9600], 1), 0);
        assertTrue(far > near * 3, "far " + far + " near " + near);
    }

    @Test
    void rangeAndTowers() {
        assertEquals(0, RadioLink.noise(0, false, false), 1e-9);
        assertTrue(RadioLink.noise(RadioLink.HANDHELD_RANGE + 1, false, false) < 0);
        assertTrue(RadioLink.noise(5000, true, true) >= 0, "towers should relay at any range");
        assertTrue(RadioLink.noise(5000, true, false) < 0, "one tower isn't enough");
    }
}
