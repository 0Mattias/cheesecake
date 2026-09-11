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

/**
 * Rays that must not be handed to nether-pathfinder.
 * <p>
 * Given one, the library prints "raytrace whiffed" and calls {@code exit(696969)}. The status a
 * process exits with is its low eight bits, and 696969 ends in 137, which is also what a shell
 * reports for a process killed by SIGKILL -- so Gradle says "this value may indicate that the
 * process was terminated with the SIGKILL signal, which is often caused by the system running out
 * of memory" and the whole thing reads as an out-of-memory kill. It is nothing of the kind. The
 * game is simply gone: no crash report, no JVM error log, nothing in the kernel log and nothing
 * from systemd-oomd, because as far as the operating system is concerned it exited normally.
 * <p>
 * Two inputs are known to do it, each reproducible on its own:
 * <ul>
 *     <li>a ray of zero length. Any nonzero length is accepted, down to the last representable
 *     step. The elytra solver produces one whenever it measures the way to a point the player
 *     already occupies, which is what the eight hitbox rays all become on a tick where the path is
 *     rebuilt around a landing spot underneath the player.</li>
 *     <li>a coordinate that is not a number.</li>
 * </ul>
 * Neither can be answered by asking the library, so they are answered here: a point is always
 * visible from itself, and a ray that is not a number is not a sight line anybody should act on.
 * <p>
 * A third does the same and is the one that was ending the game in the Nether: a ray that ends
 * exactly on a voxel boundary. The traversal walks the octree node by node and stops when it has
 * covered the ray's length; when the end sits exactly on a face, and above all on an edge or a
 * corner where faces meet, the step out of the last node and the arrival at the end fall on the
 * same parameter value, floating point decides which is seen first, and when it is the step the
 * traversal moves into a neighbouring node the ray never enters, finds no intersection, and the
 * library exits. Every node on a flight path is an integer corner, so every ray the solver aims at
 * one ends on a boundary in all three axes; whether it whiffs depends on the direction it comes
 * from. The ray that was caught doing it, from CI run 34632119143 with the read lock held and
 * nothing else wrong: {@code (386.7112215521066, 137.40911926818373, 7.0554159455922285) ->
 * (416.0, 142.0, 0.0)}. Moving that end by a billionth of a block on any axis returns true.
 * So an end that sits on a boundary is moved a millionth of a block off it, towards the start,
 * which keeps it strictly inside its voxel and changes nothing about what the ray can see.
 * <p>
 * An infinite coordinate is worse than either, and was previously let through on the grounds that
 * the library accepts it. It does not. {@code computeRay} divides the difference by its magnitude,
 * and with an infinity on both sides of that division every direction component comes out NaN --
 * the same state a zero-length ray reaches. An infinity in z then makes {@code isVisible} never
 * return at all: the traversal walks node to node for ever, on whichever thread made the call.
 * That is unrecoverable rather than merely fatal. The elytra solver runs on its own executor, and
 * {@code ElytraBehavior.destroy()} waits on it with {@code awaitTermination(Long.MAX_VALUE)}, so a
 * thread parked inside the library takes the teardown with it, and the write lock that
 * {@code NetherPathfinderContext.destroy()} needs is held by a reader that will never leave.
 * Measured against 1.6: an infinity in x or y returns, one in z does not. Rejecting every
 * non-finite coordinate covers both, and NaN with them, since NaN is not finite either.
 * <p>
 * Kept out of {@link NetherPathfinderContext} so that it can be tested -- initialising that class
 * loads Minecraft's registries, which the unit tests cannot bootstrap.
 */
final class UnusableRays {

    private UnusableRays() {}

    static boolean isZeroLength(final double startX, final double startY, final double startZ,
                                final double endX, final double endY, final double endZ) {
        return startX == endX && startY == endY && startZ == endZ;
    }

    static boolean isZeroLength(final double[] src, final double[] dst, final int i) {
        return isZeroLength(src[i * 3], src[i * 3 + 1], src[i * 3 + 2],
                dst[i * 3], dst[i * 3 + 1], dst[i * 3 + 2]);
    }

    /** How far an end that sits exactly on a voxel boundary is moved off it, in blocks. */
    static final double OFF_BOUNDARY = 1e-6;

    /**
     * One coordinate of a ray's end, moved off the voxel boundary it sits on -- an integral value
     * is one -- towards the start. Left alone when it is not on a boundary, and when the ray does
     * not move along this axis, since there is then no side to move it to.
     */
    static double offBoundary(final double end, final double start) {
        if (end != Math.rint(end) || end == start) {
            return end;
        }
        return start < end ? end - OFF_BOUNDARY : end + OFF_BOUNDARY;
    }

    /** {@link #offBoundary(double, double)} applied to every end in {@code dst}, in place. */
    static void endsOffBoundary(final int count, final double[] src, final double[] dst) {
        for (int i = 0; i < count * 3; i++) {
            dst[i] = offBoundary(dst[i], src[i]);
        }
    }

    static boolean hasNonFinite(final double startX, final double startY, final double startZ,
                                final double endX, final double endY, final double endZ) {
        return !Double.isFinite(startX) || !Double.isFinite(startY) || !Double.isFinite(startZ)
                || !Double.isFinite(endX) || !Double.isFinite(endY) || !Double.isFinite(endZ);
    }

    /** How many of the {@code count} segments carry a coordinate that is not a finite number. */
    static int countNonFinite(final int count, final double[] src, final double[] dst) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (hasNonFinite(src[i * 3], src[i * 3 + 1], src[i * 3 + 2],
                    dst[i * 3], dst[i * 3 + 1], dst[i * 3 + 2])) {
                n++;
            }
        }
        return n;
    }

    /** How many of the {@code count} segments start where they end. */
    static int countZeroLength(final int count, final double[] src, final double[] dst) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (isZeroLength(src, dst, i)) {
                n++;
            }
        }
        return n;
    }

    /**
     * {@code points}, less the entries whose segment starts where it ends. Order is kept, so the
     * result still pairs up with the other side of the same call.
     */
    static double[] withoutZeroLength(final int count, final double[] src, final double[] dst,
                                      final double[] points, final int zeroLength) {
        final double[] kept = new double[(count - zeroLength) * 3];
        int at = 0;
        for (int i = 0; i < count; i++) {
            if (!isZeroLength(src, dst, i)) {
                System.arraycopy(points, i * 3, kept, at * 3, 3);
                at++;
            }
        }
        return kept;
    }
}
