# Storage network, quantum machines, exotic materials and the arsenal

## Storage network and autocrafting

A **Storage Controller** powers a network of **Data Cables** (or glowing **Quantum Data Cables**). Feed it FE. Every device has a small idle draw, shown as the controller's load. With two controllers on one network, both show a conflict and go offline.

| Device | Purpose |
| --- | --- |
| Drive Bay | Ten storage cells (1k, 4k, 16k, 64k, 256k, Quantum 16M); priority setting |
| Storage Bus | Exposes an adjacent inventory as network storage; filters, priority |
| Importer / Exporter | Pull from / keep stocked an adjacent inventory; filters, speed cards |
| Grid / Crafting / Pattern Terminal | Browse, search (`@mod`, `#tag`, `$tooltip`), sort, craft, encode |
| Wireless Terminal | Crafting terminal in your hand; sneak-use a controller to link (64 blocks; quantum model unlimited in the same dimension) |
| Crafting Processor | Runs one autocraft job; the Quantum model runs four at sixteen dispatches a tick |
| Molecular Assembler | Executes crafting patterns with the real recipe; speed cards |
| Robot Arm | Reaches into the inventory behind it and sets stacks of up to 16 into the one in front (250 FE a move); use it with an item to filter |
| Logic Controller | Programmable redstone: inputs left/back/right, output front; AND, OR, XOR, NAND, latch, timer, delay, counter with N 1-200 |
| Gravity Lift | A pad that projects a lift column up to the first solid block (max 24); step in to rise, sneak to sink, no fall damage; 20 FE per entity per tick |
| Pattern Provider | Pushes processing-pattern inputs into an adjacent machine; machines eject products back into it |

**Fluids:** Fluid Cells (4k, 64k, Quantum) sit in Drive Bays beside item cells; each byte holds eight buckets. The Storage Controller is a fluid handler for pipes (push in, pull out, transaction-safe). The **Fluid Terminal** lists every fluid with its amount: click a fluid with an empty bucket or tank to fill it, or click with a full one to pour it in.

Cells store 8 items per byte, and each item type reserves 8 bytes. A cell keeps its contents when it is removed from a drive. Terminal clicks follow AE2 conventions: left-click takes a stack, right-click takes half, Shift-click moves to your inventory, clicking with an item stores it, and Ctrl- or middle-click orders a craft. The planner reports what it will use from storage, what it will craft and what is missing before you start. The crafting-status panel shows progress and can cancel a job, which returns everything to storage.

## Laser transport

Mid-game wireless logistics. Engrave glass and redstone with the sensor photomask in the Laser Engraver to make
Photonic Lenses (2 per wafer run); a Laser Node is a lens, two steel plates, a microprocessor and redstone (makes 2),
and the Laser Wrench is a lens on two steel ingots.

- Place a node against any chest, tank or machine (it attaches to the face you click).
- With the Laser Wrench, click one node and then another within 24 blocks in clear line of sight to fire a laser
  between them; click the same pair again to cut it. While a node is picked, a guide beam runs from it to your hand.
  Sneak-use the wrench in the air to drop the pick. Each node holds up to 8 links.
- Open a node to set each channel (Items, Fluids, Energy) to Off, Send or Receive. Senders push through any chain
  of linked nodes to every receiver, highest priority first. Items move 16 per quarter second, fluids 1,000 mB, and
  energy up to 100,000 FE/t. The item filter (6 slots, allow or deny) applies at both ends.
- Every transfer is one transaction, so nothing is lost or duplicated when a receiver fills up.
- Beams: a white carrier for every link, plus a strand in each enabled channel's colour (gold items, blue fluids,
  red energy) that thickens and carries racing packets while something is flowing. The node screen shows a live map
  of its links.

## Ore processing

| Route | Yield per raw ore | What it takes |
| --- | --- | --- |
| Crusher | 2 dust | power |
| Ore Washer | 3 dust + byproduct | water |
| Wash Plant | 4 concentrate (smelt each to an ingot) + placer finds | the full line, 1,000 mB water and 400 FE/t per load |
| Ore Dissolver | 4 crystals (crush each to dust) | 250 mB sulfuric acid |
| Ore Dissolver + Quantum Catalyst | 5 crystals | the catalyst wears one point per ore (96 uses) |

**Wash Plant** (placer-mine wash line, 3 wide x 8 deep x 3 high, built back from the shaker table): the Wash Plant
shaker table at the front, six Sluice Boxes running back (the 5th and 7th rows flanked by Wash Plant Frame posts),
three Trommel sections over the back half and a Feed Hopper on a frame column at the far end. Sneak-use the table to
see the hologram or build it from your inventory. Feed raw ore, ore blocks, gravel or sand into the hopper (or the
table) and pipe water and power into any part. Ore becomes four concentrate plus byproducts: gold nuggets 20%, flint
8%, lapis 3%, diamond 0.6%, emerald 0.4%. Gravel and sand pan out gold (30%), iron nuggets and flint. The renderer
draws the drum turning with ore tumbling inside under spray bars, water sheeting down a 1:10 incline over the
riffles, concentrate glittering behind them in the metal's colour, and the shaker deck chattering.

**Chemistry**: spin sulfur out of coal (50%) or netherrack in a Centrifuge; the Chemical Reactor turns one sulfur,
500 mB water and 250 mB hydrogen (from an Electrolyser) into 500 mB sulfuric acid. The Ore Dissolver leaches raw ore
in acid and grows crystals; crystals crush to dust. Sulfuric acid burns creatures that swim in it and dissolves
dropped items. Metals: iron, gold, copper, zinc, tin, lead, silver and aluminium (bauxite). JEI shows all three
machines' recipes.

## Exotic materials and tools

| Material | Where | Tools |
| --- | --- | --- |
| Adamantium | Deepslate, y -64 to -36, never in caves | 3×3 excavation, crushing sword |
| Faradaenium | Glowing veins in End stone | Magnetic drops, chain-lightning sword |
| Cortanium | One block in roughly 16 chunks, y -60 to -48, nearly indestructible | Unbreakable, vein mining, withering sword |
| Qubit Crystal | Deepslate, glowing violet | Root of the quantum chain |
| Quantum Alloy | Adamantium + Quantum Dust (alloy furnace) | All abilities, 5×5, rift-tearing sword |

**Abandoned quantum labs** lie sealed deep in the deepslate (y -52 to -12, rare). Each holds a dead Particle Collider, qubit crystal growing through the walls, a rift that still leaks endermites, and the last researcher's chest with her lab journal. The common config's `generate_quantum_labs` turns them off.

**Meteors** fall near players on some nights. Each one leaves a scorched crater with a core of Meteorite studded with exotic ore. Crush Meteorite for raw iron, with a chance of adamantium, faradaenium, qubit crystal or cortanium. The server config's `meteors` section can turn meteors off, keep terrain intact (they shatter into items instead), or set the nightly chance.

All materials use common `c:` tags. Each ore can be disabled in the common config's `worldgen` section.

### Exotic armor

Each material makes a full suit with the usual armor recipes; quantum pieces swap one alloy ingot for a Quantum Processor. Wearing all four pieces of one material grants its set bonus. Full sets also shed tier effects: glints, sparks, embers, then an orbit of light motes.

| Set | Bonus |
| --- | --- |
| Adamantium (Bulwark) | Resistance; heavy knockback resistance |
| Faradaenium (Dynamo) | Speed; attackers are struck by chain lightning (1.5 s cooldown) |
| Cortanium (Crucible) | Unbreakable; fire immunity and strength; attackers are set alight |
| Quantum (Phase) | All of the above, night vision, no fall damage; a phase shift voids one blow every 8 s |

## Starforge and paxels

Paxels (pickaxe, axe, shovel and hoe in one) are made only in the **Starforge**, a 3×3×3 multiblock built from adamantium frames, cortanium/quantum pylons, a nether-star crucible, a Block of Adamantium hearth and a Block of Qubit Crystal lens. It fuses a pickaxe, axe and shovel of one material around a catalyst block, keeping the best enchantments. Energy runs from 10M FE (diamond) to 200M FE (quantum, with a Singularity catalyst).

## Quantum machines

- **Industrial / Quantum Quarry:** mines a square in front of it, 9–33 or 9–65 blocks across. Energy per block is 25 FE × hardness, ×1.585 per Fortune level and ×4 with Silk Touch; the quantum model costs 40% less. Rubble can be voided. Output goes to a touching storage network or inventory.
- **Particle Collider:** turns common matter into exotic materials, Exotic Matter, Singularities and Nether Stars, drawing up to 250,000 FE/t.
- **Zero-Point Energy Generator:** 50,000 FE/t after a thirty-second spin-up; one Exotic Matter lasts twenty minutes.
- **Fusion Reactor:** a 5×5×3 tokamak with a floor of Fusion Shielding, a ring of Toroidal Field Magnets around a hollow 3×3 chamber (controller front-centre), and a roof of Shielding around a 3×3 Fusion Viewport. Sneak-use the controller to build it from your inventory. Pipe hydrogen into any casing. With 1,000 mB stored, a 50M FE ignition pulse lights the plasma. It then burns up to 8 mB/t and delivers up to 250,000 FE/t through the controller and casings. Without fuel the plasma cools and quenches.
- **Space Elevator (capstone):** place the anchor under open sky, power it and feed it Nanotube Tether Segments. It raises one every three seconds (50,000 FE each). With all 64 up, the orbital station beams down 120,000 FE/t day and night into every adjacent receiver. Sneak-use the finished anchor to ride the climber up the tether; slow fall brings you home.
- **Orbital Collector:** needs open sky and power. At night it tracks the heavens and pulls matter down a collection beam: each one-minute pass costs 3M FE and yields Cosmic Dust (nine craft into Exotic Matter), often qubit crystal or raw faradaenium, sometimes Meteorite, and very rarely a Nether Star.
- **Quantum Teleporter:** use an Entangled Qubit Pair on one pad, then on another, to link them (the pair is used up). Stand still on a linked pad for 1.5 s to jump to its partner, in any dimension. A jump costs 5,000 FE plus 20 FE per block, or 500,000 FE between dimensions. Arrivals must step off before the pad will send them back.

## Defence turrets

| Turret | Range | Fire | Cost |
| --- | --- | --- | --- |
| Plasma Turret | 20 m | A bolt every 0.3 s; no splash | 1,500 FE per bolt |
| Railgun Turret | 40 m | Charges 1.5 s, then pierces every valid target on the line | 40,000 FE per slug |

Turrets engage hostile mobs. Animals and survival players are opt-in from the console. A turret never targets whoever placed it, villagers, tamed pets or armour stands, and it fires only at what it can see. The console shows a live radar of every creature in range, plus the kill count and the charge. Redstone and SCADA can switch turrets off.

## Hover-Bike

A one-seat repulsor bike that rides a cushion about a block above land or water. W/S throttle, A/D steer (it banks into turns), Sprint boosts, Jump lifts it over obstacles. 1M FE battery: park it over a Charging Pad to recharge; sneak-use a wrench to pack it up with its charge and hull intact; repair the hull with steel ingots.

## Mech Suit

A three-block walker you climb into. W/S walks, A/D turns, Sprint runs and Jump fires the jump-jets. It steps up 1.5 blocks, and a landing from more than three blocks slams a shockwave into nearby creatures (never the pilot). It has a 2M FE battery that Charging Pads reach; repair it with steel ingots and pack it up with a wrench.

## Shield Generator

Projects a dome of force 4 to 16 m in radius (set it in the console). Hostile mobs inside are shoved back out, and projectiles fired by hostiles are burned away as they cross the surface. Upkeep is 3 FE/t per square metre of radius, plus 2,000 FE per repulse.

## Arsenal

Railgun (charge-and-release, piercing hitscan with zoom and recoil), Plasma Rifle (automatic, heat and venting), Singularity Launcher (black hole; terrain is safe unless `superweapons_break_blocks` is enabled in the server config), Quantum Blade (phase dash), Quantum Disassembler (four modes, outlined area, magnetic drops).

## Utility

- **Chunk Loader** (obsidian, gold, an eye of ender): keeps the 3×3 chunks around it loaded. Right-click with Range Upgrade cards (up to 3) to grow it to 5×5, 7×7 and 9×9; sneak-use with an empty hand to take a card back. Sneak near it to see the loaded area as a hologram. Tickets are re-checked on world load, so a loader removed while the world was closed releases its chunks.
- **Wireless Transmitter / Receiver:** a transmitter sends its redstone signal on one of 16 dye channels (right-click with a dye to tune it); every receiver on that channel, in any dimension, emits it. The lamp shows the channel colour.
- **Item Magnet:** use to toggle. While on, it pulls items and experience within 8 blocks (respecting pickup delays); sneaking pauses it.

## Time Dilation Field

Speeds up every machine and growing thing in a 5×5×5 cube around it (9³ and 13³ with Range Upgrades) by ×2, ×4, ×8 or ×16: extra block-entity ticks and extra random ticks. The faster it runs, the steeper the price: (N² − 1) × 40 + (N − 1)² × 8 FE/t for each machine inside. The clock console shows the dilated hand racing past a 1× reference hand.

## Tesseract

A hypercube that joins every tesseract on the same frequency (0–9999), in any dimension. Items, fluids and energy put into one come out of any other that is set to receive, with no loss. A frequency is private to its owner unless the console's lock is opened. Tesseracts never feed each other in a loop.

## Wireless charging

- **Wireless Charger** (late game): charges the FE armour, tools and items of players within 16 blocks (+8 per Range Upgrade, up to 3), at up to 8,000 FE/t. Arcs jump to each player it charges.
- **Dimensional Wireless Charger** (very late game): right-click to bind yourself. It charges bound players anywhere, in any dimension, at up to 40,000 FE/t, but a quarter is lost in transit (it spends 4 FE for every 3 delivered). A pillar of light climbs from it while it works.

## Farm Gantry

An overhead crane over the 9×9 field in front of it, level with the block it stands on. The bridge rolls along the rails and the carriage runs across it square by square; the tool head drops to till dirt and grass and sow it in one go, plant into bare farmland, or cut ripe crops (and melons and pumpkins off their stems). It keeps a seed from each harvest to replant and sends the rest to its harvest slots for pipes. Pipe water in and it irrigates every square it passes. 60 FE per job and 10 FE/t while moving. The console maps the field and shows the head working through it.

## Mob farming

- **Soul Snare:** use it on a mob to take an imprint of its kind; the mob is unharmed. Bosses can't be snared.
- **Spawner Cage:** put a filled snare in it and the mob appears inside the bars, turning in soul fire that rises until a copy steps out two blocks in front. Each copy costs 2,000 FE plus 400 per point of the mob's health; the cage waits while eight of that mob crowd the space. Speed and Efficiency cards apply.
- **Mob Grinder:** its saw cuts down mobs in the 5×3×5 space in front of it as a player would, so player-only drops and experience fall. It gathers the drops into its grid and the experience into a store you draw from its console. Named mobs, pets and players are spared. Point it back at a cage's spawn point for a complete farm.

## Pocket dimensions

A **Dimensional Pocket** is bigger on the inside. Use it to step into a private room in its own dimension, 7 blocks across; use any wall panel to step back out beside the door. Break the pocket and the room goes with it, contents and all (the room travels on the item). A **Spatial Expander** used on the pocket grows the room to 13 and then 21 blocks across without moving anything inside. The room stays loaded while its door is, so machines inside keep running. A **Pocket Port** placed inside against a machine or chest passes items, fluids and energy between it and the pocket outside.

## Vein mining

Combine any digging tool with a **Vein Mining Matrix** in an anvil (30 levels; the matrix takes a Nether Star, Exotic Matter, qubit crystals and diamond blocks). Hold the Vein Mine key (`` ` `` by default) and the vein under the crosshair is outlined, with a wave running through it in the order it will come away, and a count under the crosshair. Break one block and up to 64 connected blocks within 16 follow, a few per tick: the same block, or the same ore (`c:ores/*`) in other stone. Each costs durability or charge as if mined by hand, plus hunger, and it stops a block short of breaking the tool. Drops and experience gather where you struck.

## Fission

### Fuel cycle

| Step | Machine | In | Out |
| --- | --- | --- | --- |
| Mine | — | Uranium Ore (y −56 to 24) | Raw Uranium |
| Smelt | Furnace / blast furnace | Raw Uranium | Uranium Ingot |
| Enrich | Isotope Centrifuge (600 FE/t, 20 s) | 9 Uranium Ingots | 1 Enriched Uranium + 8 Depleted |
| Fabricate | Crafting | 3 Enriched Uranium + 6 steel | Uranium Fuel Rod |
| Burn | Reactor | Fuel Rod | Spent Fuel Rod (after ~40 min at full power) |
| Reprocess | Isotope Centrifuge | Spent Fuel Rod | 3 Plutonium, 3 Depleted Uranium, 2 Nuclear Waste |
| MOX | Crafting | 2 Plutonium + Depleted Uranium + steel | MOX Fuel Rod (+4% reactivity, 20% longer life) |

### The reactor

A box from 5×5×5 to 18×18×18 framed in dark Reactor Casing along every edge and corner; each wall between the edges can be Casing or Reactor Glass, so you can glaze every face but the frame stays solid. Reactor Ports and Logic Adapters go in the walls (not the edges), and the Fission Reactor Controller sits in one wall. Once it forms, the shell joins into one seamless hull. Inside: Fuel Assembly columns running floor to roof, each with a Control Rod Drive above it in the roof; everything else is water (moderator and coolant) or Graphite Moderator. Each assembly holds one fuel rod: the assembly is an open bundle of eight zircaloy rods round an empty guide tube, held by two spacer grids, and the absorber rod drawn by the controller slides down that tube.

### What it looks like running

- **Cherenkov light.** Fuel in water glows blue: a rippling sheath of light round each column (none round a column boxed in by other fuel), white-blue at its heart, with sparks flickering through the pool. Dry, graphite-moderated fuel makes no blue light; above 650 K it glows dull red through orange instead.
- **Through the glass.** Behind formed Reactor Glass the reactor draws the pool itself: the water, a blue wash that brightens with power (strongest beside the fuel), and caustics drifting across it. The light columns show through it with real depth.
- **Real light.** Fuel Assemblies give off block light that rises with power (3, 8, 12, then 15), so a running reactor lights its pool and the room round it.
- **The blue flash.** A sudden rise in power, or running past rated, fills the pool with a white-blue flash.
- **Boiling.** A water-cooled core boils streams of bubbles off the rods as it runs hot; a loop-cooled pool only fizzes.
- **On the console** the core map haloes each column blue by its flux.

Ports show their mode by colour: blue **Coolant** (pipe water in, steam out to turbines) and green **Fuel** (rods in, spent rods out). Use a port to switch.

### Coolants

| Coolant | How it cools | Effect on the core | Made from |
| --- | --- | --- | --- |
| Water | Boils into steam in the core (1 mB → 10 mB steam) | Steam voids push reactivity down: self-limiting | — |
| Heavy Water | Closed loop: leaves as Hot Heavy Water | +2% reactivity; fuel lasts a third longer; losing it adds a little reactivity | Isotope Centrifuge: water bucket |
| Liquid Sodium | Closed loop: leaves as Hot Sodium, 300 FE per mB | Takes heat 3× as well; losing it **adds** reactivity | Soda ash (smelted dried kelp blocks) + coal + bucket |
| Molten Salt | Closed loop: leaves as Hot Molten Salt, 400 FE per mB | Takes heat 2× as well; fuel can run 400 K hotter before damage | Soda ash + blaze powder + amethyst + bucket |

Loop coolants go through a **Heat Exchanger**: hot coolant and water in, steam and cold coolant out (up to 100,000 FE/t of heat each). The coolant tank holds one kind at a time; to change it, shut down, let the core cool below 450 K, and press Purge on the console (or from SCADA).

### Physics

Point kinetics with one delayed-neutron group, so power rises on a *period* you can watch. Reactivity is set by the fuel, its moderation, the control rods (12% Δk all the way in), Doppler feedback from fuel temperature, steam voids and xenon-135. Iodine-135 decays into xenon after a high-power run, holding the core down for several minutes (the iodine pit). Decay heat keeps warming the core after a SCRAM. Heat leaves through the coolant at 20 FE per mB of steam. Run the core dry or pull the rods too fast and the fuel overheats, the core is damaged and finally melts down: a steam explosion (terrain damage can be turned off with `reactor_explosions` in the server config), corium where the fuel stood, and radiation over a wide area for twenty minutes.

### Controls and SCADA

The console shows a live core map (each column coloured by temperature, with its rod insertion), instruments (power, thermal output, fuel temperature, reactivity in pcm, period, xenon, steam, coolant, fuel), a guarded SCRAM, Reset, Acknowledge, **Auto** (holds a target power by steering reactivity: it climbs quickly through the source range, then holds the period at five seconds or longer above 1% power, short of the fast-period alarm at four), rod drives for all rods or a selected column, an annunciator panel (alarms flash until acknowledged) with trip setpoints for temperature and power, and a one-minute trend of power, temperature and reactivity.

A **Reactor Logic Adapter** outputs any one alarm as redstone (use it to choose) and SCRAMs the reactor when it receives a signal.

On a SCADA terminal, click any device to open its detail view: live gauges with ranges and alarm colours, and its controls. The reactor publishes power, temperature, reactivity, period, xenon, steam, coolant, rod position and core damage, with SCRAM (guarded), Reset, Acknowledge, Auto, target power, rod position and both trip setpoints. Steam boilers and turbines publish their temperatures, flows, tanks, shaft speed, frequency and breaker. A filter chip shows only devices in alarm.

### Radiation

Spent fuel and nuclear waste irradiate whoever carries them, and meltdowns and corium leave contaminated ground. A running reactor leaks gamma through its glass (casing and the pool stop nearly all of it): up to about 0.6 against an all-glass reactor at full power, gone within a few blocks. The dose shows as poison, then wither, then nausea. In a strong field your view speckles with flecks and short tracks, as gamma rays strike a camera's sensor. A full Hazmat Suit (rubber and lead) keeps it all out.

### Luminous

With Luminous installed, a Cherenkov Well standing in water within three blocks of a running reactor gathers blue light, up to 24 a cycle at full power.

### High-Pressure Turbine

The steam turbine at power-station scale: 5 wide, 3 tall and 9 long, Turbine Casing all round with fifteen Steam Rotors and six Generator Magnets inside, the controller in the middle of the front. 50,000 FE/t on 2,500 mB/t of steam, synchronised to the grid like the small turbine. Two of them take a 25-assembly reactor at full power.

## Black Hole Power Plant

A micro black hole held in a 5×5×5 containment sphere: Containment Casing top and bottom round a 3×3 of Field Coils, Event Horizon Glass walls (casing up the corners), an Accretion Injector in the middle of each wall's lower row, Hawking Collectors in the middle of the side and back walls' centre row (the front's is a window), and the controller in the front of the bottom layer.

Ignite it with a Singularity and 500M FE. It then radiates Hawking power, which grows as the hole shrinks (P ∝ 1/M²), and evaporates, so it must be fed: any item is one unit of mass, Exotic Matter 100, a Singularity 1,000. Infalling matter adds accretion light (30% of its rest energy). Holding it costs a containment field proportional to its mass.

| Mass | Hawking | Feed to hold | Net |
| --- | --- | --- | --- |
| 200 | 25M FE/t | 36 items/s | 36M FE/t |
| 500 | 4M FE/t | 5.7 items/s | 5.7M FE/t |
| 1,000 | 1M FE/t | 1.4 items/s | 1.3M FE/t |
| 2,000 | 250k FE/t | 0.4 items/s | 157k FE/t |

Auto-feed holds a target mass. Starve a hole and it bursts; let the stored energy fall below the field's needs and it escapes as a wandering singularity. The console draws the hole, its instruments and the power-against-mass curve with the hole's position on it.

## AI mining drones

The Drone Hub's AI surveys a 49-block-wide column under and around it, clusters ore into veins (diagonals count), and scores each: ore value × √size, divided by distance, and quartered if the vein touches lava (held back while anything safer remains). It sends up to four Mining Drones to the best unclaimed veins; each flies out, phases through stone in a bore field, laser-cuts the ore and flies the haul home. Every decision appears in the console's AI log, beside a live map of veins and drones. Each launch charges a 30,000 FE battery; Speed cards make drones faster; it re-surveys every two minutes.

## Formed multiblocks join up

When a big structure forms (Black Hole Power Plant, fission reactor, fusion reactor, Starforge, refinery and pumpjack bases), its casing joins into one seamless shell: trim runs only round the outside of the machine, and the panels between blocks merge, so lattices, seams and hazard bands run unbroken across it. Glass and coil banks keep a frame where they meet the casing. Ports, injectors, collectors and the controller keep their own framed faces. Break any part and the shell falls back into separate blocks. The art comes from `tools/connected_art.py`, and the logic lives in `multiblock/Connected.java`.

## Wireless power networks

Flux-Networks-style power without wires, any distance and across dimensions. Each network needs one **Power Net Controller**. **Power Plugs** take power from whatever touches them (pushing in or drawn out); **Power Points** give it to whatever touches them; **Power Cells** store it (4M, 64M and 1G FE). Open any device to choose or create a network: a name, a colour (the devices glow in it) and access (Public, Private, or Password). Every device has a name, a priority (higher served first; Cells give up charge from the lowest and take it from the highest, equal priorities sharing), a transfer limit (800,000 FE/t by default, or none), Surge (always first) and can keep its own chunk loaded. Tabs: Net (choose, create, rename, recolour, delete), Device, Links (every device with its flow), Stats (a minute of in, out and stored), Users (members and admins, and the controller's wireless charging of members' hotbar, armour, offhand and pack). A **Net Configurator** copies settings (sneak-use) and pastes them (use). Plugs and Points are made from **Fulgurite**: sand fused by lightning (a bolt on sand, or on a lightning rod standing in it, turns some to Fused Sand).

## Redstone: red alloy, gates and lamps

**Red Alloy Wire** (copper and redstone) carries full strength along its whole run with no fade, climbs blocks like dust, and powers what it points at and sits on. **Insulated Wire** in 16 colours joins only its colour and red alloy and doesn't power the block it lies on. **Bundled Cable** carries all 16 colours at once. Sixteen gates on stone plates (AND, OR, NOT, NAND, NOR, XOR, XNOR, Buffer, Multiplexer, Pulse Former, Repeater, Randomizer, RS Latch, Toggle Latch, Timer, Counter): inputs left, back and right, output front, a lamp for each. A **Screwdriver** turns a gate (sneak: which inputs it reads); use a Repeater or Timer bare-handed to step its delay, a Counter to set its target. **Illumar Lamps** in 16 colours glow with a halo; inverted ones are lit until powered.

## Bedrock, the Pocket Void, elevators and the chunk card

TNT set off against bedrock can chip off a **Bedrock Fragment** (better odds the closer the blast; two at most). Four fragments and a glowstone dust make two **Essence of Bedrock**. The **Pocket Void** (Dank-style storage, 2,048 of each of nine kinds; the **Deep Pocket Void**, 65,536) is a little cube: a dark frame round walls that open onto the End's starfield, with a black hole spinning inside (accretion disk, photon ring, matter spiralling in; the Deep one burns gold with a second disk). It opens on use; cells lock to a kind, one is chosen to build with when you use the void on a block, and its pickup mode pulls in matching items, anything, or matching with the excess deleted. A Void Core upgrades it with its contents kept. **Elevators** (wool and a fragment): jump to rise to the next of the same colour, sneak to drop; dye them for separate shafts. A **Chunk Loader Card** in any machine's upgrade slots keeps that machine's chunk loaded.

## Radiation meters

A **Geiger Counter** clicks faster and reads the dose rate (mSv/h) where you stand; a **Dosimeter** shows the dose you have taken in all. Fission reactors now reach 18×18×18.
