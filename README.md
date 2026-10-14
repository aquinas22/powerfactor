# PowerFactor

PowerFactor 0.3.0 adds generators, electric grids, machines, steam systems, equipment and vehicles to Minecraft Java **26.2**, using **NeoForge 26.2.0.90** and **Java 25**.

## Play

The parent workspace's Windows ZIP installs a ready-to-play profile: extract it and use `Play-PowerFactor.bat`, or `START.bat` for all three mods. For manual installation, place `build/libs/powerfactor-0.3.0.jar` in a matching NeoForge instance, removing an older PowerFactor JAR first. Install the same mod on the server and every connecting client.

JEI is optional and shows the machine recipes. Simple Voice Chat is optional for ordinary machinery and required for walkie-talkie audio. Both are included in the Windows PowerFactor profile. Remote voice chat also needs the voice chat mod and its UDP port on the server.

## Get started

Find the **PowerFactor** creative tab, or use JEI to plan survival recipes. Craft the Forge Hammer and Wire Cutters to bootstrap plates and wire. A turbine, compatible copper wires and a crusher provide an early production line. Machine screens show electricity, fluid supplies, progress and the reason processing is paused. Use the face controls to configure input and output.

PowerFactor's FE represents usable power; EE models reactive power and CE models apparent power. Match wire voltage and current ratings. Battery banks store energy, charge from surplus and support cold grids.

**Pipe sorting:** sneak-use an item, fluid or steam pipe with an empty hand to open its routing panel. Choose a world-direction face, copy up to four filter references from inventory, and select allow/block mode. Filled buckets or tanks supply fluid references. Higher-priority destinations fill first; full destinations overflow to lower priorities. Exact component matching can distinguish named items or component-bearing fluids. [Routing guide](docs/pipe-routing.md).

| Control | Action |
| --- | --- |
| G | Fire or release the grapple |
| U | Deploy or fold the wingsuit |
| J | Toggle jetpack hover |
| B | Open power armor settings |
| F8 | Hide/show suit and movement HUDs |
| W/S and A/D in wingsuit | Pitch down/up and bank |
| Sneak with grapple | Swing |

Remap controls in Minecraft's Options. Power armor, grapples, jetpacks and the rover use FE. Charge equipment on a charging pad; the armor upgrade table installs modules and propulsion equipment.

- [Storage network, quantum machines, exotic materials and the arsenal](docs/quantum-and-logistics.md)
- [Machine consoles and finishes](docs/machine-console-finish.md)
- [Joined reservoirs, pipe finishes and foundry suit](docs/reservoirs-and-foundry-suit.md)
- [Machines and grids](docs/machine-polish.md)
- [Steam and cooling](docs/steam-and-cooling.md)
- [Power armor](docs/power-armor.md)
- [Grapple and wingsuit](docs/grapple-and-wingsuit.md)
- [Electric rover](docs/electric-rover.md)
- [Workshop](docs/polish-and-workshop.md)
- [Pipe filtering and routing](docs/pipe-routing.md)

## Develop

```sh
./gradlew build
./gradlew runGameTestServer
python3 tools/check_resources.py
```

The developer `launch.bat` builds and opens a dedicated creative test world; the parent Windows player distribution runs compiled JARs and requires no JDK or Gradle. Existing worlds are preserved. Back them up before replacing mods. The feature backlog and migration notes are in [TODO.md](TODO.md).

The parent [agent workflow](../AGENTS.md) and [workbench guide](../tools/workbench/README.md)
cover cached builds, automated validation, software previews, dedicated client control,
screenshots and Windows packaging through a JSON CLI or MCP stdio server.
