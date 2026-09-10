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

package cheesecake.launch.mixins;

import cheesecake.utils.accessor.IChunkArray;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public abstract class MixinChunkArray implements IChunkArray {
    @Final
    @Shadow
    AtomicReferenceArray<LevelChunk> chunks;
    @Final
    @Shadow
    int chunkRadius;

    @Final
    @Shadow
    private int viewRange;
    @Shadow
    int viewCenterX;
    @Shadow
    int viewCenterZ;
    @Shadow
    int chunkCount;

    @Shadow
    abstract boolean inRange(int x, int z);

    @Shadow
    abstract int getIndex(int x, int z);

    @Shadow
    protected abstract void replace(int index, LevelChunk chunk);

    @Override
    public int centerX() {
        return viewCenterX;
    }

    @Override
    public int centerZ() {
        return viewCenterZ;
    }

    @Override
    public int viewDistance() {
        return chunkRadius;
    }

    @Override
    public AtomicReferenceArray<LevelChunk> getChunks() {
        return chunks;
    }

    @Override
    public void copyFrom(IChunkArray other) {
        viewCenterX = other.centerX();
        viewCenterZ = other.centerZ();

        AtomicReferenceArray<LevelChunk> copyingFrom = other.getChunks();
        for (int k = 0; k < copyingFrom.length(); ++k) {
            LevelChunk chunk = copyingFrom.get(k);
            if (chunk != null) {
                ChunkPos chunkpos = chunk.getPos();
                if (inRange(chunkpos.x, chunkpos.z)) {
                    int index = getIndex(chunkpos.x, chunkpos.z);
                    if (chunks.get(index) != null) {
                        throw new IllegalStateException("Doing this would mutate the client's REAL loaded chunks?!");
                    }
                    replace(index, chunk);
                }
            }
        }
    }
}
