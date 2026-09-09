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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.collection.PaletteStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
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

    private static final BlockState AIR_BLOCK_STATE = Blocks.AIR.getDefaultState();
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
    private final RegistryKey<World> dimension;
    final int minY;
    private final BlockStateOctreeInterface boi;

    public NetherPathfinderContext(long seed, Path cache, World world) {
        this.dimension = world.getRegistryKey();
        this.minY = world.getDimension().minY();
        final int dim;
        if (this.dimension == World.NETHER) {
            dim = NetherPathfinder.DIMENSION_NETHER;
        } else if (this.dimension == World.END) {
            dim = NetherPathfinder.DIMENSION_END;
        } else {
            dim = NetherPathfinder.DIMENSION_OVERWORLD;
        }
        int height = Math.min(world.getDimension().height(), 384);
        if (!Cheesecake.settings().elytraAllowAboveRoof.value && dim == NetherPathfinder.DIMENSION_NETHER) {
            height = Math.min(height, 128);
        }
        this.maxHeight = height;
        this.context = NetherPathfinder.newContext(seed, cache != null ? cache.toString() : null, dim, height, Cheesecake.settings().elytraCustomAllocator.value);
        this.seed = seed;
        this.boi = new BlockStateOctreeInterface(this);
    }

    public boolean hasChunk(ChunkPos pos) {
        return NetherPathfinder.hasChunkFromJava(this.context, pos.x, pos.z);
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

    public void queueForPacking(final WorldChunk chunkIn) {
        final SoftReference<WorldChunk> ref = new SoftReference<>(chunkIn);
        this.writeExecutor.execute(() -> {
            // TODO: Prioritize packing recent chunks and/or ones that the path goes through,
            //       and prune the oldest chunks per chunkPackerQueueMaxSize
            final WorldChunk chunk = ref.get();
            if (chunk != null) {
                writeLock.lock();
                try {
                    // we might free this chunk
                    this.boi.chunkPtr = 0L;
                    long ptr = NetherPathfinder.allocateAndInsertChunk(this.context, chunk.getPos().x, chunk.getPos().z);
                    writeChunkData(chunk, ptr);
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
                long ptr = NetherPathfinder.getChunk(this.context, chunkPos.x, chunkPos.z);
                if (ptr == 0) {
                    return; // this shouldn't ever happen
                }
                event.getBlocks().forEach(pair -> {
                    BlockPos pos = pair.first().down(minY);
                    if (pos.getY() < 0 || pos.getY() >= 384) {
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
        final BlockPos adjustedSrc = src.down(minY);
        final BlockPos adjustedDst = dst.down(minY);
        boolean generate = Cheesecake.settings().elytraPredictTerrain.value && this.dimension == World.NETHER;
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

                return new UnpackedSegment(UnpackedSegment.from(segment).collect().stream().map(pos -> pos.up(minY)), segment.finished);
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
    public boolean raytrace(final Vec3d start, final Vec3d end) {
        final Vec3d adjustedStart = start.subtract(0, this.minY, 0);
        final Vec3d adjustedEnd = end.subtract(0, this.minY, 0);
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

        switch (visibility) {
            case Visibility.ALL:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, false) == -1;
            case Visibility.NONE:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) == -1;
            case Visibility.ANY:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) != -1;
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

        NetherPathfinder.raytrace(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, hitsOut, hitPosOut);
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

        NetherPathfinder.freeContext(this.context);
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

    private static void writeChunkData(WorldChunk chunk, long chunkPtr) {
        try {
            ChunkSection[] sections = chunk.getSectionArray();
            final int maxSections = Math.min(sections.length, 24); // pathfinder support stops at 384/16 sections
            for (int y0 = 0; y0 < maxSections; y0++) {
                final ChunkSection section = sections[y0];
                if (section == null || section.isEmpty()) {
                    continue;
                }
                final PalettedContainer<BlockState> bsc = section.getBlockStateContainer();
                IPalettedContainer<BlockState> accessor = (IPalettedContainer<BlockState>) bsc;
                Palette<BlockState> palette = accessor.getPalette();
                // Mushrooms spawn on the roof and writing them as solid will cause pages to be unnecessarily allocated.
                // Palette.index can't be used because it may update the palette
                int airId = -1;
                int caveAirId = -1;
                int redMushroomId = -1;
                int brownMushroomId = -1;
                for (int i = 0; i < palette.getSize(); i++) {
                    BlockState bs = palette.get(i);
                    if (bs == Blocks.AIR.getDefaultState()) {
                        airId = i;
                    } else if (bs == Blocks.CAVE_AIR.getDefaultState()) {
                        caveAirId = i;
                    } else if (bs == Blocks.RED_MUSHROOM.getDefaultState()) {
                        redMushroomId = i;
                    } else if (bs == Blocks.BROWN_MUSHROOM.getDefaultState()) {
                        brownMushroomId = i;
                    }
                }
                if (airId == -1 & caveAirId == -1) {
                    final long bytesInSection = SECTION_BLOCKS / 8;
                    UNSAFE.setMemory(chunkPtr + (y0 * bytesInSection), bytesInSection, (byte) 0xFF);
                    continue;
                }
                // pasted from FasterWorldScanner
                final PaletteStorage array = accessor.getStorage();
                if (array == null) {
                    continue;
                }
                final long[] longArray = array.getData();
                final int arraySize = array.getSize();
                int bitsPerEntry = array.getElementBits();
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
