# Grapple and wingsuit — PowerFactor 0.3.0

Both devices are craftable on Minecraft 26.2 / NeoForge 26.2.0.90. Their recipes appear in JEI.
The current jar is `build/libs/powerfactor-0.3.0.jar`. This pass targets the standard Just
Cause 3 traversal feel: fast pulls, momentum-preserving releases, dives and pull-ups.
The implementation and tuning values are original; exact equivalence has not been measured.

## Grapple

Carry a Powered Grapple, charge it in a Charger or on a Charging Pad, and aim at terrain or an entity.
Right-click with the device or press **G** to fire. Recast or press **Jump** to release an attached cable.
G also works with the device elsewhere in the inventory. Bindings are configurable in Minecraft Controls.

| Property | Base value |
| --- | --- |
| Battery | 120,000 FE |
| Cast range | 96 blocks |
| Projectile speed | 12 blocks/tick |
| Launch | 100 FE |
| Motor | 12 FE/t while reeling |
| Closing speed target | 3.4 blocks/tick |
| Reel shortening | Follows closing motion, minimum 0.9 blocks/tick |
| Motor acceleration | Up to 0.28 blocks/tick² |
| Speed module | +20% reel rate each, maximum three |

The Upgrade Table accepts Battery, Efficiency and Speed modules. Battery modules add base capacity;
Efficiency compounds costs by ×0.8 per module, rounded to at least 1 FE. Creative use has no FE cost.

The motor pulls by default. Hold **Sneak** to stop the motor and swing at the current cable length,
without consuming motor power. Tension removes outward motion and keeps tangential motion, so a
release carries momentum into the next maneuver. A moving entity carries its attachment point.
The reel keeps tangent velocity and does not brake a faster launch down to its motor speed.
An unpowered line does not grant a repeated free speed multiplier.
The projectile and cable use native collision checks; the player moves through ordinary Minecraft
collision handling. A taut cable suppresses fall accumulation while attached.

Reeling disconnects within two blocks of the anchor or when its battery cannot pay. Cables also
release when terrain disappears, an obstruction crosses the line, a moving target dies, the device
is dropped, the player mounts or dies, a required chunk unloads, range exceeds 104 blocks, or the
hook reaches its 60-second lifetime. Moving anchors release after world reload; terrain anchors
retain their saved location. Entity attachment offsets follow translation, not target rotation.

The visual includes a barbed projectile, sagging segmented cable and animated forearm spool.
The HUD shows READY/FIRING/REEL/SWING, anchor distance and battery charge. Full-range rendering
does not inherit the tiny projectile's ordinary short visibility distance.

## Wingsuit

Wear the Wingsuit in the chest slot, or fit the actual item into a Power Chestplate at an Upgrade
Table. A chestplate can hold one wingsuit and one Electric Jetpack together. Removing either
device leaves the other installed; both survive saves as real removable items.

- **U** deploys or folds while airborne. Deployment requires being alive, out of water/lava,
  unmounted and outside creative flight or Levitation.
- **W/S** pitch down/up; mouse look also sets flight heading and pitch. Dive for speed,
  then pull up to trade forward momentum for altitude.
- **A/D** steer with eased banking and camera roll.
- **Sneak** applies airbrakes. Low airspeed stalls and sinks.
- With a fitted jetpack, **Jump** folds the wingsuit and resumes powered thrust; **J** controls hover.
- **B** opens the compact equipment controls, including independent suit/movement HUD buttons.
  **F8** temporarily hides/shows both overlays and preserves the individual preferences.

The suit uses no FE. Its complete aerodynamic step replaces vanilla Elytra forces through a
narrowly scoped mixin while native travel retains collision, flight rotation, damage and landing.
Forces run once inside travel. Vanilla Elytra behavior remains unchanged. Gravity, speed-dependent
lift, descent recovery, pull-ups, bounded turns, drag and airbrakes share one calculation.
The traversal safety cap is 5.5 blocks/t (110 m/s). Deployment/folding preserve launch speed.
Landing, swimming, riding or changing equipment
folds it. Deployment is stored on the worn item and restored when loading airborne.

The harness, stitched membranes and leg fabric unfold with a custom spread-arm player pose.
The wind loop follows speed and the HUD displays actual airspeed. Movement, suit and jetpack
overlays all respect F1. The suit HUD is 84 × 26 GUI units; grapple is 104 × 22, or 104 × 35 with
a wing. The movement panel moves upward when the suit panel is hidden. Visibility is saved in
`config/powerfactor-hud.json`. Speed adds eased camera FOV, respecting Minecraft's FOV-effects setting.
Sound files and playback were validated; listening with speakers and
multiplayer latency tuning remain client follow-ups.

## Shared movement and vehicle plan

`MovementPhysics` isolates finite, bounded vectors, cable tension and aerodynamic steering from
Minecraft APIs. Equipment packets carry controls and state; the server validates equipment,
anchors and energy payment. The client predicts the owner's movement and displays animation;
native entity synchronization distributes authoritative state to other players.

The [electric rover](electric-rover.md) now uses the vector and steering helpers, seat-validated
input packets, server-driven collision movement, traction, acceleration, braking, slope handling
and a rechargeable entity battery. The airplane still needs aerodynamic lift, stall speed,
throttle and jet-fuel storage. Future vehicles will preserve position and fuel across saves and
expose a clear dashboard. Ownership locks and the other vehicles remain unfinished in TODO.md.

## Verification

Movement tests exercise real projectile
hits, reeling payment, swing conservation, release momentum, moving targets, blocked/broken
anchors, battery limits, deployment rules, actual socket insertion/removal and saved state.
Additional regressions cover dive/pull-up trajectories, stalls, bounded banks, fast fly-bys,
finite motion and deployment/folding momentum. A native GameTest calls the transformed
Minecraft flight method and checks that wing forces run exactly once, then removes the wing
and verifies vanilla behavior returns. The current combined report is bundled with the release.

Earlier client checks verified a 31m attached cable, an airborne 17m swing, unfolded wings and actual
gliding, the dual-device suit GUI, and survival wing-to-jetpack handoff with chest charge falling
from 600,000 to 599,250 FE. The preview is a disposable world under `runs/polish-client`.
An earlier world restart also restored airborne deployment and resumed gliding. These existing
screenshots document the previous tuning; current movement still needs hands-on comparison
in a graphical client and multiplayer latency testing.

## Reference material

[Avalanche's game overview](https://avalanchestudios.com/games/just-cause-3) and
[Avalanche's traversal tips](https://blog.playstation.com/archive/2017/08/01/5-ways-to-unleash-creative-chaos-in-just-cause-3-out-today-on-playstation-plus)
describe the intended use of the traversal tools. Public
[JC3 console-mod source](https://github.com/aaronkirkham/jc3-console-thingy) exposes mod hooks
and game structures; its character header does not contain the original aerodynamic code.
The [JC3 format tools](https://github.com/tim42/gibbed-justcause3-tools-fork) likewise provide
community resource tooling. Avalanche's original movement source was not found in the
public material reviewed, and no game code was copied into this mod.

```sh
python3 tools/check_resources.py
./gradlew test build runGameTestServer --offline
```

[Airborne swing](previews/grapple-swing.png) · [Gliding](previews/wingsuit-flight.png) ·
[Dual flight controls](previews/dual-flight-gui.png) · [Wing-to-jetpack handoff](previews/wing-to-jetpack.png)
· [Deployed model](previews/wingsuit-model.png) · [Restored flight](previews/wingsuit-restored.png)
