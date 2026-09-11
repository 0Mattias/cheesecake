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

import cheesecake.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a patch of the platform into farmland, plants wheat on it that is ready to harvest and runs
 * {@code #farm}. The process has to find the ripe crops, break them and pick up what they drop; the
 * stage passes once none of the patch is ripe any more and the player is carrying wheat.
 */
public final class FarmStage extends Stage {

    /**
     * Where the patch sits, west of the platform's centre and south of everything the earlier
     * stages leave behind, for the reason given in {@link BuildStage}: the mine patches follow the
     * chunk's centre rather than the platform's, so they can reach eight blocks south in this frame.
     * The build box is the same distance south but on the other side of the centre.
     */
    private static final int DX = -8;
    private static final int DZ = 13;
    private static final int SIZE = 3;

    private BetterBlockPos min;
    private BetterBlockPos max;
    private boolean farming;

    @Override
    public String name() {
        return "farm";
    }

    @Override
    protected int timeoutTicks() {
        return 2400;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.min = new BetterBlockPos(p.x + DX, p.y, p.z + DZ);
        this.max = new BetterBlockPos(this.min.x + SIZE - 1, p.y, this.min.z + SIZE - 1);
        // The farmland replaces the top layer of the platform, so the crops stand at the height the
        // player walks at, as they would on the ground. Moisture keeps it from drying out and
        // turning back into dirt under the crop while the stage runs.
        command("fill " + this.min.x + " " + this.min.y + " " + this.min.z + " "
                + this.max.x + " " + this.max.y + " " + this.max.z + " minecraft:farmland[moisture=7]");
        for (int x = this.min.x; x <= this.max.x; x++) {
            for (int z = this.min.z; z <= this.max.z; z++) {
                command("setblock " + x + " " + (this.min.y + 1) + " " + z + " minecraft:wheat[age=7]");
            }
        }
        // Seeds so the process can replant what it harvests, which is the other half of what it does.
        command("give " + this.t.playerName() + " minecraft:wheat_seeds 64");
        teleport(this.min.x - 2.5, this.min.y + 1, this.min.z + 0.5);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.farming) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            check(countRipe() == SIZE * SIZE, "the crops did not grow: only " + countRipe() + " are ripe");
            check(this.t.countItems(Items.WHEAT) == 0, "already carrying wheat");
            this.t.chatCommand("farm 16");
            check(this.t.cheesecake.getFarmProcess().isActive(), "the farm process did not start");
            this.t.log("farming " + (SIZE * SIZE) + " ripe crops from " + this.min + " to " + this.max);
            this.farming = true;
            return false;
        }
        int ripe = countRipe();
        int wheat = this.t.countItems(Items.WHEAT);
        if (ripe == 0 && wheat > 0) {
            this.t.log("harvested the patch and picked up " + wheat + " wheat after " + ticks() + " ticks");
            this.t.stopEverything();
            // Put the platform back as it was, so nothing later paths over farmland or over a lump
            // where the patch used to be. Filling the two layers with obsidian and then clearing the
            // upper one again is the way round that always changes a block: a fill that would change
            // nothing, which clearing air with air does, is reported as an error.
            command("fill " + this.min.x + " " + this.min.y + " " + this.min.z + " "
                    + this.max.x + " " + (this.min.y + 1) + " " + this.max.z + " minecraft:obsidian");
            command("fill " + this.min.x + " " + (this.min.y + 1) + " " + this.min.z + " "
                    + this.max.x + " " + (this.min.y + 1) + " " + this.max.z + " minecraft:air");
            return true;
        }
        // The process says "Farm failed" both when it cannot reach a crop and when it has run out
        // of work, so what it says cannot tell the two apart. Going idle with ripe crops left is
        // the failure that matters.
        check(this.t.cheesecake.getFarmProcess().isActive(),
                "the farm process stopped with " + ripe + " ripe crops left and " + wheat + " wheat carried");
        return false;
    }

    /**
     * How many of the patch's crops are grown and waiting to be harvested.
     */
    private int countRipe() {
        int ripe = 0;
        for (int x = this.min.x; x <= this.max.x; x++) {
            for (int z = this.min.z; z <= this.max.z; z++) {
                BlockState state = this.t.mc.level.getBlockState(new BlockPos(x, this.min.y + 1, z));
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    ripe++;
                }
            }
        }
        return ripe;
    }
}
