# Using Cheesecake

Cheesecake is driven from the chat box. This page covers the command prefix, the commands you will reach for most, the settings worth knowing about, where the mod keeps its files, and the usual reasons it appears not to respond. In the game, `#help` lists every command and `#help <command>` explains one in detail, including its arguments.

## Prefix

Commands start with `#`: `#goto 100 64 100`. While the `chatControl` setting is on, a message without the prefix is also tried as a command, but a mistyped one then goes to public chat, so the prefixed form is the safer habit. `prefixControl` turns the prefixed form off and `chatControl` the unprefixed form; keep at least one of them on. If you lock yourself out, delete `.minecraft/cheesecake/settings.txt` and restart the game. The character itself is the `prefix` setting.

## Commands

Coordinates accept `~` for a value relative to your position, so `#goal ~ 64 ~-100` is a hundred blocks north of you at y 64. Block names are the usual identifiers, with or without the `minecraft:` namespace.

### Moving

| Command | What it does |
| --- | --- |
| `#goto <x> <y> <z>`, `#goto <x> <z>`, `#goto <y>` | Set a goal and start walking |
| `#goto <block>` | Walk to the nearest block of that type, for example `#goto ender_chest` |
| `#goal <x> <y> <z>` then `#path` | Set a goal without moving, then start. `#goal` alone targets your feet and `#goal clear` removes the goal |
| `#thisway <blocks>` then `#path` | Go that far in the direction you are facing |
| `#invert` | Get as far from the goal as possible instead of as close |
| `#come` | Walk to where the camera is, which is useful with a freecam |
| `#axis` | Head for the nearest axis or diagonal at y 120 (`axisHeight`) |
| `#surface` | Get out of a cave to the nearest open air above |
| `#click` | Pick the destination on screen: right-click to stand on a block, left-click to walk into it, drag to select an area |
| `#stop`, `#cancel`, `#forcecancel` | Stop whatever is running. `#pause` and `#resume` suspend and continue it |

### Working

| Command | What it does |
| --- | --- |
| `#mine <block>...` | Dig for the blocks, exploring around y 11 for ores. `legitMine` restricts it to ores it has seen |
| `#mine <count> <block>` | Stop once the inventory holds that many of what the block drops; `#mine 64 iron_ore` counts raw iron |
| `#tunnel` | Dig a one-by-two tunnel straight ahead. `#tunnel <height> <width> <depth>` clears a box instead |
| `#farm [range] [waypoint]` | Harvest, replant and bone-meal crops. `farmUsingSelection` limits it to the current selection |
| `#build <file> [x y z]` | Build a schematic from `.minecraft/schematics` with its origin at your feet or at the given position. MCEdit, Sponge and Litematica files are accepted |
| `#litematica [index]`, `#schematica` | Build the schematic currently loaded in Litematica or Schematica |
| `#explore [x z]` | Keep walking to the nearest chunk it has never seen. `#explorefilter <file.json> [invert]` restricts it to a list of chunks |
| `#follow player <name>`, `#follow players`, `#follow entity <type>`, `#follow entities` | Follow a player or entities |
| `#pickup` | Collect dropped items. `#help pickup` explains the arguments |
| `#sel` | Selection commands: clear an area, fill it, build walls or a shell, and more. `#help sel` lists them |

### Flying

`#elytra` flies to the current goal with an elytra and firework rockets from the hotbar. It works in the Nether, the Overworld and the End. Set a goal with `#goal` first, then `#elytra`. `#elytra reset` recalculates from scratch and `#elytra repack` re-reads the loaded chunks. The first use prints a summary of what it needs; `elytraTermsAccepted` silences that.

In the Nether the pathfinder can predict terrain beyond what you have seen when it knows the world seed (`elytraNetherSeed`, with `elytraPredictTerrain` on). Long trips route above the build limit when `elytraAllowAboveBuildLimit` is on and the distance exceeds `elytraLongDistanceThreshold`; in the Nether that also needs `elytraAllowAboveRoof`. `elytraAutoJump` lets it walk to an edge and take off on its own, and `elytraConserveFireworks` together with `elytraFireworkSpeed` slow it down.

### Information

| Command | What it does |
| --- | --- |
| `#status` | One line of JSON: position, dimension, health, the process in control, path progress and estimates. Meant for programs; see [AI_AGENT_README.md](AI_AGENT_README.md) |
| `#eta` | Estimated ticks to the end of the current segment and to the goal |
| `#proc` | Details about the process in control |
| `#find <block>` | Search the chunk cache for a block |
| `#wp` | Waypoints. `#wp save user <name>` stores your position and `#wp goal <name>` then `#path` returns to it; `#wp goal death` lists where you last died; `#sethome` and `#home` are shortcuts |
| `#version`, `#help` | The version, and the list of commands |

### Maintenance

`#repack` re-reads the chunks around you into the cache, `#reloadall` and `#saveall` reload and save the cache for this world, `#render` fixes chunks that stopped rendering, `#blacklist` tells `#goto <block>` to skip the nearest candidate, and `#gc` asks the JVM to collect garbage.

## Settings

Say a boolean setting's name to toggle it (`#allowBreak`) or give it a value (`#allowBreak false`), and give numeric settings a value (`#primaryTimeoutMS 250`). `#<setting> reset` restores one setting, `#reset` restores all of them, and `#modified` lists the ones that differ from their defaults. Names are case insensitive. Every setting is documented in [Settings.java](src/main/java/cheesecake/api/Settings.java).

Some worth knowing about:

- `allowBreak`, `allowPlace`, `allowSprint`, `allowParkour` and `allowParkourPlace` decide what the pathfinder may do. `blockPlacementPenalty` and `acceptableThrowawayItems` shape block placing; `blocksToAvoidBreaking` protects blocks.
- `avoidance` keeps away from mobs and spawners.
- `legitMine` and `mineScanDroppedItems` affect mining; `backfill` fills tunnels behind you.
- `buildInLayers`, `buildRepeatDistance` and `buildRepeatDirection` affect building.
- `followRadius`, `farmUsingSelection` and `worldExploringChunkOffset`.
- `renderCachedChunks` with `cachedChunksOpacity` draws the whole cache, which is striking but expensive.
- The `elytra*` settings described above.
- `agentApiPort` opens the local control socket for programs.

## Files

Settings are stored in `.minecraft/cheesecake/settings.txt`. Cached chunks and waypoints are kept per world: inside the save folder for singleplayer worlds, and under `.minecraft/cheesecake/<server address>/` for servers. Schematics are read from `.minecraft/schematics`.

## When nothing happens

- Confirm the mod is installed: a `cheesecake` folder appears in `.minecraft` on first launch, and `#version` answers in chat.
- Check the prefix and the `chatControl` and `prefixControl` settings described above.
- The mod runs on the client only. It does nothing installed on a server.
- Where the fork differs from Baritone, the status section of the [README](README.md#status) explains what to expect.
