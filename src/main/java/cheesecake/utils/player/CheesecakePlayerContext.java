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

package cheesecake.utils.player;

import cheesecake.Cheesecake;
import cheesecake.api.cache.IWorldData;
import cheesecake.api.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Implementation of {@link IPlayerContext} that provides information about the primary player.
 *
 * @author Brady
 * @since 11/12/2018
 */
public final class CheesecakePlayerContext implements IPlayerContext {

    private final Cheesecake cheesecake;
    private final MinecraftClient mc;
    private final IPlayerController playerController;

    public CheesecakePlayerContext(Cheesecake cheesecake, MinecraftClient mc) {
        this.cheesecake = cheesecake;
        this.mc = mc;
        this.playerController = new CheesecakePlayerController(mc);
    }

    @Override
    public MinecraftClient minecraft() {
        return this.mc;
    }

    @Override
    public ClientPlayerEntity player() {
        return this.mc.player;
    }

    @Override
    public IPlayerController playerController() {
        return this.playerController;
    }

    @Override
    public World world() {
        return this.mc.world;
    }

    @Override
    public IWorldData worldData() {
        return this.cheesecake.getWorldProvider().getCurrentWorld();
    }

    @Override
    public BetterBlockPos viewerPos() {
        final Entity entity = this.mc.getCameraEntity();
        return entity == null ? this.playerFeet() : BetterBlockPos.from(entity.getBlockPos());
    }

    @Override
    public Rotation playerRotations() {
        return this.cheesecake.getLookBehavior().getEffectiveRotation().orElseGet(IPlayerContext.super::playerRotations);
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(player(), playerRotations(), playerController().getBlockReachDistance());
    }
}
