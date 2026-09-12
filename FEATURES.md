# What Cheesecake can do

The pathfinder is Baritone's, and much of this page is adapted from Baritone's own description of it. Where this fork behaves differently, the text says so.

## Pathing

- **Long-distance pathing and splicing.** Paths are calculated in segments, and the next segment is precalculated while the current one is being walked, so the player keeps moving towards the goal.
- **Chunk caching.** Chunks are reduced to a compact representation with four states (air, solid, water, avoid) and kept in memory, and optionally on disk, so that paths can span far more than the render distance. [Example](https://www.youtube.com/watch?v=dyfYKSubhdc)
- **Block breaking.** Breaking blocks is part of the path cost, and the cost takes your tools into account: with an efficient pickaxe it may dig through a stone wall where with a wooden one it would rather climb over it.
- **Block placing.** Placing blocks is part of the path too, including sneak-back-placing and pillaring. A configurable penalty, one second by default, keeps it from spending blocks needlessly, and the throwaway blocks it may use are configurable and default to cobblestone, dirt and netherrack. [Example](https://www.youtube.com/watch?v=F6FbI1L9UmU)
- **Falling.** Up to three blocks onto solid ground by default, further if you accept some damage, up to twenty-three blocks with a water bucket in the hotbar (it places the water beneath itself), and any distance into still water.
- **Ladders and vines.** It climbs ladders and vines, including the weeping and twisting vines of the Nether, by pressing space, and it can break a fall by catching a ladder or vine when the game allows that.
- **Doors and fence gates, slabs and stairs.**
- **Falling blocks.** The cost of breaking a block includes the sand or gravel that will fall onto it, and it will not break a block that touches a liquid, so it no longer digs out the bottom of a gravel stack under a lava lake.
- **Dangerous blocks.** It stays out of fire, off magma, away from lava edges and out of liquids it could drown in.
- **Parkour.** Sprint jumps over gaps of one to three blocks, and placing the landing block mid-jump when the gap needs it.

## Elytra

`#elytra` flies to the goal with firework rockets, in the Nether, the Overworld and the End. A separate pathfinder plans around terrain in a compact copy of the loaded chunks and, in the Nether, in terrain predicted from the world seed. Long trips can climb above the build limit and fly straight, and on arrival it searches for a safe place to land.

## Mining

`#mine` explores for ore, digs to it, and can stop at a count. The count is measured in what the block drops, resolved from the loot tables shipped inside the game and mod jars, so iron ore is counted as raw iron and modded blocks work as well. The [README](README.md#differences-from-baritone) describes the limits of that approach.

## Building and farming

`#build` builds MCEdit, Sponge and Litematica schematics, layer by layer if asked, and `#farm` harvests, replants and bone-meals crops within a range or a selection.

## How the pathfinder works

It is A* with some modifications.

- **Segmented calculation.** Traditional A* calculates until the most promising node is in the goal, but in Minecraft with a limited render distance the environment is not known all the way to the goal. Calculation therefore ends in one of three ways: a path all the way to the goal, running out of time, or reaching the edge of the loaded chunks. Whenever the calculation thread finds that the most promising node is at that edge it increments a counter, and if that happens more than fifty times (configurable) it stops early, which happens with very low render distances. Otherwise it continues until the timeout (also configurable) or until it reaches the goal.
- **Incremental cost backoff.** When calculation ends without reaching the goal, one segment has to be picked to execute first, assuming the next will be calculated at the end of it. The pathfinder keeps track of the best node under several increasing coefficients and picks the node with the smallest coefficient that gets at least five blocks from the start. Baritone's author wrote this up for the predecessor project, MineBot, and the [write-up](https://docs.google.com/document/d/1WVHHXKXFdCR1Oz__KtK8sFqyvSwJN_H4lftkHFgmzlc/edit) still applies.
- **Minimum improvement repropagation.** Alternative routes that improve a node by less than a hundredth of a tick are ignored, because propagating the improvement to every connected node costs far more than the half a millisecond it would save.
- **Backtrack cost favouring.** While calculating the next segment, backtracking along the current segment is made much cheaper, though still positive, so it will not backtrack without reason. This lets it splice onto the next segment as early as possible when that segment begins by retracing the current one. [Example](https://www.youtube.com/watch?v=CGiMcb8-99Y)
- **Backtrack detection and pausing.** Calculation runs on its own thread, and the game thread can see the latest node it considered and the best path so far, which are rendered light blue and dark blue. When the best path so far passes through the player's position on the current segment, execution pauses if it is safe to, since there is no point walking on if the plan is about to turn around. The best path takes the incremental cost backoff into account, so it matches what the calculation thread will actually pick.

## Goals

- **GoalBlock**: stand inside one specific block at foot level.
- **GoalXZ**: an x and z coordinate at any height, for long-distance travel.
- **GoalYLevel**: a y coordinate.
- **GoalTwoBlocks**: stand in a block position at either foot or eye level.
- **GoalGetToBlock**: stand adjacent to, below or on top of a block.
- **GoalNear**: get within a radius of a position, used for following entities.
- **GoalAxis**: a position on an axis or diagonal at a configurable height.
- **GoalComposite**: a list of goals, any one of which satisfies it. `#mine diamond_ore` builds one from a GoalTwoBlocks for every known diamond ore.

## Not there

Trapdoors, sprint jumping in a one-by-two corridor, boats and horses. Baritone's [issue tracker](https://github.com/cabaletta/baritone/issues) has the longer list, and most of it applies here too.
