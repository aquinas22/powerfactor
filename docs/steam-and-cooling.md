# Steam plant and cooling — October 12, 2026

The coal plant now runs as a complete fuel, steam, electricity and water loop.
The mod targets Minecraft 26.2 / NeoForge 26.2.0.90 and builds to
`build/libs/powerfactor-0.1.0.jar`.

## Construction

Place the controller at the front centre of the footprint. The structure extends behind it;
rotating the controller rotates the hologram and assembly. Sneak-use with an empty hand or
a structure part to build from inventory, after clearing the indicated space.

| Controller | Footprint, width × height × depth | Parts beyond the controller |
| --- | --- | --- |
| Steam Boiler | 3 × 3 × 3 | 23 Boiler Casings, 1 Boiler Firebox, 2 Steam Drums |
| Steam Turbine | 3 × 2 × 5 | 26 Turbine Casings, 2 Steam Rotors, 1 Generator Magnet |
| Cooling Tower | 1 × 2 × 1 | One item places both halves; keep its vent open to the sky |

The chimney extends above the boiler's construction footprint. Leave room for it.
Recipes for the controllers, parts and utility machines are available in JEI.
Parts belong to one controller at a time. Breaking a required part stops the plant and
releases the surviving ports. Right-use any formed part to open its controller screen.
Formed parts expose their controller's relevant item, fluid and energy capabilities.
Power wiring and SCADA fiber can connect to the turbine's casing.

## Running the loop

1. Feed cold water and furnace fuel into the boiler. Its two tanks each hold 32,000 mB.
2. Run **Steam Pipes** from a boiler port to a turbine port. Sneak-use the boiler-facing
   endpoint to extract, as with other pipes. These pipes accept only steam, moving up to
   4,000 mB per extraction face every ten ticks. Glass piping shows travelling fluid.
3. Run ordinary **Fluid Pipes** from the turbine's hot-water output to the cooling tower.
4. Supply FE to the cooling tower. Pipe its cold-water output back to the boiler.
5. Connect machines, energy storage or power cables to the turbine or its formed casing.

The boiler heats for approximately 12.5 seconds from cold before making steam. It converts
up to 8 mB water into 80 mB steam each tick. The burner pauses at 60% steam pressure and
resumes at 40%, preserving fuel while idle. A blocked outlet safely holds pressure.
Dry firing can cause a thermal trip; provide water and allow cooling below 70 °C.

Each mB of steam carries 20 FE of thermal energy. At the default fuel setting, coal supplies
128,000 thermal FE to the industrial boiler; warm-up and burner operation also consume fuel.
The turbine synchronizes over approximately ten seconds and produces up to **800 FE/t**,
with **1,000 CE** apparent capacity and reactive-power support. Its governor responds to load,
and its visible rotor coasts after steam stops. Its FE buffer holds 128,000 FE.

Steam spent on generation or rotor loss returns as hot condensate: 10 mB steam becomes
1 mB water. Fractional conversion and the turbine's steam chamber persist across saves,
so the loop conserves water. A full condensate tank closes the inlet until it is drained.
Energy output shares power among distinct adjacent handlers to keep storage from starving
other machines or the cooling loop.

The cooling tower has two 16,000 mB tanks and an 8,000 FE buffer. It cools up to 16 mB/t
at 2 FE/mB in dry weather, or 32 mB/t at 1 FE/mB in rain. Solid or glass roofs block the vent.
Its fan starts and coasts smoothly; running cooling emits visible vapour.

## Water and heat supplies

| Utility | Requirement | Maximum output |
| --- | --- | --- |
| Geothermal Generator | Adjacent lava or magma | 64 FE/t from lava; 24 from magma |
| Thermoelectric Generator | Lava/magma opposite water, ice or snow | 48 FE/t from lava; 18 from magma |
| Rain Collector | Actual local rain reaching the open basin | 2 mB/t rain; 6 mB/t thunderstorm |

Heat generators use the best source instead of adding multiple faces. Their buffers hold
8,000 FE. A rain collector holds 16,000 mB water and consumes no FE. Its water surface rises
with the synchronized tank contents. A roof or clear weather stops collection; connect its
output to a boiler or storage tank for replenishment.

## Models and instrumentation

The assembled pressure vessels have domed ends, retaining bands, support skids and a
chimney or finned alternator. The turbine's exposed rotor and tower fan interpolate rotation
and coast smoothly. Controller skins, fluid textures and sounds indicate running machinery.
Unformed structures retain construction holograms.

The boiler screen displays fuel, cold water, steam, temperature, pressure and trip/hold state.
The turbine shows both fluid tanks, RPM, steam consumption and generated power. Cooling and
passive utilities have live rates and gauges; hovering shows fluid quantities and status help.
Electrical and redstone tabs use the shared compact layout. The passive rain collector and
fuel-fired boiler have appropriate configuration tabs.

## Validation

```sh
python3 tools/gen_data.py
python3 tools/check_resources.py
./gradlew test build runGameTestServer --offline
```

40 unit tests and 47 in-world GameTests pass. Tests cover fuel/steam conservation, warm-up,
pressure cycling under sustained load, dry-fire recovery, full condensate shutdown,
steam-only pipes, structure ownership, saved heat/fuel/RPM/fractional condensate, SCADA discovery,
covered vents, rain/cover behavior and hot/cold generator requirements. The complete plant
test powers a crusher and its cooling loop while conserving all 16,000 mB of initial water.
Resource checks validate 1,292 JSON files and 24 animation sheets.

[Steam plant](previews/steam-plant.png) · [Boiler GUI](previews/steam-boiler-gui.png) ·
[Turbine GUI](previews/steam-turbine-gui.png) · [Cooling tower GUI](previews/cooling-tower-gui.png) ·
[Rain basin](previews/rain-collector.png)

Microphone multiplayer behavior and the complete
sound library still need player testing. Nuclear reactors and their connection to these
steam/cooling systems remain on the roadmap in [TODO.md](../TODO.md).
