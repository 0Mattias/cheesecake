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
import net.minecraft.world.level.block.Blocks;

/**
 * Puts one block of a kind that appears nowhere else at the far end of the platform and runs
 * {@code #goto <block>}, which is the get-to-block process rather than a goal: it has to find the
 * block itself, path to it and stop on arrival.
 */
public final class GetToBlockStage extends Stage {

    /**
     * A bookshelf is a plain full block that no other stage places, and it is not one of the blocks
     * the chunk cache keeps track of, so the process finds it by scanning the loaded world and the
     * stage does not have to repack a chunk the way the mining stages do. It is also not a
     * container, so the process does not try to open it on arrival.
     */
    private static final String BLOCK = "bookshelf";
    private static final int TARGET_DX = 10;
    private static final int START_DX = -10;
    private static final int DZ = 12;
    /**
     * The process aims to stand next to the block, so this is arrival with a block of slack.
     */
    private static final double ARRIVED = 3.0D;

    private BetterBlockPos target;
    private boolean going;

    @Override
    public String name() {
        return "goto-block";
    }

    @Override
    protected int timeoutTicks() {
        return 2400;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.target = new BetterBlockPos(p.x + TARGET_DX, p.y + 1, p.z + DZ);
        command("setblock " + this.target.x + " " + this.target.y + " " + this.target.z + " minecraft:" + BLOCK);
        teleport(p.x + START_DX + 0.5, p.y + 1, p.z + DZ + 0.5);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.going) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            check(this.t.mc.level.getBlockState(new BlockPos(this.target.x, this.target.y, this.target.z)).is(Blocks.BOOKSHELF),
                    "the " + BLOCK + " is not there");
            this.t.log("looking for the " + BLOCK + " at " + this.target + " from " + feet());
            this.t.chatCommand("goto " + BLOCK);
            check(this.t.cheesecake.getGetToBlockProcess().isActive(), "the get-to-block process did not start");
            this.going = true;
            return false;
        }
        // Both ways the process gives up say so in chat, and both leave it inactive, which on its
        // own would look exactly like arriving.
        check(!this.t.saidSinceMark("canceling GetToBlock"), "the process gave up: " + status());
        double distance = Math.sqrt(this.t.player().distanceToSqr(
                this.target.x + 0.5, this.target.y, this.target.z + 0.5));
        if (!this.t.cheesecake.getGetToBlockProcess().isActive()) {
            check(distance <= ARRIVED,
                    "the process stopped " + distance + " blocks from the " + BLOCK);
            this.t.log("stood next to the " + BLOCK + " at " + distance + " blocks after " + ticks()
                    + " ticks and the process stopped by itself");
            command("setblock " + this.target.x + " " + this.target.y + " " + this.target.z + " minecraft:air");
            return true;
        }
        return false;
    }
}
