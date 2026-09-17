# Driving Cheesecake from a program

This page is for software that controls the bot, whether a script or a language model acting as an agent. It covers the two ways to talk to the mod, the commands that matter most, and how to read the bot's state back.

## Two interfaces

**Chat.** Every command can be typed into the game's chat with a `#` prefix, and every reply comes back as a chat message. A program that already types into the game and reads its chat needs nothing else.

**The control socket.** For anything more than a demonstration, use the socket instead of scraping chat. Set the `agentApiPort` setting to a port, for example `#agentApiPort 5777`; the setting persists, and the mod listens on `127.0.0.1` at that port from the next tick on. Set it back to `0` to close the socket. There is no authentication: any process on the same machine can connect, so leave it off when it is not needed.

The protocol is newline-delimited JSON. Each line the client sends is one request; each line the server sends is one message.

```
→ {"type": "hello", "protocol": 1, "version": "0.2.0"}
← {"id": 1, "command": "goto 100 64 100"}
→ {"type": "result", "id": 1, "command": "goto 100 64 100", "ok": true}
→ {"type": "log", "text": "[Cheesecake] Path calculated..."}
→ {"type": "path", "event": "CALC_FINISHED_NOW_EXECUTING"}
← {"id": 2, "status": true}
→ {"type": "status", "id": 2, "inWorld": true, ...}
```

Requests:

- `{"command": "..."}` runs a chat command without the prefix. The reply's `ok` says whether the text matched a command; what the command then did is reported in `log` messages, exactly as it would appear in chat. A line that is not a JSON object is treated as a bare command, so `goto 100 64 100` on its own works too.
- `{"status": true}` answers with the same snapshot `#status` prints, described below.
- An `id` of any JSON type is echoed in the reply so requests and replies can be matched.

Unprompted messages:

- `log`: every message the mod writes to chat, including command errors such as "No goal has been set".
- `path`: one of `CALC_STARTED`, `CALC_FINISHED_NOW_EXECUTING`, `CALC_FAILED`, `NEXT_SEGMENT_CALC_STARTED`, `NEXT_SEGMENT_CALC_FINISHED`, `CONTINUING_ONTO_PLANNED_NEXT`, `SPLICING_ONTO_NEXT_EARLY`, `AT_GOAL`, `PATH_FINISHED_NEXT_STILL_CALCULATING`, `NEXT_CALC_FAILED`, `DISCARD_NEXT` or `CANCELED`. `AT_GOAL`, `CANCELED` and `CALC_FAILED` are the ones worth acting on.
- `error`: the request could not be understood, or a command threw.

Commands run on the game thread, in order. A slow client that stops reading loses messages after a thousand are queued rather than stalling the game.

A minimal Python client:

```python
import json, socket

s = socket.create_connection(("127.0.0.1", 5777))
f = s.makefile("rw", encoding="utf-8", newline="\n")

def send(**request):
    f.write(json.dumps(request) + "\n")
    f.flush()

print(f.readline())                      # hello
send(id=1, command="goto 100 64 100")
send(id=2, status=True)
for line in f:
    message = json.loads(line)
    print(message)
    if message.get("type") == "path" and message["event"] == "AT_GOAL":
        break
```

## Reading state

`#status`, or a `status` request, gives one JSON object:

```json
{
  "version": "0.2.0",
  "inWorld": true,
  "dimension": "minecraft:overworld",
  "position": {"x": 12.5, "y": 64.0, "z": -3.5},
  "feet": {"x": 12, "y": 64, "z": -4},
  "yaw": -90.0, "pitch": 10.0,
  "health": 20.0, "food": 18,
  "onGround": true, "gliding": false, "inWater": false, "inLava": false,
  "mainHand": {"item": "minecraft:diamond_pickaxe", "count": 1},
  "emptyInventorySlots": 30,
  "process": {"name": "CustomGoalProcess", "displayName": "Custom Goal GoalXZ{x=100,z=100}", "temporary": false},
  "pathing": {
    "active": true, "calculating": false,
    "goal": "GoalXZ{x=100,z=100}",
    "segmentPosition": 4, "segmentLength": 63,
    "ticksRemainingInSegment": 118.5, "estimatedTicksToGoal": 340.2
  },
  "elytra": {"active": false, "destination": null, "pathNodes": 0}
}
```

`process` is null when nothing is in control, and the position fields are absent when the player is not in a world. The tick estimates are null when there is no path or the player is not in a world; twenty ticks make a second at normal speed. In chat the line is prefixed like any other message.

## Commands

The full list is in [USAGE.md](USAGE.md) and `help` inside the game. The ones a program uses most:

| Command | Effect |
| --- | --- |
| `goto <x> <y> <z>`, `goto <x> <z>` | Set a goal and start walking |
| `goto <block>` | Walk to the nearest block of that type |
| `goal <x> <y> <z>` then `path` | Set the goal first, start later |
| `mine <block>...`, `mine <count> <block>` | Dig for blocks, optionally until the inventory holds a count of what they drop |
| `tunnel <height> <width> <depth>` | Clear a box ahead |
| `farm [range]` | Harvest and replant crops |
| `build <file> [x y z]` | Build a schematic from `.minecraft/schematics` |
| `explore [x z]` | Walk to chunks never seen before |
| `follow player <name>` | Follow a player |
| `elytra` | Fly to the goal with an elytra and fireworks |
| `wp save user <name>`, `wp goal <name>` then `path` | Store and return to a position |
| `stop` | Cancel everything; `forcecancel` if `stop` does not take |
| `status`, `eta`, `proc` | State, time estimates, details of the process in control |
| `<setting> <value>`, `modified`, `reset` | Change a setting, list changed settings, restore defaults |

## Working with it

- **Wait for events rather than sleeping.** After a command, watch for `path` events: `CALC_FINISHED_NOW_EXECUTING` means it is moving, `AT_GOAL` means it arrived, `CALC_FAILED` means no path was found from where it stands. Poll `status` for position and estimates in between.
- **Expect segments.** Long paths are calculated and walked in pieces, so `NEXT_SEGMENT_CALC_STARTED` and `CONTINUING_ONTO_PLANNED_NEXT` are normal and not a reason to intervene.
- **Recover explicitly.** If a path fails or the bot stalls, send `stop`, change what needs changing (tools, inventory, a nearer intermediate goal), and issue the command again.
- **Read the log.** Command errors and refusals come back as `log` messages, not as `ok: false`. `ok: false` only means the text was not a command at all.
- **Keep the socket private.** It is loopback only and unauthenticated; close it with `agentApiPort 0` when the program is done.
