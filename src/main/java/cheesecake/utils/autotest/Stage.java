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

package cheesecake.utils.autotest;

import cheesecake.agent.AgentStatus;
import cheesecake.api.Settings;
import cheesecake.api.event.events.PathEvent;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.Cheesecake;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * One scenario of the in-world test. A stage is ticked once per game tick after it has started,
 * says when it has passed, throws {@link AutoTestFailure} when a check fails, and is failed by the
 * driver when it runs past its timeout.
 */
public abstract class Stage {

    private static final int MAX_CALC_FAILURES = 8;

    protected AutoTestContext t;
    private int ticks;
    private int calcFailures;
    private final List<CompletableFuture<Void>> pending = new ArrayList<>();

    public abstract String name();

    /**
     * How long the stage may run before it is failed, in ticks.
     */
    protected abstract int timeoutTicks();

    /**
     * Called on the first tick, before {@link #tick()}.
     */
    protected void start() {
    }

    /**
     * One tick of the stage.
     *
     * @return true once the stage has passed
     */
    protected abstract boolean tick();

    public void onPathEvent(PathEvent event) {
        // Only failures of the path itself count. A failed plan for the segment after the current
        // one is routine while a process works from a partial path, and does not stop it.
        if (event == PathEvent.CALC_FAILED) {
            this.calcFailures++;
            if (this.calcFailures > MAX_CALC_FAILURES) {
                throw new AutoTestFailure(name() + ": path calculation failed " + this.calcFailures + " times");
            }
        }
    }

    public final boolean run(AutoTestContext context) {
        if (this.ticks == 0) {
            this.t = context;
            context.setStage(name());
            context.markChat();
            start();
        }
        this.ticks++;
        if (tick()) {
            return true;
        }
        if (this.ticks > timeoutTicks()) {
            throw new AutoTestFailure(name() + " did not finish within " + timeoutTicks() + " ticks; " + status());
        }
        return false;
    }

    protected int ticks() {
        return this.ticks;
    }

    protected Settings settings() {
        return Cheesecake.settings();
    }

    protected BetterBlockPos feet() {
        return this.t.ctx().playerFeet();
    }

    protected String status() {
        return "at " + feet() + " " + AgentStatus.snapshot(this.t.cheesecake);
    }

    protected void logProgress() {
        if (this.ticks % 100 == 0) {
            this.t.log("tick " + this.ticks + " " + status());
        }
    }

    protected void check(boolean condition, String message) {
        if (!condition) {
            throw new AutoTestFailure(name() + ": " + message);
        }
    }

    /**
     * Queues a console command on the integrated server. Commands run in the order they are queued.
     */
    protected void command(String command) {
        this.pending.add(this.t.serverCommand(command));
    }

    /**
     * Teleports the player within the dimension they are in. A plain {@code tp} from the console
     * would put them in the console's dimension, the Overworld.
     */
    protected void teleport(double x, double y, double z) {
        teleport(this.t.ctx().world().getRegistryKey().getValue().toString(), x, y, z);
    }

    protected void teleport(String dimension, double x, double y, double z) {
        command("execute in " + dimension + " run tp " + this.t.playerName() + " " + x + " " + y + " " + z);
    }

    /**
     * Whether every queued command has run. A command that reported an error fails the stage.
     */
    protected boolean commandsDone() {
        for (Iterator<CompletableFuture<Void>> it = this.pending.iterator(); it.hasNext(); ) {
            CompletableFuture<Void> future = it.next();
            if (!future.isDone()) {
                return false;
            }
            try {
                future.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new AutoTestFailure(name() + ": " + cause.getMessage(), cause);
            }
            it.remove();
        }
        return true;
    }

    protected static double xzDistance(BetterBlockPos a, int x, int z) {
        double dx = a.x - x;
        double dz = a.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
