# Electric rover — October 12, 2026

The rover is a craftable, rechargeable single-seat vehicle for Minecraft 26.2 / NeoForge 26.2.0.90.
Its recipe uses steel plates, a control circuit, two motors, a capacitor, rubber and a steel gear;
JEI shows the recipe. The current jar is `build/libs/powerfactor-0.1.0.jar`.

## Driving

Place the item on clear ground and right-click the rover to sit. Placement checks collisions,
world borders and interaction protection. The driver faces forward on mounting; the camera
follows subsequent chassis turns while retaining free look.

| Control | Action |
| --- | --- |
| W / S | Accelerate / brake into reverse |
| A / D | Steer |
| Jump | Brake |
| Sprint | Boost while driving forward |
| Sneak | Exit |

The 500,000 FE battery pays 20 FE/t with the drive motor active, or 50 FE/t for forward boost.
There is no motor cost while coasting or braking. Creative driving is free. Drive speed caps at
1.25 blocks/t (90 km/h); boost caps at 2.2 blocks/t (158.4 km/h); reverse caps at 0.4 blocks/t.
Traction damps side slip, and opposite throttle brakes before changing direction. Boost momentum
decays toward normal speed when boost is released. The chassis gets no acceleration or steering
in the air. Water/lava stop propulsion and apply drag/braking; it is a land vehicle.

Native Minecraft collision movement handles terrain and a 1.25-block step height. One-block
steps and slopes climb; taller walls stop the vehicle. Very steep terrain limits speed through
ordinary collision resolution. An unloaded chunk or world border stops horizontal motion.

## Charging, packing and repairs

Park above a powered Charging Pad to charge the entity battery. The pad shares its 2,500 FE/t
budget with other eligible equipment, with up to 1,000 FE/t per target. The packed item accepts
standard FE charging at up to 8,000 FE per insertion; compatible item chargers work too.

Sneak-use a wrench on a stopped, unoccupied rover to pack it into your inventory. Charge,
remaining hull and custom name stay on the item. An occupied or moving rover cannot be packed.
Use a steel ingot on a damaged rover to repair 10 HP, consuming one ingot outside Creative.

The hull has 80 HP. Destroying it ejects passengers and yields four steel ingots and two rubber
when entity drops are enabled; the battery is lost. Creative breaking discards it. Ordinary
falling currently does not damage the chassis. Vehicle access is shared; ownership locks and
multiplayer latency tuning remain future work.

## Simulation and visuals

The server owns position, collision and FE payment. Control packets require the actual driver,
reject non-finite values and clamp inputs. Ten ticks without fresh input, leaving the seat or
opening a client screen stops propulsion and applies brakes. Native client vehicle-position
ownership remains unset. Clients interpolate server motion and animate wheels from distance.

The model has a roll cage, seated cockpit, steering wheel, vented hood, battery deck, exposed
shock absorbers, powered headlights and brake lights. Front wheels steer and all wheels rotate;
the body eases into slope pitch and suspension motion. Motor pitch follows speed.

The dashboard displays speed, charge, hull and controls beside the driving view, clear of the
hotbar and dismount message. It moves to the upper right at narrow GUI widths to keep the
vehicle visible. Inactive wingsuit/jetpack gauges hide while seated. F1 hides the HUD.

## Verification

54 unit tests and 67 required in-world GameTests pass. Rover tests cover real one-block climbing
and wall collision, exact drive/boost payment, empty batteries, rejected passenger input, stale
input, charging a parked entity, packed item capability, entity save/load, occupied packing,
damage persistence, paid steel repair, wreck salvage and submerged motor shutdown/drag. Resource validation covers 1,333 JSON resources and
24 animation sheets.

Client checks cover normal item placement and mounting, survival acceleration, three successive
steps, 158 km/h boost, steering and world reload with the rider/battery intact. Initial two-second
driving used 800 FE (500,000 → 499,200); later hill driving and boost reached 493,980 FE. Sound
generation validates its seamless loop; listening with speakers remains on the project checklist.

```sh
python3 tools/gen_data.py
python3 tools/gen_sounds.py rover_motor
python3 tools/check_resources.py
./gradlew test build runGameTestServer --offline
```

[Chassis](previews/rover-model.png) · [Driving](previews/rover-driving.png) · [Boost](previews/rover-boost.png) · [Compact HUD](previews/rover-compact.png) · [Hidden HUD](previews/rover-hud-hidden.png)

The airplane and other planned vehicles remain in [TODO.md](../TODO.md). Shared movement
architecture is described in [the grapple/wingsuit guide](grapple-and-wingsuit.md).
