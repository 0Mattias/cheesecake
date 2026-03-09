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

package cheesecake.utils.pathing;

import cheesecake.Cheesecake;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.api.utils.IPlayerContext;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.util.math.BlockPos;

@SuppressWarnings({"deprecation"})
public class Avoidance {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final double coefficient;
    private final int radius;
    private final int radiusSq;

    public Avoidance(BlockPos center, double coefficient, int radius) {
        this(center.getX(), center.getY(), center.getZ(), coefficient, radius);
    }

    public Avoidance(int centerX, int centerY, int centerZ, double coefficient, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.coefficient = coefficient;
        this.radius = radius;
        this.radiusSq = radius * radius;
    }

    public double coefficient(int x, int y, int z) {
        int xDiff = x - centerX;
        int yDiff = y - centerY;
        int zDiff = z - centerZ;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= radiusSq ? coefficient : 1.0D;
    }

    public static List<Avoidance> create(IPlayerContext ctx) {
        if (!Cheesecake.settings().avoidance.value) {
            return Collections.emptyList();
        }
        List<Avoidance> res = new ArrayList<>();
        double mobSpawnerCoeff = Cheesecake.settings().mobSpawnerAvoidanceCoefficient.value;
        double mobCoeff = Cheesecake.settings().mobAvoidanceCoefficient.value;
        if (mobSpawnerCoeff != 1.0D) {
            ctx.worldData().getCachedWorld().getLocationsOf("mob_spawner", 1, ctx.playerFeet().x, ctx.playerFeet().z, 2)
                    .forEach(mobspawner -> res.add(new Avoidance(mobspawner, mobSpawnerCoeff, Cheesecake.settings().mobSpawnerAvoidanceRadius.value)));
        }
        if (mobCoeff != 1.0D) {
            ctx.entitiesStream()
                    .filter(entity -> entity instanceof MobEntity)
                    .filter(entity -> (!(entity instanceof SpiderEntity)) || ctx.player().getBrightnessAtEyes() < 0.5)
                    .filter(entity -> !(entity instanceof ZombifiedPiglinEntity) || ((ZombifiedPiglinEntity) entity).getAttacker() != null)
                    .filter(entity -> !(entity instanceof EndermanEntity) || ((EndermanEntity) entity).isAngry())
                    .forEach(entity -> res.add(new Avoidance(entity.getBlockPos(), mobCoeff, Cheesecake.settings().mobAvoidanceRadius.value)));
        }
        return res;
    }

    public void applySpherical(Long2DoubleOpenHashMap map) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        long hash = BetterBlockPos.longHash(centerX + x, centerY + y, centerZ + z);
                        map.put(hash, map.get(hash) * coefficient);
                    }
                }
            }
        }
    }
}
