package com.powerfactor.radio;

import java.util.Random;

/**
 * Makes clean microphone audio sound like it came over a handheld radio: band-limited to the
 * 300-3000 Hz voice band, gently clipped, with hiss and crackle that grow toward the edge of range.
 * One instance per sender-receiver link, since the filters carry state between 20 ms frames.
 * Plain Java (16-bit mono PCM at 48 kHz) so it is unit-tested.
 */
public final class RadioFilter {
    public static final int RATE = 48_000;

    private final Biquad highPass = Biquad.highPass(320, 0.707);
    private final Biquad lowPass1 = Biquad.lowPass(2900, 0.707);
    private final Biquad lowPass2 = Biquad.lowPass(2900, 0.707);
    private final Random random;
    private double hissState;

    public RadioFilter(long seed) {
        this.random = new Random(seed);
    }

    /**
     * @param pcm   one frame of 16-bit mono samples
     * @param noise 0 (strong signal) .. 1 (barely in range): how much static to mix in
     */
    public short[] process(short[] pcm, double noise) {
        noise = Math.clamp(noise, 0, 1);
        short[] out = new short[pcm.length];
        double voiceGain = 1.0 - 0.45 * noise;
        for (int i = 0; i < pcm.length; i++) {
            double x = pcm[i] / 32768.0;
            x = lowPass2.process(lowPass1.process(highPass.process(x)));
            x = Math.tanh(x * 2.2) / Math.tanh(2.2) * voiceGain;   // little speaker, pushed hard
            // Hiss: band-limited noise; crackle: sparse clicks, both scaled by distance.
            hissState = 0.6 * hissState + 0.4 * (random.nextDouble() * 2 - 1);
            x += hissState * (0.012 + 0.09 * noise);
            if (random.nextDouble() < 0.0004 + 0.004 * noise) {
                x += (random.nextDouble() * 2 - 1) * 0.4 * noise;
            }
            out[i] = (short) Math.round(Math.clamp(x, -1, 1) * 32767);
        }
        return out;
    }

    /** A direct-form biquad (RBJ cookbook). */
    static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad lowPass(double f, double q) {
            double w = 2 * Math.PI * f / RATE, alpha = Math.sin(w) / (2 * q), c = Math.cos(w);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad highPass(double f, double q) {
            double w = 2 * Math.PI * f / RATE, alpha = Math.sin(w) / (2 * q), c = Math.cos(w);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
