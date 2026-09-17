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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Buries a pickaxe in the main inventory, fills the hotbar so there is nowhere for it to go without
 * a swap, and sends the player on a walk with {@code allowInventory} and
 * {@code inventoryMoveOnlyIfStationary} on. The inventory behaviour wants that pickaxe on the
 * hotbar every tick, and with those two settings the only thing that lets it move is the inventory
 * pauser reporting that the player has come to a halt: the swap happening at all is what this
 * checks, and the walk finishing afterwards is what says the pause was given back.
 */
public final class InventoryPauseStage extends Stage {

    private static final int HOTBAR = 9;
    /**
     * Nine different items, because a give of the same item twice stacks into the one slot instead
     * of taking the next. They are not tools and not throwaway blocks, so the behaviour has no
     * reason to move any of them.
     */
    private static final String[] FILLER = {
            "white_wool", "orange_wool", "magenta_wool", "light_blue_wool",
            "yellow_wool", "lime_wool", "pink_wool", "gray_wool", "light_gray_wool"
    };
    private static final int START_DX = -10;
    private static final int GOAL_DX = 10;
    private static final int DZ = 12;

    private GoalBlock goal;
    private boolean walking;
    private boolean swapped;
    private boolean previousAllowInventory;
    private boolean previousOnlyIfStationary;

    @Override
    public String name() {
        return "inventory-pause";
    }

    @Override
    protected int timeoutTicks() {
        return 2400;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        // A clear of an empty inventory is reported as a failure, and the inventory is empty when this
        // stage runs alone, so give it something to clear.
        command("give " + this.t.playerName() + " minecraft:stick 1");
        command("clear " + this.t.playerName());
        for (String filler : FILLER) {
            command("give " + this.t.playerName() + " minecraft:" + filler + " 1");
        }
        // The hotbar is full now, so this lands in the main inventory, which is the whole point.
        command("give " + this.t.playerName() + " minecraft:diamond_pickaxe 1");
        teleport(p.x + START_DX + 0.5, p.y + 1, p.z + DZ + 0.5);
        this.goal = new GoalBlock(p.x + GOAL_DX, p.y + 1, p.z + DZ);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.walking) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            int slot = pickaxeSlot();
            check(slot >= HOTBAR, "the pickaxe went to the hotbar slot " + slot + " instead of the main inventory");
            this.previousAllowInventory = settings().allowInventory.value;
            this.previousOnlyIfStationary = settings().inventoryMoveOnlyIfStationary.value;
            settings().allowInventory.value = true;
            settings().inventoryMoveOnlyIfStationary.value = true;
            this.t.log("walking from " + feet() + " to " + this.goal + " with the pickaxe in slot " + slot);
            this.t.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
            this.walking = true;
            return false;
        }
        if (!this.swapped) {
            int slot = pickaxeSlot();
            check(slot >= 0, "the pickaxe left the inventory");
            if (slot < HOTBAR) {
                this.swapped = true;
                this.t.log("the pickaxe reached hotbar slot " + slot + " after " + ticks()
                        + " ticks, which only happens once the pauser reports the player stationary");
            }
            return false;
        }
        if (this.goal.isInGoal(feet())) {
            this.t.log("finished the walk after " + ticks() + " ticks, so pathing resumed after the pause");
            finish();
            return true;
        }
        check(this.t.cheesecake.getCustomGoalProcess().isActive(),
                "the goal process stopped before reaching the goal, at " + feet());
        return false;
    }

    /**
     * Where the pickaxe is, counting the hotbar as slots 0 to 8, or -1 if it is gone.
     */
    private int pickaxeSlot() {
        for (int i = 0; i < this.t.player().getInventory().getNonEquipmentItems().size(); i++) {
            ItemStack stack = this.t.player().getInventory().getNonEquipmentItems().get(i);
            if (stack.is(Items.DIAMOND_PICKAXE)) {
                return i;
            }
        }
        return -1;
    }

    private void finish() {
        settings().allowInventory.value = this.previousAllowInventory;
        settings().inventoryMoveOnlyIfStationary.value = this.previousOnlyIfStationary;
        this.t.stopEverything();
        command("clear " + this.t.playerName());
    }
}
