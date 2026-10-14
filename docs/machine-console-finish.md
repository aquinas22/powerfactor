# Machine consoles and finishes — PowerFactor 0.3.0

The shared machine theme uses blue steel, warm white labels, restrained teal highlights
and amber warnings. Input, output and tooling have blue, gold and violet markers.
Gauges, slots, configuration controls and side tabs share the same materials.

Processing consoles separate electrical readings, material flow, cycle progress and
inventory. The wider progress rail displays a numerical percentage and current speed.
It eases toward actual server readings, resets for a new cycle, and holds when work pauses.
Hover the process strip for the complete stop reason, recovery guidance and an approximate
remaining time at the current speed. Hover empty slots to see their role. The power display
provides exact FE/EE rates, voltage and frequency on hover. The pump shows tank fill instead
of a recipe percentage on its rail.

The processing screens are 176 × 214 GUI units, fitting the minimum 320 × 240 viewport.
Inventory slots move together in both server and client menus; their indexes stay stable.
Side tabs still start closed, shift to fit when opened and report their bounds to JEI.
Routing buttons use the same finish and retain vanilla keyboard focus, narration and
hover tooltips. A selected direction has a teal highlight. Charger and generator readouts
have more clearance and avoid overlapping the title or adjacent values.

The press, mixer, centrifuge, sawmill and laser engraver have new authored frames.
They retain their moving tooling, with ram collars, blade spindle hubs and bucket caps.
Painted housings, chrome rails, rubber feet, service handles, utility sockets and recessed
control panels give each machine a distinct silhouette. The older machine family also
receives matching metal finishes, appropriately scaled grain, quieter painted casings
and rear fan mounts. Off-state lamps no longer emit light. Collision, recipes, stored
contents, orientation and the existing animation timing remain compatible.

[Console art preview](previews/machine-console-finish.png) ·
[Machine art gallery](previews/machine-finish-gallery.png)

These are **software asset previews**, not in-game screenshots. The console preview
executes the shared `Skin.java` drawing through a Java2D adapter with Minecraft's bitmap
font and sample readings. The gallery renders the shipped block geometry/textures and
animated parts baked by Minecraft. Actual lighting, font fallback, mouse interactions,
JEI placement and the Windows launcher still need a graphical client check.

Regenerate the production assets with:

```sh
python3 tools/gen_data.py
python3 tools/gen_textures.py
python3 tools/check_resources.py
```

Both generators call `machine_finish.py` at the end, preserving the finishes on regeneration.
The preview tools require Pillow, Java 25 and the existing Minecraft development cache.
From the repository root, build and validate before producing the gallery:

```sh
python3 tools/build_release.py --cached
python3 tools/validate_release.py
python3 powerfactor/tools/render_machine_gallery.py
python3 powerfactor/tools/render_console_preview.py
python3 tools/build_release.py --package-only
```

Validation: 63 unit tests and 153 Minecraft GameTests with the three production
mods, JEI and Simple Voice Chat loaded together. Native client parsing covers 609 cuboid
models, 371 static item definitions, 21 equipment assets and 137 blockstates. All 14 machine
mechanisms bake with 85 cubes and valid atlas coordinates. Windows installer functional
checks run under PowerShell on Linux; native Windows execution is unverified.
