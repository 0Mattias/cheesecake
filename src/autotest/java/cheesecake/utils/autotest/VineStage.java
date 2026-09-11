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

import java.util.Locale;

/**
 * Builds a column of climbable blocks on the platform and asks for a goal at its top, which can
 * only be reached by climbing: the player carries nothing to pillar with.
 */
public final class VineStage extends Stage {

    public enum Kind {
        /**
         * Overworld vines that hang free: only the top one touches a block, the rest are held by
         * the vine above them and have air on every side.
         */
        VINE(-5),
        /**
         * Twisting vines, which grow upwards from the platform and end in a tip.
         */
        TWISTING(0),
        /**
         * Weeping vines, which hang from a block and end in a tip at the bottom.
         */
        WEEPING(5);

        /**
         * Where the column stands, east of the platform's centre.
         */
        final int dx;

        Kind(int dx) {
            this.dx = dx;
        }
    }

    private static final int HEIGHT = 7;
    /**
     * The columns stand this far north of the platform's centre, and the player starts three blocks
     * south of them.
     */
    private static final int DZ = -4;

    private final Kind kind;
    private BetterBlockPos bottom;
    private BetterBlockPos top;
    /**
     * The highest block a player can stand in. Weeping vines hang from a block, so the top of the
     * column is where the head would be, and a goal there would have the pathfinder mine the anchor
     * and drop the whole column.
     */
    private BetterBlockPos summit;
    private GoalBlock goal;

    public VineStage(Kind kind) {
        this.kind = kind;
    }

    @Override
    public String name() {
        return "vines-" + this.kind.name().toLowerCase(Locale.ROOT);
    }

    @Override
    protected int timeoutTicks() {
        return 1200;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        int x = p.x + this.kind.dx;
        int z = p.z + DZ;
        this.bottom = new BetterBlockPos(x, p.y + 1, z);
        this.top = new BetterBlockPos(x, p.y + HEIGHT, z);
        this.summit = this.kind == Kind.WEEPING ? this.top.below() : this.top;
        switch (this.kind) {
            case VINE:
                // The anchor sits north of the top vine only; everything below hangs free. Anchors
                // are obsidian so that the later #mine stone run does not go for them.
                setBlock(x, this.top.y, z - 1, "minecraft:obsidian");
                for (int y = this.top.y; y >= this.bottom.y; y--) {
                    setBlock(x, y, z, "minecraft:vine[north=true]");
                }
                break;
            case TWISTING:
                for (int y = this.bottom.y; y < this.top.y; y++) {
                    setBlock(x, y, z, "minecraft:twisting_vines_plant");
                }
                setBlock(x, this.top.y, z, "minecraft:twisting_vines");
                break;
            case WEEPING:
                setBlock(x, this.top.y + 1, z, "minecraft:obsidian");
                for (int y = this.top.y; y > this.bottom.y; y--) {
                    setBlock(x, y, z, "minecraft:weeping_vines_plant");
                }
                setBlock(x, this.bottom.y, z, "minecraft:weeping_vines");
                break;
        }
        teleport(x + 0.5, this.bottom.y, z + 3.5);
    }

    private void setBlock(int x, int y, int z, String block) {
        command("setblock " + x + " " + y + " " + z + " " + block);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (this.goal == null) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            this.goal = new GoalBlock(this.summit.x, this.summit.y, this.summit.z);
            this.t.log("climbing from " + feet() + " to " + this.goal);
            this.t.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
            return false;
        }
        BetterBlockPos feet = feet();
        if (feet.equals(this.summit)) {
            check(!this.t.saidSinceMark("Unable to climb"), "the mod said it was unable to climb");
            this.t.log("reached the top of the " + this.kind.name().toLowerCase(Locale.ROOT) + " column at " + feet + " after " + ticks() + " ticks");
            return true;
        }
        if (!this.t.cheesecake.getCustomGoalProcess().isActive() && ticks() % 40 == 0) {
            this.t.log("goal process is idle, re-issuing the goal");
            this.t.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
        }
        return false;
    }
}
