# Machine and progression polish

This update focuses on existing machinery. PowerFactor still targets Minecraft 26.2
and NeoForge 26.2.0.90. The Forge Hammer and Wire Cutters are the requested early-game tools.

## Models and animations

Processing machines now have exposed tooling, frames, service panels and copper details.
The wire drawer has a pulling wheel, the rolling mill has opposing rollers, the washer has
a ribbed drum and a water reservoir, and the arc furnace has moving graphite electrodes.
Rotating parts accelerate and coast with machine operation. The washer's visible water
level follows its stored water, including when filled through a fluid capability.

Generators and electrical devices have distinct physical models: a diesel engine with
pistons and a flywheel, coal firebox and heat fins, battery cells and bus bars, induction
coils, pump flanges, transparent electrolyzer chambers, fuel-cell plates, instrument meters,
heat exchangers, transformer windings and tilted photovoltaic panels. Existing wind,
crusher, capacitor, workshop, steam and refinery mechanisms retain their animations.
The small redstone and Stirling engines also have cooling ribs and bolted moving plates.

[Machine models](previews/machine-models.png) · [Generator models](previews/machine-generators.png) ·
[Running animation](previews/machine-animation.mp4)

Connected energy faces show a physical socket. Low panels, charging pads and ceiling lamps
have leads between their bodies and the cable connection. A connected wind-tower base
gains a service cabinet and exposes the turbine's energy to native and external cables.
Breaking the tower prevents extraction through a previously cached base capability.

## Controls

The three-phase electrical panel and the other side panels start closed whenever a screen
opens. Configuration always shows all six relative faces in a fixed diagram; a draggable
live model beside it shows the machine and its moving parts. Colored outlines preserve
visibility of the model. Front, back, left and right follow the machine's facing.

Left-click a face to advance its mode; right-click to reverse. Symbols show closed, input,
output and both, with hover descriptions. Redstone has three direct selectors: barred torch
for run unless powered, lit torch for run only when powered, and infinity for always run.
Chest-to-machine and machine-to-chest controls toggle automatic input and output.
The controls fit at the minimum 320-unit GUI width.

[Face and redstone controls](previews/machine-faces.png) · [Compact controls](previews/machine-compact.png) ·
[Physical cable connections](previews/machine-power-links.png)

## Early workshop and recipes

Processing now tracks the active recipe across ticks and saves. Changing to another recipe
resets its progress. Blocked outputs, missing water, redstone shutdown and loss of power
pause the current recipe without losing progress; freeing the output or restoring supplies
resumes it. The GUI and SCADA show the stop reason, and status-strip tooltips explain the
action needed. Output blocking checks both stack capacity and item compatibility.

Machine animations follow actual operating speed, including speed upgrades and changes
in the electrical supply. Blocked work stops the tooling and lets rotating parts coast down.
Crusher jaws and ore-washer rotation use continuous phases without a jump at each wrap.

[Blocked output](previews/processing-blocked.png) · [Missing water](previews/processing-water.png)

Whole output batches reserve space together, including multiple byproducts sharing a slot.
Zero-chance products reserve no space. Oversized stacks and incompatible products pause
the recipe instead of truncating or changing its output. A missing required tagged product
shows **Recipe unavailable** and preserves the inputs and partial work.

| Crafting | Output |
| --- | --- |
| Two ingots + Forge Hammer | One plate |
| Copper, silver or aluminum plate + Wire Cutters | Two matching fine wires |
| One copper fine wire | One bare copper cable block |
| Slimeball + clay ball + coal or charcoal | Two rubber |

Both hand tools return after crafting and wear once per operation, lasting 128 uses.
Wire Cutters also dismantle placed power cables through the player's normal block-breaking
path. Automated plate production uses one ingot per plate; the wire drawer makes three
fine wires per ingot. Four coal dust in the alloy furnace supplies starting graphite.
The first arc furnace uses iron plates and a control circuit, avoiding a dependency on
the steel and silicon it produces.

Wind turbines now require three blades, two motors, a generator magnet, two steel blocks
and an advanced circuit. Tower recipes produce two sections. Natural latex remains
renewable; furnace processing gives one rubber per latex, while the alloy furnace gives
three rubber from two latex. Rubber trees have fuller branching canopies, new bark and
leaf textures, and visible latex cuts.

[Rubber tree](previews/machine-rubber.png) · [Connected wind footing](previews/machine-wind-base.png)

## Energy and mod compatibility

Generators rotate output priority and share stored energy among distinct adjacent
consumers. Repeated references to the same capability are deduplicated. The coal
generator's front accepts an output connection consistently with its other energy faces.
Ore-washer fluid handlers follow the current input/output face configuration, including
after a machine rotates or an external pipe retains a cached handler.

Materials, rubber, wires and hand tools use common `c:` tags. Machine integration uses
NeoForge item, fluid and transactional energy capabilities. Wrench dismantling honors
block-break protection events before returning drops. Checks exercise generic external
handlers, transaction rollback, cached capabilities and a cancelling protection listener.
Client checks include JEI and Simple Voice Chat. Create/Mekanism runtime integration and
the existing 26.1.2 ATM11 port remain separate, unverified work.

Hand coating accepts any item in `c:rubbers`, including another mod's material. Coating
uses one item and does not consume another when the cable is already coated.

## Flat testing launcher

`launch.bat` builds the current checkout and opens `PowerFactorFlatTest`: a separate flat
Creative world with cheats enabled. Use `/gamemode survival` and `/gamemode creative`
to switch while testing. An existing saved player's chosen mode is preserved.

First launch prepares the world with a separate server that saves and stops normally.
The Java helper enables commands only after verifying the world is flat and backs up
`level.dat` before changing it. Existing non-test worlds are left alone. Java 25 is required;
Gradle also uses a Java 21 toolchain for its asset task.

## Validation

```sh
python3 tools/gen_data.py
python3 tools/gen_textures.py
python3 tools/check_resources.py
./gradlew test build runGameTestServer --offline
```

The completed checks passed 54 unit tests and 87 required in-world GameTests. Resource
validation passed for 1,368 JSON resources and 24 animation sheets. Native client checks
cover machine models, processing and generator animations, empty/full washer water,
closed panels, face and redstone selection, compact layout, pause/resume messages and Creative/Survival commands.
The world's preparation and Java helper were exercised on Linux; Windows batch execution
still needs a Windows machine.

`runGameTestServer` alone enables the fixture pack in `gametest-fixtures`: custom recipe
edge cases and a non-PowerFactor item added to the rubber tag. Its five JSON files are
included in resource validation. Ordinary client and server runs do not register this pack.
