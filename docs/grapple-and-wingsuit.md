# Grapple and wingsuit — October 12, 2026

Both devices are craftable on Minecraft 26.2 / NeoForge 26.2.0.90. Their recipes appear in JEI.
The current jar is `build/libs/powerfactor-0.1.0.jar`.

## Grapple

Carry a Powered Grapple, charge it in a Charger or on a Charging Pad, and aim at terrain or an entity.
Right-click with the device or press **G** to fire. Recast or press **Jump** to release an attached cable.
G also works with the device elsewhere in the inventory. Bindings are configurable in Minecraft Controls.

| Property | Base value |
| --- | --- |
| Battery | 120,000 FE |
| Cast range | 64 blocks |
| Launch | 100 FE |
| Motor | 12 FE/t while reeling |
| Reel shortening | 0.45 blocks/t |
| Speed module | +20% reel rate each, maximum three |

The Upgrade Table accepts Battery, Efficiency and Speed modules. Battery modules add base capacity;
Efficiency compounds costs by ×0.8 per module, rounded to at least 1 FE. Creative use has no FE cost.

The motor pulls by default. Hold **Sneak** to stop the motor and swing at the current cable length,
without consuming motor power. Tension removes outward motion and keeps tangential motion, so a
release carries momentum into the next maneuver. A moving entity carries its attachment point.
The projectile and cable use native collision checks; the player moves through ordinary Minecraft
collision handling. A taut cable suppresses fall accumulation while attached.

Reeling disconnects within two blocks of the anchor or when its battery cannot pay. Cables also
release when terrain disappears, an obstruction crosses the line, a moving target dies, the device
is dropped, the player mounts or dies, a required chunk unloads, range exceeds 72 blocks, or the
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
- Look down to dive and gain speed; level out to trade speed for lift.
- **A/D** steer with eased banking and camera roll.
- **Sneak** applies airbrakes. Low airspeed stalls and sinks.
- With a fitted jetpack, **Jump** folds the wingsuit and resumes powered thrust; **J** controls hover.
- **B** opens suit controls, with a wingsuit button and both flight bindings.

The suit uses no FE. Native Minecraft gliding supplies gravity, collision, flight rotation and
landing behavior. Additional bounded steering provides dive acceleration, airbrakes and stall
behavior; speed is capped at 2.4 blocks/t (48 m/s). Landing, swimming, riding or changing equipment
folds it. Deployment is stored on the worn item and restored when loading airborne.

The harness, stitched membranes and leg fabric unfold with a custom spread-arm player pose.
The wind loop follows speed and the HUD displays actual airspeed. Movement, suit and jetpack
overlays all respect F1. Sound files and playback were validated; listening with speakers and
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

The current full suite has 54 unit tests and 67 required in-world GameTests passing. Movement tests exercise real projectile
hits, reeling payment, swing conservation, release momentum, moving targets, blocked/broken
anchors, battery limits, deployment rules, actual socket insertion/removal and saved state.
Resource validation covers 1,333 JSON resources and 24 animation sheets.

Client checks verify a 31m attached cable, an airborne 17m swing, unfolded wings and actual
gliding, the dual-device suit GUI, and survival wing-to-jetpack handoff with chest charge falling
from 600,000 to 599,250 FE. The preview is a disposable world under `runs/polish-client`.
An actual world restart also restores airborne deployment and resumes gliding.

```sh
python3 tools/check_resources.py
./gradlew test build runGameTestServer --offline
```

[Airborne swing](previews/grapple-swing.png) · [Gliding](previews/wingsuit-flight.png) ·
[Dual flight controls](previews/dual-flight-gui.png) · [Wing-to-jetpack handoff](previews/wing-to-jetpack.png)
· [Deployed model](previews/wingsuit-model.png) · [Restored flight](previews/wingsuit-restored.png)
