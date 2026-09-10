# Cheesecake

Baritone for Minecraft 1.21.11 on Fabric.

Cheesecake is a fork of [Baritone](https://github.com/cabaletta/baritone), the Minecraft pathfinding and automation mod, packaged as a single client-side Fabric mod for Minecraft 1.21.11. It walks, mines, builds, farms, explores and flies on your behalf, driven by chat commands.

The project is experimental. The original port was produced with substantial help from AI tooling and has not been reviewed line by line by a person. It builds and loads cleanly in continuous integration, and its behaviour in the game has been checked selectively rather than exhaustively. Please read [Status](#status) before depending on it for anything that matters.

## Requirements

- Minecraft 1.21.11
- Fabric Loader (built and tested with 0.18.4)
- Java 21

Fabric API is not needed. The mod runs on the client only and does nothing when installed on a server.

## Installation

Download the jar from the most recent entry on the [Releases](https://github.com/0Mattias/cheesecake/releases) page and copy it into the `mods` folder of a Fabric 1.21.11 profile.

Every push to `main` also builds the mod and launches a headless client to confirm that it loads. To try a change that has not been released yet, open the [Actions](https://github.com/0Mattias/cheesecake/actions) page, select the most recent successful run on `main`, and download the `cheesecake-jar` artifact.

Alternatively, build the jar yourself as described under [Building](#building).

## Usage

Commands are typed into chat with a `#` prefix. Some to begin with:

| Command | Effect |
| --- | --- |
| `#goto 1000 500` | Walk to x = 1000, z = 500 |
| `#goto diamond_ore` | Walk to the nearest diamond ore |
| `#mine 8 iron_ore` | Mine iron ore until eight raw iron are in the inventory |
| `#tunnel 3 2 100` | Dig a tunnel three blocks high, two wide and a hundred long |
| `#farm` | Harvest and replant the crops around you |
| `#build house.schematic` | Build a schematic starting at your feet |
| `#elytra` | Fly to the current goal on an elytra, using fireworks |
| `#stop` | Cancel whatever is running |
| `#help` | List every command |

Settings are changed the same way. `#allowBreak false` stops the pathfinder from breaking blocks, `#modified` lists every setting that differs from its default, and `#reset` restores them. Settings persist in `.minecraft/cheesecake/settings.txt`; cached chunks and waypoints are kept per world in a folder of the same name.

Three documents cover the details:

- [USAGE.md](USAGE.md) is the full user guide, inherited from Baritone.
- [FEATURES.md](FEATURES.md) describes what the pathfinder can and cannot do.
- [AI_AGENT_README.md](AI_AGENT_README.md) is a compact command reference written for software that drives the mod through chat.

`#build` reads schematics from `.minecraft/schematics` and accepts the MCEdit (`.schematic`), Sponge (`.schem`) and Litematica (`.litematic`) formats.

## Status

### Where the code comes from

Cheesecake began as a port of Baritone's 1.19.4 branch to Minecraft 1.21.11. Upstream later published a 1.21.11 branch of its own. Rather than start over, the changes Baritone made between those two versions were backported here, so the two projects now offer the same features: climbing of vines including the Nether varieties, elytra flight in the Overworld and the End as well as the Nether, routing above the build limit on long flights, a landing search that no longer stalls the game, and farming restricted to a selection.

### Differences from Baritone

- Cheesecake is one Fabric project. There are no Forge, NeoForge or launchwrapper builds, no ProGuard pass, and no separate API jar for other mods to compile against.
- Block drops are resolved by reading the loot tables shipped inside the game and mod jars, rather than by rolling them through a simulated server. As a result `#mine` knows what modded blocks drop as well. Loot tables defined only in a server-side data pack cannot be seen from the client; blocks that use one are assumed to drop themselves.
- The `renderGoalXZBeacon` setting has no effect. The goal box is rendered instead of the beacon beam.
- The `shortBaritonePrefix` setting is called `shortCheesecakePrefix`.

### Verification

Every push is compiled against Minecraft 1.21.11, the unit tests are run, the jar is checked for correct remapping and packaging, and a client is launched under a virtual display to confirm that every mixin applies. What continuous integration cannot check is behaviour inside a world. Elytra flight and the item counts used by `#mine` are the most recently changed parts and the least exercised in play; reports on either are welcome.

## Building

A JDK 21 is required. Everything else is fetched by Gradle.

```
./gradlew build
```

The mod jar is written to `build/libs`. `./gradlew test` runs the unit tests on their own, and `./gradlew runClient` starts a development client with the mod loaded.

The code uses Yarn mappings, whereas Baritone uses Mojang's official names with Parchment. Patches taken from upstream need their Minecraft symbols translated, which is the main cost of keeping the fork current. Upstream's [SETUP.md](SETUP.md) is kept for reference; its instructions about loaders, artifacts and the `dist` directory do not apply here.

## Reporting problems

Please open an issue on GitHub. Include the Minecraft log, the command you ran and the output of `#modified`. If the problem also occurs with Baritone on 1.21.11, mention that, since the fix may belong upstream.

## Credits and license

Baritone was written by leijurv, Brady and their contributors, and this project would not exist without their work. If you find it useful, support the original at [cabaletta/baritone](https://github.com/cabaletta/baritone).

Cheesecake is distributed under the GNU Lesser General Public License, version 3, like Baritone. See [LICENSE](LICENSE).
