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
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;

/**
 * Puts a cow at one end of the platform, the player at the other, and runs {@code #follow entity
 * cow}. The follow process has to keep a goal on a moving target rather than on a block, so the
 * stage passes once the player has closed the distance to within the follow radius.
 */
public final class FollowStage extends Stage {

    /**
     * Where the cow stands and where the player starts, west of the platform's centre. The walk is
     * south of everything the earlier stages leave behind: the mine patches are placed around the
     * centre of the platform's chunk, which can be eight blocks from the platform's own centre, so
     * in this frame they reach as far as eight blocks south.
     */
    private static final int COW_DX = -12;
    private static final int PLAYER_DX = -2;
    private static final int DZ = 10;
    /**
     * The follow process aims at {@code followRadius}, which is three by default, and measures it
     * from the block the cow stands in while this measures it from the cow itself, so the player
     * can be most of a block further away than that and still be where the pathfinder wanted it.
     * The player starts ten blocks off, so this is still only reached by walking most of the way.
     */
    private static final double ARRIVED = 5.0D;

    /**
     * How far from where it was summoned the cow may be for the stage to accept it as its own.
     */
    private static final double MINE = 2.0D;

    private BetterBlockPos cowPos;
    private Entity cow;
    private boolean following;

    @Override
    public String name() {
        return "follow";
    }

    @Override
    protected int timeoutTicks() {
        return 1200;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.cowPos = new BetterBlockPos(p.x + COW_DX, p.y + 1, p.z + DZ);
        // NoAI keeps the cow where it is put, so the stage measures the pathing rather than a race
        // against a wandering animal, and PersistenceRequired keeps it from despawning.
        command("summon minecraft:cow " + this.cowPos.x + " " + this.cowPos.y + " " + this.cowPos.z
                + " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
        teleport(p.x + PLAYER_DX + 0.5, p.y + 1, p.z + DZ + 0.5);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.following) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            // The world generates its own cows near the spawn, and the command follows every cow
            // there is, so the stage has to hold on to the one it summoned rather than take the
            // first one the client happens to list.
            this.cow = nearestCow();
            check(this.cow != null, "the cow did not arrive on the client");
            this.t.chatCommand("follow entity minecraft:cow");
            check(this.t.saidSinceMark("Following these types of entities"),
                    "the command did not report what it was following");
            check(this.t.cheesecake.getFollowProcess().isActive(), "the follow process did not start");
            this.t.log("following from " + feet() + " towards the cow at " + this.cowPos);
            this.following = true;
            return false;
        }
        check(this.cow.isAlive(), "the cow disappeared");
        List<Entity> following = this.t.cheesecake.getFollowProcess().following();
        check(following != null && following.contains(this.cow), "the process is not following the cow: " + following);
        double distance = Math.sqrt(this.t.player().distanceToSqr(this.cow));
        if (distance <= ARRIVED) {
            this.t.log("reached the cow at " + distance + " blocks after " + ticks() + " ticks");
            this.t.stopEverything();
            this.t.cheesecake.getFollowProcess().cancel();
            check(!this.t.cheesecake.getFollowProcess().isActive(), "the follow process kept going after cancel");
            command("kill @e[type=minecraft:cow,x=" + this.cowPos.x + ",y=" + this.cowPos.y
                    + ",z=" + this.cowPos.z + ",distance=.." + (int) MINE + "]");
            return true;
        }
        check(this.t.cheesecake.getFollowProcess().isActive(),
                "the follow process stopped " + distance + " blocks from the cow");
        return false;
    }

    /**
     * The cow standing where this stage summoned one, or null if none is.
     */
    private Entity nearestCow() {
        Entity nearest = null;
        double nearestDistance = MINE * MINE;
        for (Entity entity : this.t.ctx().entitiesStream().toList()) {
            if (entity.getType() != EntityTypes.COW) {
                continue;
            }
            double distance = entity.distanceToSqr(this.cowPos.x + 0.5, this.cowPos.y, this.cowPos.z + 0.5);
            if (distance <= nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
