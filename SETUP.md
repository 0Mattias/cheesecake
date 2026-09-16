# Developing Cheesecake

This page is for building the mod from source and working on it. Installing a built jar is covered in the [README](README.md#installation).

## Prerequisites

- Git.
- A JDK 25. Gradle downloads everything else: Minecraft, Fabric Loader and the libraries.

## Building

```
git clone https://github.com/0Mattias/cheesecake.git
cd cheesecake
./gradlew build
```

The mod jar is written to `build/libs`. Its version comes from `git describe`, so a tagged commit produces `cheesecake-0.2.0.jar` and any other commit a snapshot name that includes the commit hash. `mod_version` in `gradle.properties` is only the fallback for a checkout without Git history.

`./gradlew test` runs the unit tests on their own. `./gradlew runClient` starts a development client with the mod loaded, using an offline account, which is the quickest way to try a change by hand.

## Layout

Everything lives in one Fabric project.

- `src/main/java/cheesecake/api` is the public surface: settings, goals, events, commands and process interfaces.
- `src/main/java/cheesecake/pathing` is the pathfinder and the movements it can plan.
- `src/main/java/cheesecake/process` holds the long-running tasks such as mining, building, farming, following and elytra flight, and `behavior` the per-tick behaviours that execute paths and look around.
- `src/main/java/cheesecake/command` is the chat command layer, `cache` the chunk cache, `launch/mixins` the hooks into the game, `agent` the status snapshot and the local control socket, and `utils` the rest.
- `src/schematica_api` holds compile-only stubs of the Litematica and Schematica APIs. They must never end up in the jar, and CI checks that they do not.
- `src/autotest/java` holds the driver and the stages of the in-world test. `runClient` has it on its classpath through the main source set's runtime classpath, and the jar task does not, so the harness never ships; the mod reaches the driver by name from `cheesecake.utils.AutoTestHook` rather than by type. CI checks the jar does not carry it.
- `src/test/java` holds the unit tests.

## Names

Minecraft has shipped with Mojang's names since 26.1, so there are no mappings: the classes in the code are the ones in the game jar, and they are the names Baritone uses, so a change taken from upstream applies as it is apart from the package name. When a signature is not obvious, the game jar in the Gradle cache answers it:

```
$JAVA_HOME/bin/javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar net.minecraft.world.level.Level
```

Up to 0.4.0 the code used Yarn mappings; the `1.21.11` branch keeps that state.

## Continuous integration

Every push runs four jobs, defined in `.github/workflows/build.yml`.

- **build** compiles the mod and the test harness, runs the unit tests and checks the jar: no stub classes, no test harness, the Nether pathfinder present, no nested library jars, version substituted, icon present, and on a tag a jar named after it.
- **headless client smoke test** launches the client under a virtual display and fails on any mixin that does not apply.
- **in-world pathing test** launches the client with `CHEESECAKE_AUTO_TEST=true`. The client creates a survival world from a fixed seed and runs the stages in `cheesecake.utils.autotest` one after another: a walk with the goal drawn as the box and then as the beacon beam, a trip over the control socket, an `#explore` that must head for an uncached chunk, climbs up three kinds of vines, two `#mine` runs that must count what the blocks drop, a box built from a `#sel` selection, a `#follow` after an animal, a `#farm` of a ripe field, a `#goto <block>` that must find the block itself, a tunnel the bot mines through and the backfill must close again, an inventory swap that can only happen while the pauser holds the path, and four elytra flights. Each stage builds its scenario with server commands, so only the first walk depends on the terrain. The client exits with `PASS` once every stage passed, and the screenshot of the beam is uploaded as an artifact. `CheesecakeAutoTest`, in the `src/autotest/java` source set, is the driver; run it locally the same way, leave the window alone, and it leaves the world under `run/saves`. `CHEESECAKE_AUTO_TEST_ONLY` names the stages to run, comma-separated, when working on one of them.
- **publish release** runs only for a version tag, after the other three pass, and is described under releasing in the [README](README.md#releasing).

A separate weekly workflow, `.github/workflows/upstream-watch.yml`, compares Baritone's branch with the last upstream commit recorded in `.github/upstream-baseline` and keeps an issue up to date with anything new. After reviewing those commits, move the baseline forward.

## IDE

IntelliJ IDEA opens the project as a Gradle project without further setup, and the run configurations for the client come from the Fabric Loom plugin.
