/*
 * This file is part of Cheesecake.
 *
 * Cheesecake is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cheesecake is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Cheesecake.  If not, see <https://www.gnu.org/licenses/>.
 */

package cheesecake.utils;

import cheesecake.Cheesecake;
import cheesecake.api.event.events.PathEvent;
import cheesecake.api.event.events.TickEvent;
import cheesecake.api.event.listener.AbstractGameEventListener;
import cheesecake.utils.autotest.AutoTestContext;
import cheesecake.utils.autotest.AutoTestFailure;
import cheesecake.utils.autotest.BuildStage;
import cheesecake.utils.autotest.ElytraStage;
import cheesecake.utils.autotest.FarmStage;
import cheesecake.utils.autotest.FollowStage;
import cheesecake.utils.autotest.MineStage;
import cheesecake.utils.autotest.PlatformStage;
import cheesecake.utils.autotest.PrepareStage;
import cheesecake.utils.autotest.SocketStage;
import cheesecake.utils.autotest.Stage;
import cheesecake.utils.autotest.VineStage;
import cheesecake.utils.autotest.WalkStage;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An end-to-end test of the mod inside a running client, used by CI. With the environment variable
 * {@code CHEESECAKE_AUTO_TEST=true} the client creates a survival world from a fixed seed as soon as
 * it reaches the title screen and runs the stages in {@link cheesecake.utils.autotest} one after
 * another: a walk with the goal drawn as the box and then as the beacon beam, a trip driven over the
 * control socket, climbs up three kinds of vines, two {@code #mine} runs that have to count what
 * the blocks drop, a box built from a selection, a walk after an animal, a field harvested and
 * replanted, and four elytra flights. Each stage builds what it needs with server commands,
 * so the run does not depend on the terrain beyond the first walk. Progress is written to standard
 * output with the {@value AutoTestContext#TAG} prefix; the final line is either {@code PASS} or
 * {@code FAIL}, and a failure also exits with status 1.
 * <p>
 * Baritone shipped a test like this until 2021. It went away with the virtual display it needed,
 * which the continuous integration of this fork has since put back.
 */
public final class CheesecakeAutoTest implements AbstractGameEventListener {

    public static final boolean ENABLED = "true".equals(System.getenv("CHEESECAKE_AUTO_TEST"));
    /**
     * A comma-separated list of stage names to run instead of all of them, for working on one
     * stage. The world is prepared and the platform built regardless, since the later stages need
     * them.
     */
    private static final String ONLY = System.getenv("CHEESECAKE_AUTO_TEST_ONLY");

    private static final String TAG = AutoTestContext.TAG;
    private static final int WARMUP_TICKS = 100;
    private static final int MAX_TICKS_BEFORE_START = 6000;
    /**
     * A backstop over the stages' own timeouts.
     */
    private static final int MAX_TICKS = 30000;

    private final Cheesecake cheesecake;
    private final Deque<Stage> stages = select(List.of(
            new PrepareStage(),
            new WalkStage(),
            new SocketStage(),
            new PlatformStage(),
            new VineStage(VineStage.Kind.VINE),
            new VineStage(VineStage.Kind.TWISTING),
            new VineStage(VineStage.Kind.WEEPING),
            new MineStage("iron_ore", 8, Items.RAW_IRON, 3),
            new MineStage("stone", 16, Items.COBBLESTONE, -5),
            new BuildStage(),
            new FollowStage(),
            new FarmStage(),
            new ElytraStage(ElytraStage.Trip.OVERWORLD_ABOVE_LIMIT),
            new ElytraStage(ElytraStage.Trip.OVERWORLD_AUTO_JUMP),
            new ElytraStage(ElytraStage.Trip.NETHER_BELOW_ROOF),
            new ElytraStage(ElytraStage.Trip.NETHER_ABOVE_ROOF)
    ));
    private final int stageCount = this.stages.size();
    private boolean started;
    private boolean finished;
    private int ticksBeforeStart;
    private int ticksInWorld;
    private AutoTestContext context;
    private Stage current;
    private int passed;

    public CheesecakeAutoTest(Cheesecake cheesecake) {
        this.cheesecake = cheesecake;
        log("enabled: will create a world and run " + this.stageCount + " stages: "
                + this.stages.stream().map(Stage::name).collect(Collectors.joining(", ")));
    }

    private static Deque<Stage> select(List<Stage> all) {
        if (ONLY == null || ONLY.isBlank()) {
            return new ArrayDeque<>(all);
        }
        Set<String> wanted = new HashSet<>(Arrays.asList(ONLY.split(",")));
        Deque<Stage> selected = new ArrayDeque<>();
        for (Stage stage : all) {
            if (stage instanceof PrepareStage || stage instanceof PlatformStage || wanted.remove(stage.name().trim())) {
                selected.add(stage);
            }
        }
        if (!wanted.isEmpty()) {
            throw new IllegalArgumentException("unknown stages in CHEESECAKE_AUTO_TEST_ONLY: " + wanted);
        }
        return selected;
    }

    @Override
    public void onTick(TickEvent event) {
        if (this.finished) {
            return;
        }
        try {
            tick(event);
        } catch (AutoTestFailure e) {
            fail(e.getMessage(), e.getCause());
        } catch (Throwable t) {
            fail("unexpected " + t, t);
        }
    }

    private void tick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.started) {
            this.ticksBeforeStart++;
            // A fresh profile shows the accessibility onboarding screen before the title screen, and
            // nothing would ever dismiss it. Opt out before the client picks its first screen, and if
            // it is already up, do what its Continue button does.
            mc.options.onboardAccessibility = false;
            if (mc.gui.screen() instanceof AccessibilityOnboardingScreen) {
                log("dismissing the accessibility onboarding screen");
                mc.options.onboardingAccessibilityFinished();
                mc.gui.setScreen(new TitleScreen());
                return;
            }
            if (mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
                this.started = true;
                configureOptions(mc.options);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle(); // what F1 does; keeps the HUD out of the screenshots
                }
                createWorld(mc);
            } else if (this.ticksBeforeStart % 100 == 0) {
                log("waiting for the title screen, tick " + this.ticksBeforeStart + ", screen=" + name(mc.gui.screen()) + ", overlay=" + name(mc.gui.overlay()));
            }
            if (this.ticksBeforeStart > MAX_TICKS_BEFORE_START) {
                fail("never reached the title screen; screen=" + name(mc.gui.screen()) + ", overlay=" + name(mc.gui.overlay()), null);
            }
            return;
        }
        if (event.getType() != TickEvent.Type.IN || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.gui.screen() instanceof PauseScreen) {
            // The pause menu would stop the integrated server ticking.
            mc.gui.setScreen(null);
        }
        this.ticksInWorld++;
        if (this.ticksInWorld < WARMUP_TICKS) {
            if (this.ticksInWorld % 20 == 0) {
                log("waiting for the world to settle, tick " + this.ticksInWorld);
            }
            return;
        }
        if (this.context == null) {
            this.context = new AutoTestContext(this.cheesecake, mc);
        }
        if (this.current == null) {
            this.current = this.stages.poll();
            if (this.current == null) {
                pass("all " + this.passed + " stages passed after " + this.ticksInWorld + " ticks in the world");
                return;
            }
            log("stage " + (this.passed + 1) + " of " + this.stageCount + ": " + this.current.name());
        }
        if (this.current.run(this.context)) {
            this.passed++;
            log("stage " + this.current.name() + " passed");
            this.current = null;
        }
        if (this.ticksInWorld > MAX_TICKS) {
            fail("the stages did not finish within " + MAX_TICKS + " ticks; " + this.passed + " passed, in " + this.current.name(), null);
        }
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if (this.finished) {
            return;
        }
        log("path event " + event);
        try {
            if (this.current != null) {
                this.current.onPathEvent(event);
            }
        } catch (AutoTestFailure e) {
            fail(e.getMessage(), e.getCause());
        }
    }

    @Override
    public void onPlayerDeath() {
        if (!this.finished) {
            fail("the player died" + (this.current != null ? " during " + this.current.name() : ""), null);
        }
    }

    private static void configureOptions(Options options) {
        options.framerateLimit().set(20);
        options.renderDistance().set(4);
        options.simulationDistance().set(5);
        options.cloudStatus().set(CloudStatus.OFF);
        options.particles().set(ParticleStatus.MINIMAL);
        options.biomeBlendRadius().set(0);
        options.pauseOnLostFocus = false;
    }

    private static void createWorld(Minecraft mc) {
        String name = "cheesecake-autotest-" + System.currentTimeMillis();
        log("creating world " + name + " with seed " + AutoTestContext.SEED);
        LevelSettings info = new LevelSettings(
                name,
                GameType.SURVIVAL,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                true,
                WorldDataConfiguration.DEFAULT
        );
        mc.createWorldOpenFlows().createFreshLevel(
                name,
                info,
                new WorldOptions(AutoTestContext.SEED, true, false),
                WorldPresets::createNormalWorldDimensions,
                mc.gui.screen()
        );
    }

    private void pass(String message) {
        this.finished = true;
        log("PASS: " + message);
        Minecraft.getInstance().stop();
    }

    private void fail(String message, Throwable cause) {
        this.finished = true;
        log("FAIL: " + message);
        if (cause != null) {
            cause.printStackTrace();
        }
        System.out.flush();
        System.exit(1);
    }

    private static String name(Object o) {
        return o == null ? "none" : o.getClass().getSimpleName();
    }

    private static void log(String message) {
        System.out.println(TAG + " " + message);
    }
}
