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

import cheesecake.api.pathing.goals.GoalBlock;
import cheesecake.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Walls a tunnel across the platform with a plug of dirt in the middle and sends the player to the
 * far end with {@code backfill} on. The only way through is to mine the plug, and the backfill
 * process has to notice what was broken while pathing and put it back afterwards.
 */
public final class BackfillStage extends Stage {

    /**
     * The tunnel runs east to west across the platform, with the plug at the middle.
     */
    private static final int HALF_LENGTH = 6;
    private static final int DZ = 11;
    /**
     * How long to give the process to fill the plug back in once the player has arrived. Placing a
     * block means looking at it first, and the process only works while the path is safe to cancel.
     */
    private static final int FILL_TICKS = 600;

    private BetterBlockPos plug;
    private GoalBlock goal;
    private boolean walking;
    private boolean broke;
    private int arrivedTick;
    private boolean previousBackfill;

    @Override
    public String name() {
        return "backfill";
    }

    @Override
    protected int timeoutTicks() {
        return 3600;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.plug = new BetterBlockPos(p.x, p.y + 1, p.z + DZ);
        int west = p.x - HALF_LENGTH - 1;
        int east = p.x + HALF_LENGTH + 1;
        // Walls either side and a roof, so the bot can neither walk around the plug nor climb over
        // it. Obsidian because it is slow enough to mine that the plug is always the cheaper way.
        command("fill " + west + " " + (p.y + 1) + " " + (p.z + DZ - 1) + " "
                + east + " " + (p.y + 2) + " " + (p.z + DZ - 1) + " minecraft:obsidian");
        command("fill " + west + " " + (p.y + 1) + " " + (p.z + DZ + 1) + " "
                + east + " " + (p.y + 2) + " " + (p.z + DZ + 1) + " minecraft:obsidian");
        command("fill " + west + " " + (p.y + 3) + " " + (p.z + DZ - 1) + " "
                + east + " " + (p.y + 3) + " " + (p.z + DZ + 1) + " minecraft:obsidian");
        command("fill " + this.plug.x + " " + this.plug.y + " " + this.plug.z + " "
                + this.plug.x + " " + (this.plug.y + 1) + " " + this.plug.z + " minecraft:dirt");
        command("give " + this.t.playerName() + " minecraft:dirt 64");
        teleport(p.x - HALF_LENGTH + 0.5, p.y + 1, p.z + DZ + 0.5);
        this.previousBackfill = settings().backfill.value;
        settings().backfill.value = true;
        // Backfill refuses to run with parkour on and turns itself off if it finds it on.
        check(!settings().allowParkour.value, "allowParkour is on, which backfill refuses to run with");
        this.goal = new GoalBlock(p.x + HALF_LENGTH, p.y + 1, p.z + DZ);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.walking) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            check(plugBlocks() == 2, "the plug is not there");
            this.t.log("walking through the plug at " + this.plug + " to " + this.goal);
            this.t.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
            this.walking = true;
            return false;
        }
        if (!this.broke) {
            if (plugBlocks() == 0) {
                this.broke = true;
                this.t.log("the plug was mined after " + ticks() + " ticks");
            }
            return false;
        }
        if (this.arrivedTick == 0) {
            if (!this.goal.isInGoal(feet())) {
                return false;
            }
            this.arrivedTick = ticks();
            this.t.log("reached the far end after " + ticks() + " ticks; waiting for the backfill");
            return false;
        }
        int filled = plugBlocks();
        if (filled == 2) {
            this.t.log("the plug was filled back in " + (ticks() - this.arrivedTick) + " ticks after arriving");
            finish();
            return true;
        }
        check(ticks() - this.arrivedTick < FILL_TICKS,
                "the backfill left " + (2 - filled) + " of the 2 broken blocks open");
        return false;
    }

    /**
     * How many of the two blocks the plug filled are solid again. The process places dirt, and the
     * plug was dirt, so this counts either.
     */
    private int plugBlocks() {
        int solid = 0;
        for (int y = this.plug.y; y <= this.plug.y + 1; y++) {
            if (!this.t.mc.level.getBlockState(new BlockPos(this.plug.x, y, this.plug.z)).is(Blocks.AIR)) {
                solid++;
            }
        }
        return solid;
    }

    private void finish() {
        settings().backfill.value = this.previousBackfill;
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        int west = p.x - HALF_LENGTH - 1;
        int east = p.x + HALF_LENGTH + 1;
        // Fill the whole tunnel solid and then clear it, so both fills always change a block: a
        // fill that would change nothing is reported as an error.
        command("fill " + west + " " + (p.y + 1) + " " + (p.z + DZ - 1) + " "
                + east + " " + (p.y + 3) + " " + (p.z + DZ + 1) + " minecraft:obsidian");
        command("fill " + west + " " + (p.y + 1) + " " + (p.z + DZ - 1) + " "
                + east + " " + (p.y + 3) + " " + (p.z + DZ + 1) + " minecraft:air");
    }
}
