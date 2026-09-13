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
import cheesecake.process.elytra.pathfinder.Chunk;
import cheesecake.process.elytra.pathfinder.NetherPathfinder;
import cheesecake.process.elytra.pathfinder.PathSegment;
import cheesecake.process.elytra.pathfinder.Raytracer;
import cheesecake.utils.accessor.IPalettedContainer;

import java.lang.ref.SoftReference;
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

    // The native library needed this lock held while there were pointers to its chunks in Java.
    // The port needs none of that: a chunk is an object that stays valid for whoever holds it, and
    // the table takes lookups, inserts and culls from any thread at once. The lock is kept so that
    // the threads still run in the order the flights were tested in -- packing, culling and
    // generating searches on the write side, everything else on the read side.
    public final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    public final ReentrantReadWriteLock.ReadLock readLock = rwl.readLock();
    public final ReentrantReadWriteLock.WriteLock writeLock = rwl.writeLock();
    private final int maxHeight;

    // Visible for access in BlockStateOctreeInterface
    final NetherPathfinder context;
    private final long seed;
    // write locked operations
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(named("nether-pathfinder-write"));
    // operations that don't make changes to the chunk cache. could use multiple threads but i'm not sure if it would cause problems.
    private final ExecutorService readExecutor = Executors.newSingleThreadExecutor(named("nether-pathfinder-read"));
    private final ResourceKey<Level> dimension;
    /** Whether a region cache was given, in which case a path calculation can insert chunks. */
    private final boolean cached;
    final int minY;
    private final BlockStateOctreeInterface boi;

    /**
     * Named, so that a crash log or a trace says which thread was inside the pathfinder rather
     * than "pool-20-thread-1"; daemon, so that a context still working at exit does not hold the
     * game open, which the client reports as a crash.
     */
    private static java.util.concurrent.ThreadFactory named(final String name) {
        return runnable -> {
            final Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    public NetherPathfinderContext(long seed, Path cache, Level world) {
        this.dimension = world.dimension();
        this.minY = world.dimensionType().minY();
        final NetherPathfinder.Dimension dim;
        if (this.dimension == Level.NETHER) {
            dim = NetherPathfinder.Dimension.NETHER;
        } else if (this.dimension == Level.END) {
            dim = NetherPathfinder.Dimension.END;
        } else {
            dim = NetherPathfinder.Dimension.OVERWORLD;
        }
        final int height = heightFor(world);
        this.maxHeight = height;
        this.context = new NetherPathfinder(seed, cache != null ? cache.toString() : null, dim, height);
        this.cached = cache != null;
        this.seed = seed;
        this.boi = new BlockStateOctreeInterface(this);
    }

    public boolean hasChunk(ChunkPos pos) {
        return this.context.hasChunkFromCaller(pos.x(), pos.z());
    }

    public void queueCacheCulling(int chunkX, int chunkZ, int maxDistanceBlocks) {
        this.writeExecutor.execute(() -> {
            writeLock.lock();
            try {
                this.boi.invalidate();
                this.context.cullFarChunks(chunkX, chunkZ, maxDistanceBlocks);
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
                    // we might replace this chunk
                    this.boi.invalidate();
                    final Chunk packed = this.context.allocateAndInsertChunk(chunk.getPos().x(), chunk.getPos().z());
                    writeChunkData(chunk, packed);
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
                final Chunk chunk = this.context.getChunk(chunkPos.x(), chunkPos.z());
                if (chunk == null) {
                    return; // this shouldn't ever happen
                }
                event.getBlocks().forEach(pair -> {
                    BlockPos pos = pair.first().below(minY);
                    if (pos.getY() < 0 || pos.getY() >= 384) {
                        return;
                    }
                    boolean isSolid = !pair.second().isAir();
                    // one block at a time in a chunk that is in use, so keep the x8 summary exact
                    chunk.setBlock(pos.getX() & 15, pos.getY(), pos.getZ() & 15, isSolid, true);
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
        // A search that generates terrain inserts the chunks it makes, so it is a writer. A search
        // that does not is a reader, as upstream treats it, although with a region cache it too
        // inserts chunks, a region's the first time it reaches one; the port's table takes that
        // from any thread. Taking the write lock for these searches instead was tried and
        // grounded the in-world test's return flight in four runs of ten: while a writer holds
        // the lock the game thread cannot take the read side, so the player is not steered, and a
        // flight recalculates its segments far too often to be blind for each one.
        Lock l = generate ? writeLock : readLock;
        ExecutorService exec = generate ? writeExecutor : readExecutor;
        return CompletableFuture.supplyAsync(() -> {
            l.lock();
            try {
                final PathSegment segment = this.context.pathFind(
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
        if (UnusableRays.hasNonFinite(startX, startY, startZ, endX, endY, endZ)) {
            return false;
        }
        final double adjustedStartY = startY - this.minY;
        final double adjustedEndY = endY - this.minY;
        return Raytracer.raytrace(this.context, startX, adjustedStartY, startZ,
                UnusableRays.offBoundary(endX, startX), UnusableRays.offBoundary(adjustedEndY, adjustedStartY), UnusableRays.offBoundary(endZ, startZ),
                NetherPathfinder.CacheMiss.SOLID) == null;
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
        if (UnusableRays.hasNonFinite(start.x, start.y, start.z, end.x, end.y, end.z)) {
            return false;
        }
        final Vec3 adjustedStart = start.subtract(0, this.minY, 0);
        final Vec3 adjustedEnd = end.subtract(0, this.minY, 0);
        return Raytracer.raytrace(this.context, adjustedStart.x, adjustedStart.y, adjustedStart.z,
                UnusableRays.offBoundary(adjustedEnd.x, adjustedStart.x), UnusableRays.offBoundary(adjustedEnd.y, adjustedStart.y), UnusableRays.offBoundary(adjustedEnd.z, adjustedStart.z),
                NetherPathfinder.CacheMiss.SOLID) == null;
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
        if (UnusableRays.countNonFinite(count, src, dst) > 0) {
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
        UnusableRays.endsOffBoundary(kept, keptSrc, keptDst);

        switch (visibility) {
            case Visibility.ALL:
                for (int i = 0; i < kept; i++) {
                    if (!clear(keptSrc, keptDst, i)) {
                        return false;
                    }
                }
                return true;
            case Visibility.NONE:
                for (int i = 0; i < kept; i++) {
                    if (clear(keptSrc, keptDst, i)) {
                        return false;
                    }
                }
                return true;
            case Visibility.ANY:
                for (int i = 0; i < kept; i++) {
                    if (clear(keptSrc, keptDst, i)) {
                        return true;
                    }
                }
                return false;
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
        // a ray with a coordinate that is not a finite number is not a sight line, so report no
        // hit for it either.
        if (UnusableRays.countNonFinite(count, src, dst) > 0) {
            java.util.Arrays.fill(hitsOut, false);
            return;
        }
        final int degenerate = UnusableRays.countZeroLength(count, src, dst);
        if (degenerate == 0) {
            UnusableRays.endsOffBoundary(count, src, dst);
            raytraceEach(count, src, dst, hitsOut, hitPosOut);
            return;
        }
        final double[] keptSrc = UnusableRays.withoutZeroLength(count, src, dst, src, degenerate);
        final double[] keptDst = UnusableRays.withoutZeroLength(count, src, dst, dst, degenerate);
        final int kept = count - degenerate;
        final boolean[] keptHits = new boolean[kept];
        final double[] keptHitPos = new double[kept * 3];
        if (kept > 0) {
            UnusableRays.endsOffBoundary(kept, keptSrc, keptDst);
            raytraceEach(kept, keptSrc, keptDst, keptHits, keptHitPos);
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

    /** Whether ray {@code i} of a batch already in the pathfinder's y range reaches its end. */
    private boolean clear(double[] src, double[] dst, int i) {
        final int o = i * 3;
        return Raytracer.raytrace(this.context, src[o], src[o + 1], src[o + 2], dst[o], dst[o + 1], dst[o + 2],
                NetherPathfinder.CacheMiss.SOLID) == null;
    }

    /** Traces a batch of rays already in the pathfinder's y range, one at a time. */
    private void raytraceEach(int count, double[] src, double[] dst, boolean[] hitsOut, double[] hitPosOut) {
        for (int i = 0; i < count; i++) {
            final int o = i * 3;
            final Vec3 hit = Raytracer.raytrace(this.context, src[o], src[o + 1], src[o + 2], dst[o], dst[o + 1], dst[o + 2],
                    NetherPathfinder.CacheMiss.SOLID);
            hitsOut[i] = hit != null;
            if (hit != null && hitPosOut != null) {
                hitPosOut[o] = hit.x;
                hitPosOut[o + 1] = hit.y;
                hitPosOut[o + 2] = hit.z;
            }
        }
    }

    public boolean passable(int x, int y, int z) {
        return !this.boi.get0(x, y, z);
    }

    public void cancel() {
        this.context.cancel();
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

        // The elytra solver may still be raytracing into this context from a thread of its own:
        // ElytraProcess tears the behavior and the context down as two separate tasks on a pool
        // with four threads. With the native library that was fatal, memory freed under the
        // solver's feet. A chunk is an object now, and closing the table under a running ray or
        // search only changes what it answers, so there is nothing to wait for.
        this.context.close();
    }

    public long getSeed() {
        return this.seed;
    }

    /** The dimension this context was built for; its height and floor are that dimension's. */
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    /** The height a context for this world is built with, under the current settings. */
    public static int heightFor(Level world) {
        int height = Math.min(world.dimensionType().height(), 384);
        if (!Cheesecake.settings().elytraAllowAboveRoof.value && world.dimension() == Level.NETHER) {
            height = Math.min(height, 128);
        }
        return height;
    }

    /**
     * Whether this context is the one the current world and settings call for. A context is built
     * for one dimension, one height, one seed and one cache, and cannot be changed after.
     */
    public boolean builtFor(Level world, long seed, Path cache) {
        return this.dimension == world.dimension()
                && this.maxHeight == heightFor(world)
                && this.seed == seed
                && this.cached == (cache != null);
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

    private static void writeChunkData(LevelChunk chunk, Chunk packed) {
        try {
            LevelChunkSection[] sections = chunk.getSections();
            final int maxSections = Math.min(sections.length, 24); // pathfinder support stops at 384/16 sections
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
                    packed.fillSection(y0, true);
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
                            packed.setBlock(x, y, z, true);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static final class Visibility {

        public static final int ALL = 0;
        public static final int NONE = 1;
        public static final int ANY = 2;

        private Visibility() {}
    }
}
