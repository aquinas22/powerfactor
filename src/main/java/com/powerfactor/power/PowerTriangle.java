package com.powerfactor.power;

/**
 * PowerFactor's three power units, per tick:
 * <ul>
 *   <li><b>FE</b> (Forge Energy) is real power: the part that actually does work, and the only part
 *       other mods see.</li>
 *   <li><b>EE</b> (Ender Energy) is reactive power: the "imaginary" part that sloshes back and forth
 *       feeding motor magnetic fields. It does no work but still has to be carried.</li>
 *   <li><b>CE</b> (Creative Energy) is apparent power, the magnitude of FE + jEE. Generators, cables
 *       and transformers are rated in CE, so EE eats capacity that FE could have used.</li>
 * </ul>
 * Power factor is FE / CE.
 */
public record PowerTriangle(double fe, double ee) {
    public static final PowerTriangle ZERO = new PowerTriangle(0, 0);

    public double ce() {
        return Math.hypot(fe, ee);
    }

    /** 1.0 for a pure FE load; 1.0 by convention when nothing is flowing. */
    public double powerFactor() {
        double ce = ce();
        return ce == 0 ? 1.0 : Math.abs(fe) / ce;
    }

    /**
     * The most FE a source rated {@code ceRating} can deliver while also supplying {@code ee} of
     * reactive power. EE is served first (motors stall without it), FE gets what's left.
     */
    public static double maxFeFor(double ceRating, double ee) {
        double ee2 = ee * ee;
        double ce2 = ceRating * ceRating;
        return ee2 >= ce2 ? 0 : Math.sqrt(ce2 - ee2);
    }
}
