package com.powerfactor.radio;

/**
 * Whether two walkie-talkies can hear each other, and how much static there is. Handhelds reach
 * {@link #HANDHELD_RANGE} blocks; if both ends are near powered radio towers the towers relay the
 * call at any distance, with a clear signal.
 */
public final class RadioLink {
    public static final double HANDHELD_RANGE = 192, TOWER_REACH = 96;

    /** Static level 0..1, or a negative number if out of range. */
    public static double noise(double distance, boolean senderNearTower, boolean receiverNearTower) {
        if (senderNearTower && receiverNearTower) {
            return 0.05;
        }
        if (distance > HANDHELD_RANGE) {
            return -1;
        }
        double f = distance / HANDHELD_RANGE;
        return f * f;
    }

    private RadioLink() {}
}
