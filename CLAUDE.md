# Working on Cheesecake

Cheesecake is Baritone for Minecraft 1.21.11 on Fabric: a single Fabric project on Yarn mappings, ported from upstream Baritone's 1.19.4 branch and since brought to parity with upstream's own 1.21.11 branch. The package `cheesecake.*` mirrors upstream's `baritone.*`. README.md says what the mod does and SETUP.md describes the layout; this file is for changing the code.

## Build and test

- JDK 21 is required and Gradle fetches everything else. With Homebrew's formula on macOS: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- `./gradlew build` compiles, runs the unit tests and writes `build/libs/cheesecake-<version>.jar`; `./gradlew test` runs only the tests. About a minute once dependencies are cached.
- `CHEESECAKE_AUTO_TEST=true ./gradlew runClient` runs the in-world test locally: a game window opens, a world is created under `run/saves`, the pathfinder walks 120 blocks, and the client exits by itself. Leave the window alone. The routine is `CheesecakeAutoTest`.
- Unit tests are JUnit 4 under `src/test/java`. The mapped Minecraft jar is on the test classpath, so tests can read game data (`LootTableDropsTest` reads the vanilla loot tables) but cannot bootstrap the registries.

## Mappings

The code uses Yarn. Upstream uses Mojang mappings with Parchment, so every change taken from upstream needs its Minecraft symbols translated. To find a Yarn name, inspect the mapped jar in the Gradle cache:

```
javap -cp ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-*/minecraft-merged-1.21.11-*.jar net.minecraft.world.World
```

The jar directly under `fabric-loom/1.21.11/` is obfuscated and not useful. `./gradlew genSources` has not produced sources here.

## Upstream

```
git remote add upstream https://github.com/cabaletta/baritone.git
git fetch upstream 1.21.11
```

The fork's base is upstream commit `d3c170af`. Everything upstream did after it on the 1.21.11 branch has been backported or deliberately left out (the `renderGoalXZBeacon` beam and the Forge, NeoForge and tweaker builds). Upstream paths map as `src/api/java/baritone/api` to `src/main/java/cheesecake/api`, `src/launch/java/baritone/launch/mixins` to `src/main/java/cheesecake/launch/mixins`, and `src/main/java/baritone` to `src/main/java/cheesecake`. `.github/upstream-baseline` records the last reviewed upstream commit; the weekly upstream-watch workflow files an issue when upstream moves, and the baseline is bumped after a review.

## Continuous integration

`.github/workflows/build.yml` runs on every push to `main` and to `claude/**` branches: a build with jar checks, a headless client that proves the mixins apply, and the in-world pathing test. A version tag additionally runs the publish job. A workflow can only be started with `gh workflow run` once it exists on `main`.

## Releasing

Set `mod_version` in `gradle.properties`, commit and push, then `git tag -a vX.Y.Z --cleanup=verbatim -F notes.md` and push the tag. The tag message's body becomes the release notes and `--cleanup=verbatim` keeps markdown headings, which git would otherwise strip as comments. The jar's version comes from `git describe`, which is why CI fetches tags. The release waits for all three test jobs.

## Conventions

- One commit per logical change, with a body that says why. Backports cite the upstream commit hashes and author.
- Keep upstream's structure and names so future backports diff cleanly; do not reformat files wholesale.
- `src/schematica_api` is compile-only. CI fails the build if those classes reach the jar.
- Documentation has one home per audience: README.md and USAGE.md for players, FEATURES.md for what the pathfinder can do, SETUP.md for developers, AI_AGENT_README.md for programs driving the bot through chat or the control socket.
- Deliberate differences from upstream are recorded in the README's status section. Two to know about: block drops come from the loot-table JSON in the jars (`LootTableDrops`) rather than from rolling tables through a faked server, and the `CANCELED` path event fires only when a path or calculation was actually cancelled.

## Things that cost time once

- A fresh profile shows `AccessibilityOnboardingScreen` before the title screen; anything that automates the client must opt out first (`options.onboardAccessibility = false`).
- `IPlayerContext.world()` is a `World`, not a `ClientWorld`; check `instanceof` before calling client-only methods.
- The control manager cancels the segment on every idle tick, and the elytra process asks for `CANCEL_AND_SET_GOAL` on every tick while flying.
- `gh pr view N` needs `--repo 0Mattias/cheesecake`; without it the number can resolve against another repository.

## Open work

Follow-ups are tracked as GitHub issues rather than in this file. The ones that need a person in the game are the elytra flights, the `#mine` item counts and vine climbing, because the in-world test only covers walking.
