package com.powerfactor.power;

/** Energy-bounded shield and landing absorption; all quantities are independent of Minecraft. */
public final class ArmorPower {
    public record Absorption(double amount, int energy) {}

    public static Absorption absorb(double requested, int available, int costPerUnit) {
        if (!Double.isFinite(requested) || requested < 0 || available < 0 || costPerUnit <= 0) {
            throw new IllegalArgumentException("Invalid armor absorption");
        }
        double amount = Math.min(requested, available / (double) costPerUnit);
        int cost = Math.min(available, (int) Math.ceil(amount * costPerUnit));
        return new Absorption(amount, cost);
    }

    public static int walkingCost(int speedModules) { return 6 + 3 * Math.clamp(speedModules, 0, 3); }
    public static int jumpCost(int jumpModules) { return 200 + 60 * Math.clamp(jumpModules, 0, 3); }
    public static double speedBonus(int speedModules) { return 0.25 + 0.15 * Math.clamp(speedModules, 0, 3); }
    public static double jumpBonus(int jumpModules) { return 0.10 + 0.08 * Math.clamp(jumpModules, 0, 3); }
    public static double landingLimit(int shockModules) { return 5 + 3 * Math.clamp(shockModules, 0, 3); }

    private ArmorPower() {}
}
