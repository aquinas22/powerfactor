# PowerFactor to-do

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
- [x] Flat Creative test launcher with cheats and mode switching. See [update notes](docs/machine-polish.md).

The feature backlog below is paused while testing this polish update.

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
- [ ] Adamantium: super rare.
- [ ] Faradaenium: found in the End.
- [ ] Cortanium: indestructible. Spawns one per 16 chunks.

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
- [ ] Item filters and sorters for item pipes.
- [ ] Fluid filters and routing for fluid pipes.
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
- [ ] Helicopter and boat, alongside the rover and airplane.
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
- [ ] Railgun and plasma cutter: power-hungry weapons drawing from the grid.
- [ ] Zipline: attaches between towers or pylons and carries the player across. Works with the grid and the grapple.
- [ ] Multi-stage production line: chain machines so one output flows straight into the next.
- [ ] Storage network: our own central controller that sees every connected chest and drawer, with a remote crafting interface.
- [ ] Autocrafting: request an item from the network and machines craft it on demand. **High priority.**
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
- [ ] Quantum processor tier: crafting chain topping out at quantum chips. Needed for fusion and the best machines.
- [ ] Quantum entanglement link: upgrade to the Ender wireless with unlimited range, at a high FE cost per transfer.
- [ ] Quantum crafting matrix: multiblock that crafts top-tier items in one step, with a quantum processor core.
- [ ] Quantum storage: very large storage with a fixed capacity per item type, fed by the storage network.
- [ ] Zero-point energy generator: final power source, drawing from the vacuum. Needs quantum components. Late-game answer to fusion.
- [ ] Particle accelerator: multiblock that smashes materials to make the fictional ores (adamantium, cortanium). Links the rare-ore plan to the endgame. **High priority.**
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
