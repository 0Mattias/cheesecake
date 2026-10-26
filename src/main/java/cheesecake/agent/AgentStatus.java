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

package cheesecake.agent;

import cheesecake.Cheesecake;
import cheesecake.api.ICheesecake;
import cheesecake.api.behavior.IPathingBehavior;
import cheesecake.api.pathing.goals.Goal;
import cheesecake.api.pathing.path.IPathExecutor;
import cheesecake.api.process.ICheesecakeProcess;
import cheesecake.api.process.IElytraProcess;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.api.utils.IPlayerContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Builds the JSON status snapshot used by {@code #status} and the agent API. Must be called on the
 * game thread.
 */
public final class AgentStatus {

    private AgentStatus() {}

    public static JsonObject snapshot(ICheesecake cheesecake) {
        JsonObject status = new JsonObject();
        status.addProperty("version", version());

        IPlayerContext ctx = cheesecake.getPlayerContext();
        LocalPlayer player = ctx.player();
        Level world = ctx.world();
        boolean inWorld = player != null && world != null;
        status.addProperty("inWorld", inWorld);
        if (inWorld) {
            status.addProperty("dimension", world.dimension().identifier().toString());
            status.add("position", vec(player.getX(), player.getY(), player.getZ()));
            BetterBlockPos feet = ctx.playerFeet();
            status.add("feet", pos(feet));
            status.addProperty("yaw", round(player.getYRot()));
            status.addProperty("pitch", round(player.getXRot()));
            status.addProperty("health", round(player.getHealth()));
            status.addProperty("food", player.getFoodData().getFoodLevel());
            status.addProperty("onGround", player.onGround());
            status.addProperty("gliding", player.isFallFlying());
            status.addProperty("inWater", player.isInWater());
            status.addProperty("inLava", player.isInLava());
            status.add("mainHand", stack(player.getMainHandItem()));
            int empty = 0;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.isEmpty()) {
                    empty++;
                }
            }
            status.addProperty("emptyInventorySlots", empty);
        }

        ICheesecakeProcess process = cheesecake.getPathingControlManager().mostRecentInControl().orElse(null);
        if (process != null && process.isActive()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", process.getClass().getSimpleName());
            p.addProperty("displayName", process.displayName());
            p.addProperty("temporary", process.isTemporary());
            status.add("process", p);
        } else {
            status.add("process", JsonNull.INSTANCE);
        }

        IPathingBehavior pathing = cheesecake.getPathingBehavior();
        JsonObject path = new JsonObject();
        path.addProperty("active", pathing.isPathing());
        path.addProperty("calculating", pathing.getInProgress().isPresent());
        Goal goal = pathing.getGoal();
        path.addProperty("goal", goal == null ? null : goal.toString());
        IPathExecutor executor = pathing.getCurrent();
        if (executor != null) {
            path.addProperty("segmentPosition", executor.getPosition());
            path.addProperty("segmentLength", executor.getPath().length());
        }
        // Both estimates start from the player's position, which there is none of outside a world.
        path.add("ticksRemainingInSegment", inWorld ? number(pathing.ticksRemainingInSegment()) : JsonNull.INSTANCE);
        path.add("estimatedTicksToGoal", inWorld ? number(pathing.estimatedTicksToGoal()) : JsonNull.INSTANCE);
        status.add("pathing", path);

        IElytraProcess elytra = cheesecake.getElytraProcess();
        JsonObject e = new JsonObject();
        e.addProperty("active", elytra.isActive());
        BlockPos destination = elytra.currentDestination();
        e.add("destination", destination == null ? JsonNull.INSTANCE : pos(destination));
        // Zero while the path is still being computed. The process neither steers nor fires a
        // rocket until this is not, which is invisible from "active" and "destination" alone.
        e.addProperty("pathNodes", elytra.getPath().size());
        status.add("elytra", e);

        return status;
    }

    private static String version() {
        String version = Cheesecake.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    private static JsonObject vec(double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", round(x));
        o.addProperty("y", round(y));
        o.addProperty("z", round(z));
        return o;
    }

    private static JsonObject pos(BlockPos pos) {
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        return o;
    }

    private static JsonElement stack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        o.addProperty("count", stack.getCount());
        return o;
    }

    private static JsonElement number(Optional<Double> value) {
        if (!value.isPresent() || value.get().isNaN() || value.get().isInfinite()) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(round(value.get()));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
