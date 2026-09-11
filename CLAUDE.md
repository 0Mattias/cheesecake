# Working on Cheesecake

Cheesecake is Baritone for Minecraft 26.2 on Fabric: a single Fabric project ported from upstream Baritone's 1.19.4 branch, brought to parity with upstream's 1.21.11 branch, migrated from Yarn to Mojang's names and moved to 26.2 with the delta of upstream's 26.2 branch. The package `cheesecake.*` mirrors upstream's `baritone.*`. README.md says what the mod does and SETUP.md describes the layout; this file is for changing the code.

## Build and test

- JDK 25 is required and Gradle fetches everything else. With Homebrew's formula on macOS: `export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`.
- `./gradlew build` compiles, runs the unit tests and writes `build/libs/cheesecake-<version>.jar`; `./gradlew test` runs only the tests. About a minute once dependencies are cached.
- `CHEESECAKE_AUTO_TEST=true ./gradlew runClient` runs the in-world test locally: a game window opens, a world is created under `run/saves`, the stages in `cheesecake.utils.autotest` run one after another (a walk, the control socket, an `#explore`, vine climbs, two `#mine` runs, a `#sel` build, a `#follow`, a `#farm`, a `#goto <block>`, a backfill, an inventory pause, four elytra flights) and the client exits by itself after several minutes. Leave the window alone. The driver is `CheesecakeAutoTest`; a stage builds its scenario with server commands and fails with a message that names the stage. `CHEESECAKE_AUTO_TEST_ONLY=elytra-nether-below-roof` (comma-separated stage names) runs only those stages after the world is prepared and the platform built, which is the way to iterate on one of them.
- Unit tests are JUnit 4 under `src/test/java`. The mapped Minecraft jar is on the test classpath, so tests can read game data (`LootTableDropsTest` reads the vanilla loot tables) but cannot bootstrap the registries.

## Names

Minecraft ships with Mojang's names since 26.1 and Yarn ended at 1.21.11, so there are no mappings and the code uses the same names as upstream: a change taken from upstream applies as it is, apart from the package name and this fork's own differences. To check a signature, inspect the game jar in the Gradle cache:

```
$JAVA_HOME/bin/javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar net.minecraft.world.level.Level
```

The `1.21.11` branch keeps the last Yarn-based state, for fixes to 0.4.0.

## Upstream

```
git remote add upstream https://github.com/cabaletta/baritone.git
git fetch upstream 26.2
```

The fork's base is upstream commit `d3c170af`. Everything upstream did after it on the 1.21.11 branch and then on the 26.2 branch has been backported or deliberately left out (the Forge, NeoForge and tweaker builds, and upstream's version of the `renderGoalXZBeacon` beam). Upstream paths map as `src/api/java/baritone/api` to `src/main/java/cheesecake/api`, `src/launch/java/baritone/launch/mixins` to `src/main/java/cheesecake/launch/mixins`, and `src/main/java/baritone` to `src/main/java/cheesecake`. `.github/upstream-baseline` records the last reviewed upstream commit; the weekly upstream-watch workflow files an issue when upstream moves, and the baseline is bumped after a review.

## Continuous integration

`.github/workflows/build.yml` runs on every push to `main` and to `claude/**` branches: a build with jar checks, a headless client that proves the mixins apply, and the in-world pathing test. A version tag additionally runs the publish job. A workflow can only be started with `gh workflow run` once it exists on `main`.

## Releasing

Set `mod_version` in `gradle.properties`, commit and push, then `git tag -a vX.Y.Z --cleanup=verbatim -F notes.md` and push the tag. The tag message's body becomes the release notes and `--cleanup=verbatim` keeps markdown headings, which git would otherwise strip as comments. The jar's version comes from `git describe`, which is why CI fetches tags. The release waits for all three test jobs.

## Conventions

- One commit per logical change, with a body that says why. Backports cite the upstream commit hashes and author.
- Keep upstream's structure and names so future backports diff cleanly; do not reformat files wholesale.
- `src/schematica_api` is compile-only. CI fails the build if those classes reach the jar.
- Documentation has one home per audience: README.md and USAGE.md for players, FEATURES.md for what the pathfinder can do, SETUP.md for developers, AI_AGENT_README.md for programs driving the bot through chat or the control socket.
- Deliberate differences from upstream are recorded in the README's status section. Three to know about: block drops come from the loot-table JSON in the jars (`LootTableDrops`) rather than from rolling tables through a faked server; the `CANCELED` path event fires only when a path or calculation was actually cancelled; and `CustomGoalProcess` lets a finishing path report `AT_GOAL` instead of cancelling it in the same tick, which is what upstream does.

## Things that cost time once

- A fresh profile shows `AccessibilityOnboardingScreen` before the title screen; anything that automates the client must opt out first (`options.onboardAccessibility = false`).
- `IPlayerContext.world()` is a `World`, not a `ClientWorld`; check `instanceof` before calling client-only methods.
- The control manager cancels the segment on every idle tick, and the elytra process asks for `CANCEL_AND_SET_GOAL` on every tick while flying.
- `gh pr view N` needs `--repo 0Mattias/cheesecake`; without it the number can resolve against another repository.
- `javap` on macOS is a stub that fails with "Unable to locate a Java Runtime" unless a JDK is installed system-wide; call `$JAVA_HOME/bin/javap` instead.

## Open work

Follow-ups are tracked as GitHub issues rather than in this file. Every process under `cheesecake.process` now has a stage in the in-world test; what is not covered is the parts of them that need terrain the fixed seed does not provide, and the settings each stage leaves at their defaults.
