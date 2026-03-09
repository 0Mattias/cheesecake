# Cheesecake AI Agent Skill Guide

This document is designed to help an AI agent easily utilize the Cheesecake mod (a Port/Fork of Baritone to modern Minecraft + Fabric) to automate actions in Minecraft. By utilizing chat commands prefixed with `#`, you have full control over a highly capable pathfinding, mining, building, and exploring engine.

## Core Capabilities
Cheesecake can understand the environment and dynamically:
- Find and break blocks (including considering your current tools).
- Select the best path (walking, swimming, pillaring, parking, falling, ladder climbing, and block placements).
- Handle advanced world interactions, like combat avoidance, liquid avoidance, and block-state awareness.
- Process complex tasks such as mining quantities of specific ores, building schematics, or harvesting and planting crops.

## Usage Interface (Chat Commands)
To control Cheesecake, issue commands in chat starting with a `#`. If you are acting as an AI connecting to the game, simulate typing these commands into the chat input. 

**Basic Navigation:**
* `#goal <x> <y> <z>` or `#goal <x> <z>`: Set a target block coordinate to focus on.
* `#path`: Begin calculating and executing the path to the previously set goal.
* `#goto <x> <y> <z>` or `#goto <x> <z>`: In a single step, set a goal and start navigating to it immediately.
* `#goto <block_type>` (e.g., `#goto diamond_ore`): Start pathing to the closest instance of a specified block.
* `#goal`: Target the block at your own feet (useful to `#invert` escaping behavior).
* `#cancel`, `#stop`, or `#forcecancel`: Stop all pathing and automation tasks immediately.

**Mining and Farming:**
* `#mine <block1> <block2> ...`: Dynamically explore and dig to find target blocks. Can take quantities, e.g., `#mine 64 diamond_ore`.
* `#tunnel 3 2 100`: Clear an area (3m high, 2m wide, 100 deep), or just `#tunnel` for a 1x2 tunnel.
* `#farm <range>`: Automatically harvest, replant, or use bone meal on crops within a given range.

**Exploration and Interacting:**
* `#explore <x> <z>`: Explore new, unseen chunks starting from the designated point (defaults to the player's feet).
* `#follow player <playerName>` or `#follow entity <type>`: Track and consistently follow a specific moving target.
* `#build <schematic_name>.schematic`: Build a schematic originating at the player's feet. Can also be structured as `#build <name>.schematic <x> <y> <z>` for absolute coordinates.
* `#click`: Click a block on your screen space, navigating to it directly (left click for occupying the space, right click for standing on it).

**Settings Manipulation:**
Change boolean or numerical values simply by referencing their names.
* Example: `#allowBreak true` (toggles breaking blocks for pathing).
* Example: `#allowPlace true` (toggles placing throwaway blocks like dirt/cobble for pathing).
* Example: `#primaryTimeoutMS 250` (adjusts an internal integer setting).
* `#reset`: Restores all variables to factory settings.
* `#modified`: Lists all settings currently differing from the default.

## Best Practices for AI Integration
1. **Understand Timeouts & Calculations:** Pathfinding takes time and recalculates per segment dynamically if chunks load slowly. Expect minor delays and asynchronous behavior; monitor coordinates actively if attempting synchronized movement.
2. **Handle Errors and Cancels:** If a path fails, Cheesecake may drop out of navigating. Issue a `#stop`, alter conditions (e.g., check your tool's durability/inventory blocks), and try `#goto` again.
3. **Use the Help System:** Commands are robust. For quick context logic and parameters on commands you discover, type `#help <feature>`.
4. **Waypoints (WP):** For repeated runs to particular static areas without recomputing coordinates manually, save the current location with `#wp save user <label>` and return using `#wp goal <label>` followed by `#path`.
