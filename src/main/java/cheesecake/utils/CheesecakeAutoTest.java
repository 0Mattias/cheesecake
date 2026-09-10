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
import cheesecake.agent.AgentStatus;
import cheesecake.api.event.events.PathEvent;
import cheesecake.api.event.events.TickEvent;
import cheesecake.api.event.listener.AbstractGameEventListener;
import cheesecake.api.pathing.goals.Goal;
import cheesecake.api.pathing.goals.GoalXZ;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.api.utils.IPlayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;

/**
 * An end-to-end test of the mod inside a running client, used by CI. With the environment variable
 * {@code CHEESECAKE_AUTO_TEST=true} the client creates a survival world from a fixed seed as soon as
 * it reaches the title screen, asks the pathfinder to walk a fixed distance, and shuts the game down
 * once the goal is reached. Progress is written to standard output with the {@value #TAG} prefix;
 * the final line is either {@code PASS} or {@code FAIL}, and a failure also exits with status 1.
 * <p>
 * Baritone shipped a test like this until 2021. It went away with the virtual display it needed,
 * which the continuous integration of this fork has since put back.
 */
public final class CheesecakeAutoTest implements AbstractGameEventListener {

    public static final boolean ENABLED = "true".equals(System.getenv("CHEESECAKE_AUTO_TEST"));

    private static final String TAG = "[cheesecake-autotest]";
    private static final long SEED = -928872506371745L;
    /**
     * Blocks to travel along +x from wherever the world puts the player.
     */
    private static final int DISTANCE = 120;
    private static final int WARMUP_TICKS = 100;
    private static final int MAX_TICKS = 4800;
    private static final int MAX_CALC_FAILURES = 8;

    private final Cheesecake cheesecake;
    private boolean started;
    private boolean finished;
    private int ticksInWorld;
    private int calcFailures;
    private Goal goal;
    private BetterBlockPos start;

    public CheesecakeAutoTest(Cheesecake cheesecake) {
        this.cheesecake = cheesecake;
        log("enabled: will create a world and path " + DISTANCE + " blocks");
    }

    @Override
    public void onTick(TickEvent event) {
        if (this.finished) {
            return;
        }
        try {
            tick(event);
        } catch (Throwable t) {
            fail("unexpected " + t, t);
        }
    }

    private void tick(TickEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!this.started) {
            if (mc.currentScreen instanceof TitleScreen && mc.getOverlay() == null) {
                this.started = true;
                configureOptions(mc.options);
                createWorld(mc);
            }
            return;
        }
        if (event.getType() != TickEvent.Type.IN || mc.player == null || mc.world == null) {
            return;
        }
        if (mc.currentScreen instanceof GameMenuScreen) {
            // The pause menu would stop the integrated server ticking.
            mc.setScreen(null);
        }
        this.ticksInWorld++;
        if (this.ticksInWorld < WARMUP_TICKS) {
            if (this.ticksInWorld % 20 == 0) {
                log("waiting for the world to settle, tick " + this.ticksInWorld);
            }
            return;
        }

        IPlayerContext ctx = this.cheesecake.getPlayerContext();
        if (this.goal == null) {
            this.start = ctx.playerFeet();
            this.goal = new GoalXZ(this.start.x + DISTANCE, this.start.z);
            log("starting at " + this.start + ", goal " + this.goal);
            this.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
            return;
        }

        BetterBlockPos feet = ctx.playerFeet();
        if (this.ticksInWorld % 100 == 0) {
            log("tick " + this.ticksInWorld + " at " + feet + " " + AgentStatus.snapshot(this.cheesecake));
        }
        if (this.goal.isInGoal(feet)) {
            pass("reached " + feet + " from " + this.start + " after " + this.ticksInWorld + " ticks");
            return;
        }
        if (!this.cheesecake.getCustomGoalProcess().isActive() && this.ticksInWorld % 40 == 0) {
            // The process gives up after a failed calculation; ask again until the failure budget runs out.
            log("goal process is idle, re-issuing the goal");
            this.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
        }
        if (this.ticksInWorld > MAX_TICKS) {
            fail("did not reach " + this.goal + " within " + MAX_TICKS + " ticks; at " + feet, null);
        }
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if (this.finished) {
            return;
        }
        log("path event " + event);
        if (event == PathEvent.CALC_FAILED || event == PathEvent.NEXT_CALC_FAILED) {
            this.calcFailures++;
            if (this.calcFailures > MAX_CALC_FAILURES) {
                fail("path calculation failed " + this.calcFailures + " times", null);
            }
        }
    }

    @Override
    public void onPlayerDeath() {
        if (!this.finished) {
            fail("the player died", null);
        }
    }

    private static void configureOptions(GameOptions options) {
        options.getMaxFps().setValue(20);
        options.getViewDistance().setValue(4);
        options.getSimulationDistance().setValue(5);
        options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        options.getParticles().setValue(ParticlesMode.MINIMAL);
        options.getBiomeBlendRadius().setValue(0);
        options.pauseOnLostFocus = false;
        options.hudHidden = true;
    }

    private static void createWorld(MinecraftClient mc) {
        String name = "cheesecake-autotest-" + System.currentTimeMillis();
        log("creating world " + name + " with seed " + SEED);
        LevelInfo info = new LevelInfo(
                name,
                GameMode.SURVIVAL,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(DataConfiguration.SAFE_MODE.enabledFeatures()),
                DataConfiguration.SAFE_MODE
        );
        mc.createIntegratedServerLoader().createAndStart(
                name,
                info,
                new GeneratorOptions(SEED, true, false),
                WorldPresets::createDemoOptions,
                mc.currentScreen
        );
    }

    private void pass(String message) {
        this.finished = true;
        log("PASS: " + message);
        MinecraftClient.getInstance().scheduleStop();
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

    private static void log(String message) {
        System.out.println(TAG + " " + message);
    }
}
