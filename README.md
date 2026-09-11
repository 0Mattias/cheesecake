# Cheesecake

Baritone for Minecraft 26.2 on Fabric.

Cheesecake is a fork of [Baritone](https://github.com/cabaletta/baritone), the Minecraft pathfinding and automation mod, packaged as a single client-side Fabric mod for Minecraft 26.2. It walks, mines, builds, farms, explores and flies on your behalf, driven by chat commands.

The project is experimental. The original port was produced with substantial help from AI tooling and has not been reviewed line by line by a person. It builds and loads cleanly in continuous integration, and its behaviour in the game has been checked selectively rather than exhaustively. Please read [Status](#status) before depending on it for anything that matters.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.5 or newer
- Java 25

Fabric API is not needed. The mod runs on the client only and does nothing when installed on a server.

## Installation

Download the jar from the most recent entry on the [Releases](https://github.com/0Mattias/cheesecake/releases) page and copy it into the `mods` folder of a Fabric 26.2 profile. Version 0.4.0 is the last release for Minecraft 1.21.11.

Every push to `main` also builds the mod, launches a headless client to confirm that it loads, and walks a path in a generated world. To try a change that has not been released yet, open the [Actions](https://github.com/0Mattias/cheesecake/actions) page, select the most recent successful run on `main`, and download the `cheesecake-jar` artifact.

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
| `#status` | Print the bot's state as one line of JSON |
| `#stop` | Cancel whatever is running |
| `#help` | List every command |

Settings are changed the same way. `#allowBreak false` stops the pathfinder from breaking blocks, `#modified` lists every setting that differs from its default, and `#reset` restores them. Settings persist in `.minecraft/cheesecake/settings.txt`; cached chunks and waypoints are kept per world in a folder of the same name.

Three documents cover the details:

- [USAGE.md](USAGE.md) is the full user guide, inherited from Baritone.
- [FEATURES.md](FEATURES.md) describes what the pathfinder can and cannot do.
- [AI_AGENT_README.md](AI_AGENT_README.md) is for software that drives the mod, through chat or through the local control socket that `agentApiPort` opens.

`#build` reads schematics from `.minecraft/schematics` and accepts the MCEdit (`.schematic`), Sponge (`.schem`) and Litematica (`.litematic`) formats.

## Status

### Where the code comes from

Cheesecake began as a port of Baritone's 1.19.4 branch to Minecraft 1.21.11. Upstream later published a 1.21.11 branch of its own. Rather than start over, the changes Baritone made between those two versions were backported here, so the two projects now offer the same features: climbing of vines including the Nether varieties, elytra flight in the Overworld and the End as well as the Nether, routing above the build limit on long flights, a landing search that no longer stalls the game, and farming restricted to a selection. Minecraft has shipped with Mojang's names since 26.1 and Yarn ended at 1.21.11, so with the move to 26.2 the code was migrated to the same names Baritone uses, and the three commits of upstream's 26.2 branch were applied.

### Differences from Baritone

- Cheesecake is one Fabric project. There are no Forge, NeoForge or launchwrapper builds, no ProGuard pass, and no separate API jar for other mods to compile against.
- Block drops are resolved by reading the loot tables shipped inside the game and mod jars, rather than by rolling them through a simulated server. As a result `#mine` knows what modded blocks drop as well. Loot tables defined only in a server-side data pack cannot be seen from the client; blocks that use one are assumed to drop themselves.
- The `renderGoalXZBeacon` setting draws the beacon beam instead of the goal box, as its description says. Upstream draws the beam on top of the box for every X/Z goal and never reads the setting.
- Arriving at a `#goto` goal reports the `AT_GOAL` path event. Upstream cancels the path in the same tick the player steps into the goal, before the path can report that it finished, so its listeners see `CANCELED` instead. Likewise `CANCELED` is only fired when a path or a calculation was actually cancelled.
- The elytra landing search treats every kind of air as air. Upstream only recognises the plain `air` block, and carved caves in the Nether are `cave_air`, so upstream circles above them until the fireworks run out.
- The worker threads are daemon threads. Since 26.2 the client watches its own shutdown and files a crash report when a thread outlives the game; upstream's pool keeps the chunk packer parked for the life of the game, which trips that.
- Rays of zero length, and rays with a coordinate that is not a number, are answered without asking nether-pathfinder. Handed either, the library prints `raytrace whiffed` and calls `exit(696969)`; a process exits with the low eight bits of that, so the game disappears with status 137 -- the number a shell also reports for a process killed by SIGKILL, which makes it read as the machine running out of memory. It is not: there is no kill, and so no crash report, no JVM error log and nothing in the kernel log either. The solver produces a zero-length ray whenever it measures the way to a point the player already stands on.
- The elytra process gives up on finding a spot to jump off from after 600 ticks. Upstream keeps asking for a walking path it cannot have, and because the state pauses the path executor the player never moves and the failure returns as `NEXT_CALC_FAILED`, which is not the event the give-up is waiting for; the bot sits still indefinitely.
- The `shortBaritonePrefix` setting is called `shortCheesecakePrefix`.

### Verification

Every push is compiled against Minecraft 26.2, the unit tests are run, the jar is checked for packaging, and a client is launched under a virtual display to confirm that every mixin applies. A second client then creates a survival world from a fixed seed and plays through a series of scenarios, building what each needs with server commands: it walks a fixed distance with the goal drawn as the box and then as the beacon beam and photographs the beam; drives a trip over the control socket; climbs free-hanging, twisting and weeping vines; runs `#mine` twice and checks that it stops at the requested count of what the blocks drop; and flies with an elytra above the build limit in the Overworld, through Nether terrain below the roof, and over the roof, landing each time. Building, farming, following and the other processes are only covered by the unit tests, and reports on them are welcome.

## Building

A JDK 25 is required. Everything else is fetched by Gradle.

```
./gradlew build
```

The mod jar is written to `build/libs`. `./gradlew test` runs the unit tests on their own, and `./gradlew runClient` starts a development client with the mod loaded.

### Releasing

Set `mod_version` in `gradle.properties` to the new number, then push an annotated tag of the form `vX.Y.Z`. CI builds the jar with that version, runs the unit tests and the headless client, and publishes a GitHub release with the jar and its SHA-256 checksum. The body of the tag message becomes the release notes; a tag without one gets GitHub's generated notes. Git drops lines that start with `#` from a tag message, so write headings another way or tag with `--cleanup=verbatim`. A pre-release suffix such as `v0.3.0-rc.1` publishes a pre-release.

The code uses the names Minecraft ships with, which are the ones Baritone uses too, so a patch taken from upstream applies apart from the package name and this fork's own differences. [SETUP.md](SETUP.md) covers the development setup, the project layout and what each CI job does.

## Reporting problems

Please open an issue on GitHub. Include the Minecraft log, the command you ran and the output of `#modified`. If the problem also occurs with Baritone on 26.2, mention that, since the fix may belong upstream.

## Credits and license

Baritone was written by leijurv, Brady and their contributors, and this project would not exist without their work. If you find it useful, support the original at [cabaletta/baritone](https://github.com/cabaletta/baritone).

Cheesecake is distributed under the GNU Lesser General Public License, version 3, like Baritone. See [LICENSE](LICENSE).
