# Reservoirs, logistics finishes and foundry armor — PowerFactor 0.3.0

Adjacent fluid tanks containing the same fluid and components join into one reservoir.
Every physical cell contributes 32,000 mB. Horizontal layers level their fill, lower liquid
layers fill first, and gases spread through the connected volume. Empty cells retain a
fluid affinity so incompatible neighboring reservoirs keep a divider. Draining a reservoir
clears that affinity and allows empty cells to join a different fluid.

Capabilities, tank interactions and comparators see the aggregate contents. Only loaded,
live cells participate. Each block still saves its own local share, so there is no central
controller that owns another block's fluid. Breaking one cell creates one unstackable tank
item with exactly that cell's contents in `powerfactor:fluid`, including fluid components.
Survival placement restores that share and rejoins neighbors. Cloning a filled tank also
preserves its fluid component. Tank items expose a fluid capability, and their tooltips
describe the retained amount and capacity.

The glass shell uses multipart geometry: internal panes, caps and frame seams disappear
where cells join. The connected fluid renderer also hides interior walls and covered tops.
Exterior rails, corner pillars, utility ports, fill markings and thin glass reflections
give the combined reservoir a clear outline. Pipes have reinforced ribs, painted collars,
machined endpoint bolts and distinct insertion/extraction fittings. Routing rules, filter
components and persistent IDs remain compatible.

[Pipe and reservoir preview](previews/logistics-finish-gallery.png)

The foundry suit has a complete articulated humanoid model: helmet crown and brow, visor,
breather, ear modules, chest core, rear power pack, shoulder plates, forearm housings, hip
belt, thigh/knee plating, actuator rails, reinforced toes and tread soles. Original 256px
atlas materials distinguish ivory enamel, gunmetal, rubber, copper, chrome and visor glass.
The four inventory icons receive matching detail.

The normal equipment renderer selects separate adult/baby models per armor slot. Slot
masks stay fixed as models animate; rendering a second piece cannot unhide geometry from
another queued piece. Models are invalidated when the renderer reloads. Player layers retain
emissive visor/core accents and animated leg pistons without duplicating the static plates.

[Armor pose gallery](previews/armor-finish-gallery.png)

Suit and movement overlays are compact and independently hideable through **B**. **F8**
temporarily hides/shows both while preserving individual choices; **F1** also hides them.
Choices are saved in the local `config/powerfactor-hud.json`. The suit panel is 84 × 26 GUI
units; the grapple is 104 × 22, or 104 × 35 with a wing. Hidden suit instrumentation frees
the space above the movement panel. Equipment settings now fit a 248 × 213 panel.

Regression tests exercise reservoir filling/balancing, transaction aborts, breaking and
splitting, item codec serialization, actual survival placement and rejoining, removed cached
capabilities, distinct fluid components and block-entity reload. Native model checks bake
armor in six adult/baby poses and check all 24 animated slot masks, finite vertices and UVs.
The linked galleries are software asset previews from shipped geometry/textures and native
baked poses. Current in-game lighting, layering, resource reload and HUD interactions still
need a graphical client check.
