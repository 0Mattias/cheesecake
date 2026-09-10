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
 * Builds an obsidian platform high above the terrain and puts the player on it. The stages after
 * this one build what they need on the platform, so they do not depend on the terrain the seed
 * happens to put under the player.
 */
public final class PlatformStage extends Stage {

    /**
     * Above any terrain the overworld generates, below the build limit with room for what the
     * stages build on top.
     */
    public static final int PLATFORM_Y = 280;
    /**
     * Wide enough to cover the whole chunk the centre is in, wherever in that chunk the centre
     * falls, so the mining stages can build inside that chunk.
     */
    public static final int RADIUS = 16;

    private BetterBlockPos center;

    @Override
    public String name() {
        return "platform";
    }

    @Override
    protected int timeoutTicks() {
        return 600;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos feet = feet();
        this.center = new BetterBlockPos(feet.x, PLATFORM_Y, feet.z);
        this.t.platform = this.center;
        this.t.log("building the platform around " + this.center);
        command("fill " + (this.center.x - RADIUS) + " " + PLATFORM_Y + " " + (this.center.z - RADIUS) + " "
                + (this.center.x + RADIUS) + " " + PLATFORM_Y + " " + (this.center.z + RADIUS) + " minecraft:obsidian");
        teleport(this.center.x + 0.5, PLATFORM_Y + 1, this.center.z + 0.5);
    }

    @Override
    protected boolean tick() {
        if (!commandsDone() || ticks() < 20) {
            return false;
        }
        BetterBlockPos feet = feet();
        if (feet.y != PLATFORM_Y + 1 || !this.t.player().onGround()) {
            return false;
        }
        check(this.t.mc.level.getBlockState(new BlockPos(feet.x, PLATFORM_Y, feet.z)).is(Blocks.OBSIDIAN),
                "standing on " + this.t.mc.level.getBlockState(new BlockPos(feet.x, PLATFORM_Y, feet.z)) + " instead of the platform");
        this.t.log("standing on the platform at " + feet);
        return true;
    }
}
