# PowerFactor to-do

## October 13 quantum & logistics expansion
- [x] Storage network: controller, data cables, drive bay with six cell tiers, storage bus, importer/exporter, grid/crafting/pattern terminals, wireless terminals.
- [x] Autocrafting: crafting planner (unit-tested), crafting and quantum processors, molecular assembler, pattern provider, plan preview and job status.
- [x] Exotic ores (adamantium, faradaenium, cortanium, qubit crystal) with c: tags and config toggles; quantum component chain.
- [x] Exotic tool sets with tiered abilities; Starforge multiblock and paxels (only forgeable there).
- [x] Industrial and Quantum Quarry, Particle Collider, Zero-Point Energy Generator.
- [x] Arsenal: railgun, plasma rifle, singularity launcher, quantum blade, quantum disassembler.
- [x] Bright high-tech GUI theme across every screen.
- [x] Exotic armor sets with tiered full-set bonuses and ambient effects; baby-model layers; GameTest for phase/dynamo/crucible.
- [x] Shield Generator: 4-16 m force dome repelling hostile mobs and their projectiles; lattice dome renderer; console with radius control; GameTest and client check (repelled hostiles at night).
- [x] Space Elevator (capstone): anchor raises 64 Nanotube Tether segments (50k FE each), then beams down 120k FE/t day and night; tether/climber renderer visible to 512 blocks; console; climber ride (sneak-use); GameTest and a full real-time build in the client.
- [x] Mech Suit: three-block walker (walk/run/turn, 1.5-block step, jump-jets, landing stomp), walk-cycle renderer, HUD; GameTest and client check (walked and jetted via workbench `drive`).
- [x] Logic Controller: programmable redstone block (AND/OR/XOR/NAND/LATCH/TIMER/DELAY/COUNT, N 1-200), console with schematic lamps and a logic trace; GameTest and client check (TIMER set from the console).
- [x] Gravity Lift: pad projecting a lift column to the first solid block (sneak to sink, no fall damage, items ride too); renderer; GameTest and client check (player rose 16 blocks).
- [x] Hover-Bike: driven in the client via workbench `drive` (97 km/h boost, banking, trails).
- [x] Hover-Bike: repulsor cushion over land and water, banking, boost, Jump lift, 1M FE battery (Charging Pads reach it), wrench packing; HUD; GameTest and client check (workbench `spawn_vehicle`).
- [x] Orbital Collector: night-time, open-sky tracking dish (3M FE per pass) producing Cosmic Dust (9 → Exotic Matter) and rare drops; console with orbit/beam animation; GameTest and client check.
- [x] Robot Arm: powered pick-and-place between the inventories behind and in front, item filter, IK-animated arm carrying the stack; GameTest and client check.
- [x] Abandoned quantum labs: rare sealed deepslate rooms (dead collider, qubit-crystal decay, endermite rift, researcher's chest with a lab journal); config gate `generate_quantum_labs`; GameTest and client check (workbench `place_feature`).
- [x] Fluid storage on the network: 4k/64k/Quantum fluid cells in Drive Bays, transactional controller fluid capability (journalled rollback), Fluid Terminal with bucket fill/drain; unit test and conservation GameTest; client check.
- [x] Meteor strikes: night-time meteors near players (config: on/off, craters, chance), fiery trail renderer, scorched crater with a Meteorite core and exotic ore; Meteorite crushes into iron and rare drops; GameTest and client check (workbench `spawn_meteor`).
- [x] Fusion Reactor: 5×5×3 tokamak (shielding, field magnets, viewport), hydrogen fuel, 50M FE ignition, 250k FE/t, quench; console with live torus; GameTest and client check.
- [x] GUI pass in the client: Auto Capacitor Bank, Mod Bench and SCADA moved onto the bright chassis; label overflow fixes (terminal banner, collider, Starforge, turret, fusion).
- [x] Quantum Teleporter pads: entangled-pair linking, charge-up field, cross-dimension jumps, no ping-pong; GameTest and client check.
- [x] Plasma and railgun defence turrets: targeting rules, line of sight, animated heads with recoil and charge glow, radar console; GameTests.
- [x] Paxels redrawn in the classic paxel style; exotic tool textures cleaned up (no speckle).
- [x] JEI collider/Starforge pages and the "+" transfer into the pattern terminal checked in the client (workbench `show_recipes` action).
- [x] JEI categories for the collider and Starforge; JEI "+" transfer into the pattern terminal (any recipe; processing keeps amounts) and the crafting terminal (pulls from the network, shift fills even stacks).

## October 13 logistics pass
- [x] Item/fluid/steam pipe endpoint allow/block lists, four ghost references per world-direction face, and exact component matching.
- [x] Destination priorities with overflow fallback and fair rotation between equally ranked destinations.
- [x] Compact routing panel, filled-container fluid references, packed settings, saved disabled masks and immediate reciprocal-link checks.
- [x] Routing panel checked in the client (fixed dark-on-dark button labels in the shared ControlButton).

## Implemented in the October 12 pass
- [x] **Multiblocks**: refinery, wind tower, and pumpjack (two-block mechanism over nine foundation blocks).
      Missing parts have holograms; refinery and pumpjack casings expose controller energy and fluids.
- [x] Multiblock auto-build: sneak-use the controller with an empty hand or a structure part. Checks the
      whole structure and inventory before placing, consumes exact materials, respects placement protection.
- [x] Animation and model polish: pumpjack inertia and linked walking beam/pitman mechanism;
      detailed support frame, motor and skid; refinery flanges, service ladder, pipework, animated status textures.
- [x] GUI polish: live pumpjack preview, glass gauges, readout backgrounds, status lamps, tank/energy/tab
      tooltips, bounded titles and button labels, compact tabs at the minimum GUI width.
- [x] Resource generators repaired; model/texture/animation validation in `tools/check_resources.py`.
- [x] Industrial steam plant: large boiler and turbine/alternator multiblocks, exclusive casing ownership,
      fuel and fluid ports, steam piping, pressure governor, dry-fire trip, condensate recycling and SCADA.
- [x] Cooling tower with powered fan, open vent checks and a water return loop; geothermal and
      thermoelectric generators; passive rain catchment with a rising water surface.
- [x] Power armor: four FE batteries, damage shielding, articulated models, glowing visor, live HUD and
      suit settings; removable jetpack socket preserves the pack's charge and upgrades.
- [x] Exoskeleton servos and upgrade modules: speed, jump and step assist, powered landing cushions,
      optional night vision and rebreather. Charging pads share power across carried and worn equipment.
- [x] Powered grapple and wingsuit: moving anchors, motorized reeling, momentum-preserving swings,
      spread-arm fabric animation, banking, airbrakes and dual jetpack/wingsuit armor sockets.
- [x] Electric rover: single-seat server-driven traction, reverse, braking and boost; 500,000 FE battery,
      charging-pad support, one-block hill climbing, preserved wrench packing, repairs and animated chassis.

## Requested vehicles and flight (open)
- [ ] **Helicopter**: powered by FE (not jet fuel), vertical takeoff and hover, rotor animation, passenger seat, fuel-free flight limited by battery.
- [ ] **Mini airplane**: jet fuel (from the refinery), takeoff/landing, fuel gauge.

## Machine and progression polish
- [x] Existing machine and generator models, exposed tooling, cooling fins, coils and smooth animations.
- [x] Fixed six-face controls with symbols, direct redstone selectors, live model preview and closed-by-default panels.
- [x] Physical energy sockets and wind-base connection cabinet; external cables can extract through a complete tower.
- [x] Forge Hammer and Wire Cutters, hand plates/wire, more expensive wind turbine and a working graphite/arc bootstrap.
- [x] Fuller rubber trees, refreshed bark/leaves/rubber, slime/clay/coal rubber and improved latex processing.
- [x] Fair generator output, cached fluid-face filtering, common resource/tool tags and wrench protection events.
- [x] Recipe-specific saved progress, pause/resume for blocked outputs and missing supplies, clear GUI/SCADA stop reasons,
      and animation speed tied to actual processing and upgrades.
- [x] Whole output-batch reservations, stack-limit checks, missing-product protection and common-tag hand insulation.
- [x] Mixed 480 V copper runs use their weakest rating; endpoint changes rebuild grids; conflicting breaker chains stay isolated;
      fresh wires require committed FE before energizing. Washer model updates include tiny empty/full transitions.
- [x] Common-tag hydrogen, crude oil, steam and hot water; steam pipes preserve external fluid components;
      machine consumption and saving retain external fluid identity, with rollback and port restrictions tested.
- [x] Live voltage/current meter needles, cold-grid status, actual battery charge segments and transfer activity;
      external battery extraction appears in readouts, with committed/aborted transfer and snapshot checks.
- [x] Source-role filtering prevents grid-support feedback; ordinary loads take priority over reserve charging;
      exact battery menu energy and disabled/empty states, including redstone and surplus-routing checks.
- [x] Charged banks start cold grids and resume passive consumers after rewiring; direct atomic backup avoids idle drain
      and storage loops, shares output fairly and respects wire limits and line losses.
- [x] Flat Creative test launcher with cheats and mode switching. See [update notes](docs/machine-polish.md).

Development has resumed with the October 13 logistics pass.

## Requested features
- [x] **Electric rover**: 90 km/h drive / 158 km/h boost, native step climbing, FE powered and chargeable on a pad.
- [ ] **Mini airplane**: runs on jet fuel (from the refinery), takeoff/landing, fuel gauge.
- [ ] **Quarry**: area miner using the mining-drill head, FE powered, multiblock frame.
- [ ] **Powered elevator**.
- [ ] **Reactor** (nuclear or fusion) as late-game power; multiblock.

## Verify in a real client (can't be tested headless)
- [ ] Walkie-talkies with real microphones (two players, Simple Voice Chat); radio tower relay at long range.
- [ ] Listen to all synthesized sounds (none have been heard yet).
- [ ] Wrench: rotate/dismantle/strip by hand; jetpack with thruster modules.

## Known gaps / polish
- [ ] Existing pumpjacks need a 3x3 layer of nine Pumpjack Foundation blocks directly below them.
      Existing two-block refineries need rebuilding as the refinery multiblock.
- [ ] Mod targets Minecraft 26.2; the ATM11 server is 26.1.2 (needs a port to drop into that pack).

## Energy and naming
- [ ] Rename Creative Energy (CE) to ME (Minecraft Energy) in code, GUIs, and docs. Later, not now.

## Wiring and data
- [ ] Redstone power cables that carry both energy and data, Mekanism-style. More resources than copper or aluminum; the upgrade over existing cables.

## Power generation
- [ ] Solar panels (more than the current single block).
- [x] Industrial coal power plant: boiler + turbine + generator magnet. Multiblock, large model, 800 FE/t per turbine.
- [x] Detailed large models for the coal boiler and steam turbine, including pressure vessels, rotor, chimney and alternator fins.
- [ ] Detailed large models for future reactors and other major generators.

## Nuclear
- [ ] Uranium ore and processing.
- [ ] Small modular reactors (SMRs).
- [ ] Thorium and a thorium path.
- [ ] Fusion reactor (late game).

## Rare ores and fictional materials
- [ ] Rare fake ores that fit with other Forge (c: tag) ores and resources. Check the tags so other mods' recipes and worldgen pick them up.
- [x] Adamantium: super rare.
- [x] Faradaenium: found in the End.
- [x] Cortanium: indestructible. Spawns one per 16 chunks.

## Movement
- [x] Grapple hook: 64m cast, FE-powered reel, Sneak to swing, Jump/recast to release without losing momentum.
- [x] Wingsuit: explicit deployment, dive acceleration, banked steering and airbrakes; standalone or armor socket.

## Guns
- [ ] More guns with a COD-style feel (recoil, ADS, reload, hit feedback).

## Metallurgy
- [ ] Expand metallurgy: more alloys and processes beyond the current metals.

## Heat and steam
- [x] Steam network: coal boiler, insulated steam pipes, steam turbine. Built on `EngineHeat`; reactor connection follows nuclear implementation.
- [x] Cooling systems: hot condensate → powered cooling tower → cold boiler feed, conserving water. Towers require open vents.
- [ ] Attach the cooling/steam infrastructure to future reactors.
- [x] Geothermal generators placed next to lava or magma.

## Radiation and safety
- [ ] Radiation: uranium, thorium, and reactor areas expose players over time. **High priority (needed for nuclear).**
- [ ] Protection: hazmat suit, lead blocks.
- [ ] Dosimeter / Geiger counter item that warns before dangerous exposure.
- [ ] Reactor meltdown and containment: overheating with failed cooling causes an explosion.
- [ ] Spent fuel handling: storage or reprocessing, so the nuclear chain has an end.

## Logistics and automation
- [x] Item filters and sorters for item pipes.
- [x] Fluid filters and routing for fluid pipes.
- [ ] Inserters and extractors that move items between machines and chests without pipes.
- [ ] Mining drones and farming drones that automate ores and crops in a small area.

## Robots and builders
- [ ] Builder drone: flies to a multiblock and places missing parts. Implements the multiblock auto-build from the in-progress list. **Medium-high priority.**

## Machines and processing
- [x] Mechanical press, mixer, centrifuge, and sawmill: recipes, animated tooling, upgrades, sided automation, and JEI categories.
      Plate and gear press dies select the product for bulk input; replace after 512 jobs.
- [ ] Electrolytic separator: splits ores or fluids into several outputs.
- [ ] More alloy furnace recipes, feeding the metallurgy plans.

## Power and energy (additional)
- [ ] Battery tiers: basic, advanced, ultimate.
- [ ] Load management block: prioritizes or sheds load when power runs short.
- [ ] Storm (lightning) and tidal generators.
- [x] Thermoelectric generator: opposite hot and cold faces, up to 48 FE/t.

## Armor and tools
- [x] Power armor set: FE-powered helmet, chest, legs, boots. Removable jetpack fits an upgrade-table socket in the chest plate.
- [x] Exoskeleton leg pieces: speed, jump and step boosts, with FE payment and upgrade slots.
- [ ] Electric tool tiers with upgrade slots, using the existing upgrade table.

## Vehicles and movement (additional)
- [ ] Boat, alongside the rover and airplane.
- [ ] Hovercraft: fast water vehicle.
- [x] Shared movement foundation: bounded vector physics and steering in `MovementPhysics`, server-authoritative
      equipment state and costs, local prediction, native collision/glide handling. Vehicle plan in `docs/grapple-and-wingsuit.md`.

## Worldgen and exploration
- [ ] Derelict power plants and abandoned labs with loot.
- [ ] Oil fields and geysers as exploration rewards.
- [ ] Thorium and uranium deposits in biomes or deep layers, tied to the rare-ore plan.

## Quality of life and information
- [ ] Jade or WTHIT-style overlay showing machine stats when looking at a block.
- [ ] Grid map and visualizer: GUI showing power flow across the grid.
- [ ] Machine ownership and locks for multiplayer.

## Compatibility
- [ ] Create and Mekanism integration (both are in ATM11). Fit alongside them rather than duplicate them.

## More systems (from brainstorm)
- [ ] Automated turrets: sentries that target hostile mobs (and players, if set to). Fits the new guns. **High priority.**
- [ ] Demolition charges and dynamite: timed or remote-detonated explosives for structures.
- [x] Railgun and plasma weapons: power-hungry weapons drawing from the grid.
- [ ] Zipline: attaches between towers or pylons and carries the player across. Works with the grid and the grapple.
- [ ] Multi-stage production line: chain machines so one output flows straight into the next.
- [x] Storage network: our own central controller that sees every connected chest and drawer, with a remote crafting interface.
- [x] Autocrafting: request an item from the network and machines craft it on demand.
- [ ] Programmable logic controller (PLC): program simple rules to control machines and read SCADA data.
- [ ] Greenhouse: sealed multiblock with grow lamps and controlled water, for crops and rubber trees.
- [ ] Biofuel: turn crops, rubber, or ethanol into fuel for generators and vehicles.
- [ ] Hydroponics: crop growing without soil, fed by fluid pipes.
- [ ] Desalination plant: sea water becomes fresh water. Pairs with the cooling system. **High priority.**
- [ ] Water treatment and irrigation: clean and distribute water to farms.
- [x] Rain collector: passive water source during actual rain or thunderstorms; cover stops collection.
- [x] Industrial building blocks: oriented steel I-beams, grated catwalks, riveted steel, hazard blocks, and warning blocks.
- [ ] Industrial lighting: street lamps, floodlights, neon signs.
- [ ] Machine paint and skins: cosmetic colors for machines and cables.
- [ ] Power surge storms: weather event that overloads unshielded grids; surge protectors counter it.
- [ ] Meteor impacts: crash sites with rare materials, linked to the fictional ore plan.
- [ ] Mining veins: rich ore veins that a vein miner clears in one action.
- [ ] Power-based drawer storage: compact high-capacity storage using FE.

## Endgame quantum (ideas)
- [x] Quantum processor tier: crafting chain topping out at quantum chips.
- [ ] Quantum entanglement link: upgrade to the Ender wireless with unlimited range, at a high FE cost per transfer.
- [ ] Quantum crafting matrix: multiblock that crafts top-tier items in one step, with a quantum processor core.
- [x] Quantum storage cell (16M bytes, 255 types).
- [x] Zero-point energy generator.
- [x] Particle collider: smashes materials into the fictional ores, exotic matter and singularities.
- [ ] Matter fabricator: turns raw materials into any item in the mod at a steep FE cost. Last unlock.

## AI and robots (ideas)
- [ ] Industrial robot arm: multi-joint arm that picks up and places items. Programmable through the PLC.
- [ ] Robot workers: programmable robots that mine, haul, and farm on set routes, commanded by the PLC.
- [ ] Companion robot: follows the player, carries items, fights nearby threats.
- [ ] Robot factory: multiblock that builds robot workers from components. A progression goal.
- [ ] Smart grid AI: predicts demand, balances load, and sheds load before a blackout. Upgrades the load management block.
- [ ] Turret AI: sentries rank threats, call for help from nearby turrets, and hold fire on marked friendlies.
- [ ] Autopilot: drones, rovers, and airplanes fly or drive a set route on their own.
- [ ] AI core: computer block crafted from microprocessors and memory chips. Runs PLC rules and the smart grid. **High priority** (foundation for the robots, smart grid, and autopilot).

## Machine console and art pass (0.3.0)
- [x] Shared blue-steel theme, readable status text, calibrated progress rail and cycle timing hints.
- [x] Authored workshop housings, material grain, tooling details, service panels and rear fan cages.
- [x] Native model parsing, animated-part baking and software preview sheets.
- [ ] Graphical client review of the 0.3.0 screens with JEI and Windows 11 launch.

## Logistics, equipment and agent tools (0.3.0)
- [x] Reinforced pipes, machined routing fittings, connected reservoir shells and fluid surfaces.
- [x] Joined loaded-cell capacity, local shares, exact fluid components and retained drop metadata.
- [x] Tank capability rollback, destruction/splitting, survival placement/rejoining and save/reload checks.
- [x] Complete foundry armor models and 256px materials, separate adult/baby slot models and pose checks.
- [x] Smaller suit/grapple overlays, independent saved visibility and F8 master toggle.
- [x] Original Just Cause 3-inspired flight step, fast grapple pulls, retained launch momentum and W/S pitch.
- [x] JSON/MCP workbench: build/test/package jobs, native previews and guarded dedicated client actions.
- [ ] Hands-on movement comparison, current armor/HUD rendering and multiplayer latency testing on a desktop.
