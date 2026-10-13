package com.powerfactor.power;

/** Fuel-to-steam conversion, pressure governor and dry-fire trips, independent of Minecraft. */
public final class BoilerModel {
    public static final int WATER_PER_TICK = 8, EXPANSION = 10, FE_PER_STEAM = 20;
    public static final int BOILING_HEAT = 500, BURNER_LOSS = 20;
    public record Output(int waterUsed, int steamMade, int fuelSpent, boolean firing) {
        public static final Output NONE = new Output(0, 0, 0, false);
    }

    private final EngineHeat heat = new EngineHeat();
    private long fuel, fuelMax;
    private boolean pressureHeld;

    public boolean needsFuel() { return fuel <= 0; }
    public void addFuel(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Fuel energy must be positive");
        fuel = Math.addExact(fuel, amount);
        fuelMax = fuel;
    }

    public Output tick(boolean enabled, int water, int steam, int capacity) {
        if (water < 0 || steam < 0 || capacity <= 0 || steam > capacity) {
            throw new IllegalArgumentException("Invalid boiler tanks");
        }
        // Hysteresis prevents rapid burner cycling. A blocked outlet safely holds pressure without consuming fuel.
        if ((long) steam * 5 >= (long) capacity * 3) pressureHeld = true;
        else if ((long) steam * 5 <= (long) capacity * 2) pressureHeld = false;
        // An open, wet boiler settles around 625 heat. Dry firing drives it to red.
        double stress = water == 0 ? 1 : 0.5 + 0.5 * steam / capacity;
        boolean firing = heat.tick(enabled && fuel > 0 && !pressureHeld, stress, EngineHeat.MAX);
        if (!firing) return Output.NONE;
        int loss = (int) Math.min(BURNER_LOSS, fuel);
        fuel -= loss;
        int used = heat.heat() < BOILING_HEAT ? 0 : (int) Math.min(Math.min(WATER_PER_TICK, water),
                Math.min((capacity - steam) / EXPANSION, fuel / (EXPANSION * FE_PER_STEAM)));
        int made = used * EXPANSION;
        int cost = made * FE_PER_STEAM;
        fuel -= cost;
        return new Output(used, made, loss + cost, true);
    }

    public int heat() { return heat.heat(); }
    public int temperatureC() { return 20 + heat.heat() / 5; }
    public boolean tripped() { return heat.seized(); }
    public boolean pressureHeld() { return pressureHeld; }
    public long fuel() { return fuel; }
    public int fuelPermille() { return fuelMax == 0 ? 0 : (int) Math.clamp(1000.0 * fuel / fuelMax, 0, 1000); }
    public long fuelMax() { return fuelMax; }
    public void load(int thermal, boolean trip, long fuel, long maximum) {
        load(thermal, trip, fuel, maximum, false);
    }
    public void load(int thermal, boolean trip, long fuel, long maximum, boolean held) {
        heat.load(thermal, trip);
        this.fuel = Math.max(0, fuel);
        fuelMax = Math.max(this.fuel, maximum);
        pressureHeld = held;
    }
}
