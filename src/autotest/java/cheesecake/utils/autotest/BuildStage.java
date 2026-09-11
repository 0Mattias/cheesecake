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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Selects an empty box on the platform, hands the player the material and fills the box with
 * {@code #sel set}. The builder has to work out what to place, walk to each spot and place it, and
 * stop by itself once the box matches; the stage then reads every block back out of the world.
 */
public final class BuildStage extends Stage {

    /**
     * Sandstone is a plain full block with no block state to get wrong, and no other stage places
     * or mines it.
     */
    private static final String MATERIAL = "sandstone";
    /**
     * Where the box sits, relative to the platform's centre. The mine patches are placed around the
     * centre of the platform's chunk rather than the platform's own centre, and the two can be eight
     * blocks apart, so in this frame those patches can be anywhere up to eight blocks south. Staying
     * past that, and well south of the vine columns, is what keeps the stages off each other.
     */
    private static final int DX = 6;
    private static final int DZ = 13;
    private static final int WIDTH = 3;
    private static final int HEIGHT = 2;

    private BetterBlockPos min;
    private BetterBlockPos max;
    private boolean building;

    @Override
    public String name() {
        return "build";
    }

    @Override
    protected int timeoutTicks() {
        return 3600;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.min = new BetterBlockPos(p.x + DX, p.y + 1, p.z + DZ);
        this.max = new BetterBlockPos(this.min.x + WIDTH - 1, this.min.y + HEIGHT - 1, this.min.z + WIDTH - 1);
        command("give " + this.t.playerName() + " minecraft:" + MATERIAL + " 64");
        teleport(this.min.x - 2.5, this.min.y, this.min.z + 0.5);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.building) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            check(this.t.countItems(Items.SANDSTONE) >= blockCount(), "the material did not arrive");
            // A leftover from an earlier stage would have the builder mine instead of place, which
            // is not what this stage is checking. Nothing else builds in this corner.
            check(countMatching(Blocks.AIR) == blockCount(), "the box was not empty before building");
            this.t.chatCommand("sel pos1 " + this.min.x + " " + this.min.y + " " + this.min.z);
            this.t.chatCommand("sel pos2 " + this.max.x + " " + this.max.y + " " + this.max.z);
            this.t.chatCommand("sel set minecraft:" + MATERIAL);
            check(this.t.cheesecake.getBuilderProcess().isActive(), "the builder did not start");
            this.t.log("building " + blockCount() + " blocks from " + this.min + " to " + this.max);
            this.building = true;
            return false;
        }
        if (this.t.cheesecake.getBuilderProcess().isActive()) {
            check(!this.t.saidSinceMark("Missing materials"), "the builder ran out of material");
            check(!this.t.saidSinceMark("Unable to do it"), "the builder gave up: " + status());
            return false;
        }
        check(this.t.saidSinceMark("Done building"), "the builder stopped without reporting that it was done");
        int placed = countMatching(Blocks.SANDSTONE);
        check(placed == blockCount(), "the builder reported done with " + placed + " of " + blockCount() + " blocks placed");
        this.t.log("built " + placed + " blocks after " + ticks() + " ticks and the process stopped by itself");
        // A selection outlives the build and is drawn for the rest of the run, so drop it.
        this.t.chatCommand("sel clear");
        // Leave the platform as it was found, so a later stage does not path over this.
        command("fill " + this.min.x + " " + this.min.y + " " + this.min.z + " "
                + this.max.x + " " + this.max.y + " " + this.max.z + " minecraft:air");
        return true;
    }

    private int blockCount() {
        return WIDTH * WIDTH * HEIGHT;
    }

    /**
     * How many blocks of the box are the given block.
     */
    private int countMatching(Block block) {
        int matching = 0;
        for (int x = this.min.x; x <= this.max.x; x++) {
            for (int y = this.min.y; y <= this.max.y; y++) {
                for (int z = this.min.z; z <= this.max.z; z++) {
                    BlockState state = this.t.mc.level.getBlockState(new BlockPos(x, y, z));
                    if (state.is(block)) {
                        matching++;
                    }
                }
            }
        }
        return matching;
    }
}
