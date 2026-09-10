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
import cheesecake.api.utils.BetterBlockPos;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

/**
 * Places a patch of a block on the platform, hands the player a stone pickaxe and runs
 * {@code #mine <count> <block>}. The process has to count what the block drops, which comes from
 * the loot tables, and stop by itself once the inventory holds that many.
 */
public final class MineStage extends Stage {

    private final String block;
    private final int count;
    private final Item drop;
    private final int dx;
    private BetterBlockPos stand;
    private boolean repacked;
    private boolean mining;
    private int miningTick;

    /**
     * @param block the block to mine, by its id without the namespace
     * @param count how many drops to ask for; the patch holds eighteen blocks
     * @param drop  the item the block drops without silk touch
     * @param dx    where the patch starts, east of the middle of the platform centre's chunk. The
     *              patch and the player stay inside that chunk: the mine process looks tracked ores
     *              up in the chunk cache, and a freshly placed patch is only there once its chunk
     *              has been packed again.
     */
    public MineStage(String block, int count, Item drop, int dx) {
        this.block = block;
        this.count = count;
        this.drop = drop;
        this.dx = dx;
    }

    @Override
    public String name() {
        return "mine-" + this.block;
    }

    @Override
    protected int timeoutTicks() {
        return 2400;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.stand = new BetterBlockPos((p.x >> 4 << 4) + 8, p.y + 1, (p.z >> 4 << 4) + 8);
        int x = this.stand.x + this.dx;
        command("fill " + x + " " + (p.y + 1) + " " + (this.stand.z - 1) + " " + (x + 2) + " " + (p.y + 2) + " " + (this.stand.z + 1) + " minecraft:" + this.block);
        if (this.t.countItems(Items.STONE_PICKAXE) == 0) {
            command("give " + this.t.playerName() + " minecraft:stone_pickaxe");
        }
        teleport(this.stand.x + 0.5, this.stand.y, this.stand.z + 0.5);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.mining) {
            if (!commandsDone()) {
                return false;
            }
            if (!this.repacked) {
                // The patch went in by command, after the chunk was cached. Pack it again now
                // rather than relying on the block update having done so.
                this.t.cheesecake.getWorldProvider().getCurrentWorld().getCachedWorld()
                        .queueForPacking(this.t.mc.world.getChunk(this.stand.x >> 4, this.stand.z >> 4));
                this.repacked = true;
                this.miningTick = ticks();
                return false;
            }
            if (ticks() - this.miningTick < 20 || !this.t.player().isOnGround()) {
                return false;
            }
            check(this.t.countItems(Items.STONE_PICKAXE) > 0, "the pickaxe did not arrive");
            check(this.t.countItems(this.drop) == 0, "already carrying " + Registries.ITEM.getId(this.drop));
            this.t.chatCommand("mine " + this.count + " " + this.block);
            check(this.t.cheesecake.getMineProcess().isActive(), "the mine process did not start");
            this.mining = true;
            this.miningTick = ticks();
            return false;
        }
        if (ticks() - this.miningTick == 10) {
            String goal = String.valueOf(AgentStatus.snapshot(this.t.cheesecake).getAsJsonObject("pathing").get("goal"));
            this.t.log("mining towards " + (goal.length() > 300 ? goal.substring(0, 300) + "..." : goal));
        }
        int have = this.t.countItems(this.drop);
        if (have >= this.count && !this.t.cheesecake.getMineProcess().isActive()) {
            check(this.t.saidSinceMark("Have " + have + " valid items"), "the process stopped without reporting the count");
            this.t.log("mined " + have + " " + Registries.ITEM.getId(this.drop) + " after " + ticks() + " ticks and the process stopped by itself");
            return true;
        }
        check(this.t.cheesecake.getMineProcess().isActive() || have >= this.count,
                "the mine process stopped with " + have + " " + Registries.ITEM.getId(this.drop));
        return false;
    }
}
