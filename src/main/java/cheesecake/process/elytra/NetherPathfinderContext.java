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

package cheesecake.process.elytra;

import cheesecake.Cheesecake;
import cheesecake.api.event.events.BlockChangeEvent;
import cheesecake.utils.accessor.IPalettedContainer;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import dev.babbaj.pathfinder.PathSegment;
import sun.misc.Unsafe;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;

/**
 * @author Brady
 */
@SuppressWarnings({"unchecked"})
public final class NetherPathfinderContext implements IElytraPathFinder {

    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final BlockState AIR_BLOCK_STATE = Blocks.AIR.defaultBlockState();
    /**
     * Blocks in one 16x16x16 chunk section. The pathfinder stores each as a single bit.
     */
    private static final int SECTION_BLOCKS = 16 * 16 * 16;

    // This lock must be held while there are active pointers to chunks in java,
    // but we just hold it for the entire tick so we don't have to think much about it.
    public final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    public final ReentrantReadWriteLock.ReadLock readLock = rwl.readLock();
    public final ReentrantReadWriteLock.WriteLock writeLock = rwl.writeLock();
    private final int maxHeight;

    // Visible for access in BlockStateOctreeInterface
    final long context;
    private final long seed;
    // write locked operations
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
    // operations that don't make changes to the chunk cache. could use multiple threads but i'm not sure if it would cause problems.
    private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();
    private final ResourceKey<Level> dimension;
    final int minY;
    private final BlockStateOctreeInterface boi;

    public NetherPathfinderContext(long seed, Path cache, Level world) {
        this.dimension = world.dimension();
        this.minY = world.dimensionType().minY();
        final int dim;
        if (this.dimension == Level.NETHER) {
            dim = NetherPathfinder.DIMENSION_NETHER;
        } else if (this.dimension == Level.END) {
            dim = NetherPathfinder.DIMENSION_END;
        } else {
            dim = NetherPathfinder.DIMENSION_OVERWORLD;
        }
        int height = Math.min(world.dimensionType().height(), 384);
        if (!Cheesecake.settings().elytraAllowAboveRoof.value && dim == NetherPathfinder.DIMENSION_NETHER) {
            height = Math.min(height, 128);
        }
        this.maxHeight = height;
        this.context = NetherPathfinder.newContext(seed, cache != null ? cache.toString() : null, dim, height, Cheesecake.settings().elytraCustomAllocator.value);
        this.seed = seed;
        this.boi = new BlockStateOctreeInterface(this);
    }

    public boolean hasChunk(ChunkPos pos) {
        return NetherPathfinder.hasChunkFromJava(this.context, pos.x(), pos.z());
    }

    public void queueCacheCulling(int chunkX, int chunkZ, int maxDistanceBlocks) {
        this.writeExecutor.execute(() -> {
            writeLock.lock();
            try {
                this.boi.chunkPtr = 0L;
                NetherPathfinder.cullFarChunks(this.context, chunkX, chunkZ, maxDistanceBlocks);
            } finally {
                writeLock.unlock();
            }
        });
    }

    public void queueForPacking(final LevelChunk chunkIn) {
        final SoftReference<LevelChunk> ref = new SoftReference<>(chunkIn);
        this.writeExecutor.execute(() -> {
            // TODO: Prioritize packing recent chunks and/or ones that the path goes through,
            //       and prune the oldest chunks per chunkPackerQueueMaxSize
            final LevelChunk chunk = ref.get();
            if (chunk != null) {
                writeLock.lock();
                try {
                    // we might free this chunk
                    this.boi.chunkPtr = 0L;
                    long ptr = NetherPathfinder.allocateAndInsertChunk(this.context, chunk.getPos().x(), chunk.getPos().z());
                    writeChunkData(chunk, ptr, this.maxHeight);
                } finally {
                    writeLock.unlock();
                }
            }
        });
    }

    public void queueBlockUpdate(BlockChangeEvent event) {
        this.writeExecutor.execute(() -> {
            ChunkPos chunkPos = event.getChunkPos();
            // not inserting or deleting from the cache hashmap but it would still be bad for this function to race with itself
            writeLock.lock();
            try {
                long ptr = NetherPathfinder.getChunk(this.context, chunkPos.x(), chunkPos.z());
                if (ptr == 0) {
                    return; // this shouldn't ever happen
                }
                event.getBlocks().forEach(pair -> {
                    BlockPos pos = pair.first().below(minY);
                    // Against this context's height, not the tallest a context can be: a block
                    // update above what the chunk was allocated for writes past the end of it.
                    if (pos.getY() < 0 || pos.getY() >= maxHeight) {
                        return;
                    }
                    boolean isSolid = pair.second() != AIR_BLOCK_STATE;
                    Octree.setBlock(ptr, pos.getX() & 15, pos.getY(), pos.getZ() & 15, isSolid);
                });
            } finally {
                writeLock.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<UnpackedSegment> pathFindAsync(final BlockPos src, final BlockPos dst) {
        final BlockPos adjustedSrc = src.below(minY);
        final BlockPos adjustedDst = dst.below(minY);
        boolean generate = Cheesecake.settings().elytraPredictTerrain.value && this.dimension == Level.NETHER;
        Lock l = generate ? writeLock : readLock;
        ExecutorService exec = generate ? writeExecutor : readExecutor;
        return CompletableFuture.supplyAsync(() -> {
            l.lock();
            try {
                final PathSegment segment = NetherPathfinder.pathFind(
                        this.context,
                        adjustedSrc.getX(), adjustedSrc.getY(), adjustedSrc.getZ(),
                        adjustedDst.getX(), adjustedDst.getY(), adjustedDst.getZ(),
                        !Cheesecake.settings().elytraAllowTightSpaces.value, // atleastX4
                        false, // refine
                        10000, // timeoutMs
                        !generate, // useAirIfChunkNotLoaded
                        // TODO: Determine appropriate cost value
                        8.0 // fakeChunkCost
                );
                if (segment == null) {
                    throw new PathCalculationException("Path calculation failed");
                }

                return new UnpackedSegment(UnpackedSegment.from(segment).collect().stream().map(pos -> pos.above(minY)), segment.finished);
            } finally {
                l.unlock();
            }
        }, exec);
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param startX The start X coordinate
     * @param startY The start Y coordinate
     * @param startZ The start Z coordinate
     * @param endX   The end X coordinate
     * @param endY   The end Y coordinate
     * @param endZ   The end Z coordinate
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final double startX, final double startY, final double startZ,
                            final double endX, final double endY, final double endZ) {
        if (UnusableRays.isZeroLength(startX, startY, startZ, endX, endY, endZ)) {
            return true;
        }
        if (UnusableRays.hasNaN(startX, startY, startZ, endX, endY, endZ)) {
            return false;
        }
        final double adjustedStartY = startY - this.minY;
        final double adjustedEndY = endY - this.minY;
        return NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID, startX, adjustedStartY, startZ, endX, adjustedEndY, endZ);
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param start The starting point
     * @param end   The ending point
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final Vec3 start, final Vec3 end) {
        if (UnusableRays.isZeroLength(start.x, start.y, start.z, end.x, end.y, end.z)) {
            return true;
        }
        if (UnusableRays.hasNaN(start.x, start.y, start.z, end.x, end.y, end.z)) {
            return false;
        }
        final Vec3 adjustedStart = start.subtract(0, this.minY, 0);
        final Vec3 adjustedEnd = end.subtract(0, this.minY, 0);
        return NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID, adjustedStart.x, adjustedStart.y, adjustedStart.z, adjustedEnd.x, adjustedEnd.y, adjustedEnd.z);
    }

    public boolean raytrace(final int count, final double[] src, final double[] dst, final int visibility) {
        if (src.length != count * 3 || dst.length != count * 3) {
            throw new IllegalArgumentException("Bad array lengths");
        }

        for (int i = 1; i < src.length; i += 3) {
            src[i] -= this.minY;
            dst[i] -= this.minY;
        }

        // Answer for the zero-length segments here rather than asking about them, and put only
        // the rest to the library. A point is always visible from itself, which decides ANY and
        // NONE outright and leaves ALL to the segments that remain.
        if (UnusableRays.countNaN(count, src, dst) > 0) {
            return visibility == Visibility.NONE;
        }
        final int degenerate = UnusableRays.countZeroLength(count, src, dst);
        if (degenerate > 0) {
            if (visibility == Visibility.ANY) {
                return true;
            }
            if (visibility == Visibility.NONE) {
                return false;
            }
            if (degenerate == count && visibility == Visibility.ALL) {
                return true;
            }
        }
        final double[] keptSrc = degenerate == 0 ? src : UnusableRays.withoutZeroLength(count, src, dst, src, degenerate);
        final double[] keptDst = degenerate == 0 ? dst : UnusableRays.withoutZeroLength(count, src, dst, dst, degenerate);
        final int kept = count - degenerate;

        switch (visibility) {
            case Visibility.ALL:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, kept, keptSrc, keptDst, false) == -1;
            case Visibility.NONE:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, kept, keptSrc, keptDst, true) == -1;
            case Visibility.ANY:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, kept, keptSrc, keptDst, true) != -1;
            default:
                throw new IllegalArgumentException("lol");
        }
    }

    public void raytrace(final int count, final double[] src, final double[] dst, final boolean[] hitsOut, final double[] hitPosOut) {
        if (src.length != count * 3 || dst.length != count * 3) {
            throw new IllegalArgumentException("Bad array lengths");
        }

        for (int i = 1; i < src.length; i += 3) {
            src[i] -= this.minY;
            dst[i] -= this.minY;
        }

        // Same reason as above. A zero-length ray passes through nothing, so it hits nothing;
        // a ray that is not a number is not a sight line, so report no hit for it either.
        if (UnusableRays.countNaN(count, src, dst) > 0) {
            java.util.Arrays.fill(hitsOut, false);
            return;
        }
        final int degenerate = UnusableRays.countZeroLength(count, src, dst);
        if (degenerate == 0) {
            NetherPathfinder.raytrace(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, hitsOut, hitPosOut);
            return;
        }
        final double[] keptSrc = UnusableRays.withoutZeroLength(count, src, dst, src, degenerate);
        final double[] keptDst = UnusableRays.withoutZeroLength(count, src, dst, dst, degenerate);
        final int kept = count - degenerate;
        final boolean[] keptHits = new boolean[kept];
        final double[] keptHitPos = new double[kept * 3];
        if (kept > 0) {
            NetherPathfinder.raytrace(this.context, NetherPathfinder.CACHE_MISS_SOLID, kept, keptSrc, keptDst, keptHits, keptHitPos);
        }
        int at = 0;
        for (int i = 0; i < count; i++) {
            if (UnusableRays.isZeroLength(src, dst, i)) {
                hitsOut[i] = false;
            } else {
                hitsOut[i] = keptHits[at];
                System.arraycopy(keptHitPos, at * 3, hitPosOut, i * 3, 3);
                at++;
            }
        }
    }

    public boolean passable(int x, int y, int z) {
        return !this.boi.get0(x, y, z);
    }

    public void cancel() {
        NetherPathfinder.cancel(this.context);
    }

    public void destroy() {
        this.cancel();
        // Ignore anything that was queued up, just shutdown the executor
        this.readExecutor.shutdownNow();
        this.writeExecutor.shutdownNow();

        try {
            while (!this.readExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {}
            while (!this.writeExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {}
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Shutting down this class's own executors is not enough to know that nobody is inside the
        // library. The elytra solver raytraces into this context from a thread of its own, under
        // the read lock, and ElytraProcess tears the behavior and the context down as two separate
        // tasks on a pool with four threads, so the two run at once: free the context here without
        // waiting and the solver is left reading memory that has been handed back. That is what
        // ends the game in the Nether -- as a segmentation fault inside the library on the solver's
        // thread if the read lands on unmapped memory, and otherwise as a traversal over freed
        // memory that arrives nowhere, which the library reports as "raytrace whiffed" before
        // calling exit(696969), a status of 137 that reads like a kill and is not one.
        //
        // Taking the write lock is what the lock is for: it waits for every reader to leave and
        // keeps the next one out, so the pointer cannot be freed with a thread standing on it.
        writeLock.lock();
        try {
            NetherPathfinder.freeContext(this.context);
        } finally {
            writeLock.unlock();
        }
    }

    public long getSeed() {
        return this.seed;
    }

    public void acquireReadLock() {
        this.readLock.lock();
    }

    public boolean tryAcquireReadLock() {
        return this.readLock.tryLock();
    }

    public void releaseReadLock() {
        this.readLock.unlock();
    }

    public int getMaxHeight() {
        return this.maxHeight;
    }

    /**
     * @param maxHeight the height the context was created with. The chunk is allocated to that
     *                  height, so it bounds what may be written into it -- the number of sections
     *                  the world's chunk has does not. In the Nether with elytraAllowAboveRoof off
     *                  the two differ by a factor of two, and writing the world's sixteen sections
     *                  into the eight the chunk has room for walks off the end of the allocation.
     */
    private static void writeChunkData(LevelChunk chunk, long chunkPtr, int maxHeight) {
        try {
            LevelChunkSection[] sections = chunk.getSections();
            final int maxSections = Math.min(sections.length, maxHeight / 16);
            for (int y0 = 0; y0 < maxSections; y0++) {
                final LevelChunkSection section = sections[y0];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                final PalettedContainer<BlockState> bsc = section.getStates();
                IPalettedContainer<BlockState> accessor = (IPalettedContainer<BlockState>) bsc;
                Palette<BlockState> palette = accessor.getPalette();
                // Mushrooms spawn on the roof and writing them as solid will cause pages to be unnecessarily allocated.
                // Palette.index can't be used because it may update the palette
                int airId = -1;
                int caveAirId = -1;
                int redMushroomId = -1;
                int brownMushroomId = -1;
                for (int i = 0; i < palette.getSize(); i++) {
                    BlockState bs = palette.valueFor(i);
                    if (bs == Blocks.AIR.defaultBlockState()) {
                        airId = i;
                    } else if (bs == Blocks.CAVE_AIR.defaultBlockState()) {
                        caveAirId = i;
                    } else if (bs == Blocks.RED_MUSHROOM.defaultBlockState()) {
                        redMushroomId = i;
                    } else if (bs == Blocks.BROWN_MUSHROOM.defaultBlockState()) {
                        brownMushroomId = i;
                    }
                }
                if (airId == -1 & caveAirId == -1) {
                    final long bytesInSection = SECTION_BLOCKS / 8;
                    UNSAFE.setMemory(chunkPtr + (y0 * bytesInSection), bytesInSection, (byte) 0xFF);
                    continue;
                }
                // pasted from FasterWorldScanner
                final BitStorage array = accessor.getStorage();
                if (array == null) {
                    continue;
                }
                final long[] longArray = array.getRaw();
                final int arraySize = array.getSize();
                int bitsPerEntry = array.getBits();
                long maxEntryValue = (1L << bitsPerEntry) - 1L;

                final int yReal = y0 << 4;
                for (int i = 0, idx = 0; i < longArray.length && idx < arraySize; ++i) {
                    long l = longArray[i];
                    for (int offset = 0; offset <= (64 - bitsPerEntry) && idx < arraySize; offset += bitsPerEntry, ++idx) {
                        int value = (int) ((l >> offset) & maxEntryValue);
                        int x = (idx & 15);
                        int y = yReal + (idx >> 8);
                        int z = ((idx >> 4) & 15);

                        // Avoid unnecessary writes that may trigger a page allocation
                        if (!(value == airId | value == caveAirId) & value != redMushroomId & value != brownMushroomId) {
                            Octree.setBlock(chunkPtr, x, y, z, true);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static boolean isSupported() {
        return NetherPathfinder.isThisSystemSupported();
    }

    public static final class Visibility {

        public static final int ALL = 0;
        public static final int NONE = 1;
        public static final int ANY = 2;

        private Visibility() {}
    }
}
