# Power armor and exoskeleton — October 12, 2026

The full suit is craftable and rechargeable on Minecraft 26.2 / NeoForge 26.2.0.90.
The latest build is `build/libs/powerfactor-0.1.0.jar`. Recipes are available in JEI.

## Equipment

| Piece | Base battery | Hull armor | Powered features |
| --- | --- | --- | --- |
| Power Helmet | 200,000 FE | 2 | Damage shield; optional night vision and rebreather |
| Power Chestplate | 600,000 FE | 6 | Damage shield; removable Electric Jetpack and Wingsuit |
| Power Leggings | 400,000 FE | 5 | Damage shield; speed, jump and step servos |
| Power Boots | 200,000 FE | 2 | Damage shield; landing cushion |

The hull supplies iron-level armor even without charge. Each charged piece absorbs up to
8% of damage left after ordinary armor and other mitigation, paying 120 FE per damage point
absorbed. A partial battery grants only protection it can pay for. Plating modules add 2%
per module; the suit's combined shield is capped at 50%. Sources that bypass armor also
bypass this shield. Landing absorption is a separate boot ability.

Charge pieces individually in a Charger, or wear the suit and stand on a powered Charging
Pad. Armor uses the standard FE item capability for compatible chargers. The pad shares its
2,500 FE/t among eligible items and rotates allocation order, so every piece charges even
while several large batteries are empty. Dropped items charge too.

## Upgrade Table

Each piece has six sockets. Put the piece in the central device bay, then fit modules around
it. Taking the piece away takes its installed modules with it. The existing module limits,
duplicate checks, item persistence and capacity clamping apply.

| Module | Fits | Effect |
| --- | --- | --- |
| Battery | All pieces | Adds another base battery capacity, each; maximum 3 |
| Efficiency | All pieces | Costs ×0.8 per module, maximum 3 |
| Plating | All pieces | +2% shield contribution per module, maximum 3 |
| Speed | Leggings | +15% movement speed per module, maximum 3 |
| Jump Servo | Leggings | +0.08 jump strength per module, maximum 3 |
| Shock Absorber | Boots | Cushions 3 more blocks per module, maximum 3 |
| Night Vision | Helmet | Powered night vision, 4 FE/t before efficiency |
| Rebreather | Helmet | Restores air underwater, 2 FE/t before efficiency |
| Electric Jetpack | Chestplate | Fits as the actual removable item in one socket |
| Wingsuit | Chestplate | Fits as the actual removable item; can coexist with the jetpack |
| Thruster | Chestplate | Boosts fitted jetpack thrust, maximum 3 |

The fitted jetpack keeps its own charge and installed modules when removed, including across
saves. While fitted, flight uses the **chest battery**. Chest and jetpack Thruster/Efficiency
modules combine up to the existing three-module limit; they cannot multiply past it. An empty
chest plate does not grant flight. Shift-moving a jetpack into the table fits it into an
available chest socket, and a second jetpack is rejected.

## Movement and controls

Charged leggings add 25% movement speed, +0.10 jump strength and +0.5 step height. Speed modules
raise the speed bonus to 70%; three Jump Servos raise jump strength by +0.34 in total.
Walking costs 6 FE/t plus 3 per Speed module. Each jump costs 200 FE plus 60 per Jump Servo,
before efficiency. Servos require enough energy for a jump, avoiding a boost they cannot pay
for. Sneaking, disabling movement assists, removing the leggings or losing power restores
ordinary movement attributes. Creative flight, riding, gliding and swimming do not receive leg boosts.

Powered boots cushion up to five blocks beyond the player's safe fall distance, costing
100 FE per block absorbed before efficiency. Shock modules increase the limit to fourteen.
They provide partial cushioning when energy runs short; empty boots provide no powered
landing protection.

- **B** opens suit settings: live batteries, fitted functions, movement-assist, night-vision,
  jetpack-hover and wingsuit controls.
- **Jump** climbs with a fitted jetpack; **J** toggles hover by default; **Sneak** descends.
- **U** deploys a fitted wingsuit in midair; **Jump** folds it into jetpack thrust.
- The four-battery HUD and item bars show charge; hover over a suit row for exact FE amounts.

Hover's default moved from H to J to avoid Simple Voice Chat's H binding. Existing saved
bindings stay in place and can be changed in Minecraft Controls; the suit screen shows the
actual hover binding and also provides a hover button. All suit settings persist on the items.
Night vision respects external potion effects when the visor is disabled.

## Visuals and validation

The suit has industrial plate textures, shoulder armor, limb actuators, reinforced boots,
and a visor and chest indicator that glow while charged. Actuator pistons follow walking
limbs. A fitted jetpack uses the existing animated fans, exhaust, hover motion and sound.
The suit screen and HUD fit the 320-unit minimum GUI width; the jetpack gauge moves above
the hotbar at narrow widths.
Both suit and jetpack overlays respect F1 when hiding the HUD.

The current full suite has 54 unit tests and 67 in-world GameTests passing. Armor tests cover energy-bounded shielding,
actual damage-event reduction and bypass sources, partial landing absorption, servo shutdown,
jetpack insertion/removal/save persistence, charging all worn pieces, and visor/potion
coexistence, actual landing events and underwater air supply. Client checks verify survival flight and falling chest charge, full suit models,
and live settings. The resource checker validates 1,333 JSON resources and 24 animation sheets,
including equipment texture references.

```sh
python3 tools/gen_data.py
python3 tools/gen_textures.py
python3 tools/check_resources.py
./gradlew test build runGameTestServer --offline
```

The grapple and wingsuit are implemented; see [their guide](grapple-and-wingsuit.md).
The remaining vehicle, nuclear and automation plans remain in [TODO.md](../TODO.md).

[Suit model](previews/power-armor.png) · [Fitted jetpack](previews/power-armor-jetpack.png) · [Suit controls](previews/power-armor-gui.png) ·
[Compact suit controls](previews/power-armor-compact.png) · [Upgrade Table](previews/power-armor-upgrades.png)
