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

package cheesecake.process;

import cheesecake.Cheesecake;
import cheesecake.api.pathing.goals.Goal;
import cheesecake.api.pathing.goals.GoalBlock;
import cheesecake.api.pathing.goals.GoalComposite;
import cheesecake.api.pathing.goals.GoalNear;
import cheesecake.api.pathing.goals.GoalXZ;
import cheesecake.api.process.IFollowProcess;
import cheesecake.api.process.PathingCommand;
import cheesecake.api.process.PathingCommandType;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.utils.CheesecakeProcessHelper;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Follow an entity
 *
 * @author leijurv
 */
public final class FollowProcess extends CheesecakeProcessHelper implements IFollowProcess {

    private Predicate<Entity> filter;
    private List<Entity> cache;
    private boolean into; // walk straight into the target, regardless of settings

    public FollowProcess(Cheesecake cheesecake) {
        super(cheesecake);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        scanWorld();
        Goal goal = new GoalComposite(cache.stream().map(this::towards).toArray(Goal[]::new));
        return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    private Goal towards(Entity following) {
        BlockPos pos;
        if (Cheesecake.settings().followOffsetDistance.value == 0 || into) {
            pos = following.getBlockPos();
        } else {
            GoalXZ g = GoalXZ.fromDirection(
                    new net.minecraft.util.math.Vec3d(following.getX(), following.getY(), following.getZ()),
                    Cheesecake.settings().followOffsetDirection.value,
                    Cheesecake.settings().followOffsetDistance.value);
            pos = new BetterBlockPos(g.getX(), following.getY(), g.getZ());
        }
        if (into) {
            return new GoalBlock(pos);
        }
        return new GoalNear(pos, Cheesecake.settings().followRadius.value);
    }

    private boolean followable(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!entity.isAlive()) {
            return false;
        }
        if (entity.equals(ctx.player())) {
            return false;
        }
        int maxDist = Cheesecake.settings().followTargetMaxDistance.value;
        if (maxDist != 0 && entity.squaredDistanceTo(ctx.player()) > maxDist * maxDist) {
            return false;
        }
        return ctx.entitiesStream().anyMatch(entity::equals);
    }

    private void scanWorld() {
        cache = ctx.entitiesStream()
                .filter(this::followable)
                .filter(this.filter)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public boolean isActive() {
        if (filter == null) {
            return false;
        }
        scanWorld();
        return !cache.isEmpty();
    }

    @Override
    public void onLostControl() {
        filter = null;
        cache = null;
    }

    @Override
    public String displayName0() {
        return "Following " + cache;
    }

    @Override
    public void follow(Predicate<Entity> filter) {
        this.filter = filter;
        this.into = false;
    }

    @Override
    public void pickup(Predicate<ItemStack> filter) {
        this.filter = e -> e instanceof ItemEntity && filter.test(((ItemEntity) e).getStack());
        this.into = true;
    }

    @Override
    public List<Entity> following() {
        return cache;
    }

    @Override
    public Predicate<Entity> currentFilter() {
        return filter;
    }
}
