# Workshop and visual update — October 12, 2026

The subsequent [machine and progression polish](machine-polish.md) covers the current
models, face controls, recipes, rubber, compatibility checks and flat-world launcher.

PowerFactor targets Minecraft 26.2 / NeoForge 26.2.0.90. The built mod is
`build/libs/powerfactor-0.1.0.jar`.

## New machines

All four machines accept processing upgrades, side configuration, redstone control,
automatic input/output, item pipes, and JEI recipe lookup. Their exposed tooling animates
while working and coasts when power stops.

| Machine | Examples | Base power |
| --- | --- | --- |
| Mechanical Press | Plate die: one ingot → one plate. Gear die: four iron, brass, or steel ingots → one gear. | 32 FE/t |
| Mixer | Copper + zinc dust → brass; copper + tin → bronze; gold + silver → electrum. Smelt the blended dust into ingots. | 20 FE/t |
| Centrifuge | Four dirt → one clay ball + one sand. Four gravel → one flint + two sand. | 40 FE/t |
| Sawmill | One log → six matching planks + one stick, including rubber logs and Nether stems. | 16 FE/t |

Fit the appropriate press die in the purple tool slot. Each die lasts 512 completed jobs.
The die selects the recipe consistently for bulk input.

## Structures and construction

The pumpjack now needs nine **Pumpjack Foundation** blocks in a 3×3 layer directly below
its lower half. Its energy buffer and oil tank are accessible through any formed foundation
block. Removing a foundation part stops pumping. Existing pumpjacks retain their mechanism
and stored contents; add the foundation to make them operate again.

Sneak-use a pumpjack, refinery controller, or wind turbine with an empty main hand or a
structure part to assemble missing parts from inventory. Clear the hologram first.
Assembly checks all required materials and positions before placement, honours placement
protection, and consumes only the missing parts. Creative assembly uses no materials.
The wind turbine's minimum is six tower sections; it can still use a taller tower.

Factory construction now includes axis-oriented steel I-beams, grated steel catwalks,
riveted steel walls, hazard blocks, and warning blocks. Crafting recipes appear in JEI.

## Visual changes

The pumpjack has a detailed skid, motor, support frame and linked walking beam/pitman
mechanism, with smooth starting and stopping. The refinery column has flanges, ladder rungs,
return pipework and animated status textures. Generator screens have glass gauges and
readout backgrounds; shared machine screens have fitted titles, labelled tab tooltips,
and a compact layout at the 320-unit minimum GUI width. The pumpjack GUI includes a live
model preview. Fluid and energy gauges show quantities on hover.

[Pumpjack model](previews/pumpjack.png) · [Pumpjack GUI](previews/pumpjack-gui.png) · [Workshop models](previews/workshop.png) ·
[Processing GUI](previews/processing-gui.png) · [Compact electrical panel](previews/compact-gui.png)

## Validation

```sh
python3 tools/gen_textures.py
python3 tools/gen_data.py
python3 tools/check_resources.py
./gradlew test build runGameTestServer --offline
```

The final run passed 34 unit tests and 40 in-world GameTests. The resource checker validated
1,202 JSON resources and 22 animation sheets. Client checks covered model loading, the live
pumpjack preview, sneak-use assembly, a powered processing recipe, and the electrical panel
at minimum GUI width. Microphone multiplayer behavior and the complete sound library still
need player testing.

To reopen an existing development world:

```sh
./gradlew runClient -PclientDir=runs/polish-client -PquickPlayWorld="New World" --offline
```

Steam generation and cooling are now implemented in the [steam plant update](steam-and-cooling.md).
The remaining roadmap is tracked in [TODO.md](../TODO.md), including vehicles, quarry,
nuclear systems, power armour, and robots.
