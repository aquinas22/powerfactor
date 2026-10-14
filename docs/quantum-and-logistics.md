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
