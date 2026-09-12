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
 * Rays the elytra process answers itself, without asking the pathfinder.
 * <ul>
 *     <li>A ray of zero length. The solver produces one whenever it measures the way to a point
 *     the player already occupies, which is what the eight hitbox rays all become on a tick where
 *     the path is rebuilt around a landing spot underneath the player. A point is visible from
 *     itself, and passes through nothing, so it hits nothing.</li>
 *     <li>A ray with a coordinate that is not a finite number. It is not a sight line anybody
 *     should act on, so it is not visible, and it hits nothing. The pathfinder refuses one with
 *     an exception, which is right for a library and wrong for a solver thread.</li>
 *     <li>A ray that ends exactly on a voxel boundary, which every ray aimed at a path node does,
 *     since every node is an integer corner. The end is moved a millionth of a block off the
 *     boundary towards the start, which keeps it strictly inside the voxel the ray crosses last.
 *     A ray that ends on the corner of a solid block is then answered by that last voxel and not
 *     by how the traversal's arithmetic happens to round, so a node that touches a block answers
 *     the same from every direction.</li>
 * </ul>
 * The first two were once fatal: handed either, the native nether-pathfinder printed "raytrace
 * whiffed" and called {@code exit(696969)}, a status of 137 that reads like a kill and is not one,
 * and an infinity in z made {@code isVisible} never return at all. The end on a boundary could
 * make its traversal step out of its last node into one the ray never enters, and exit the same
 * way; the ray that was caught doing it, from CI run 34632119143: {@code (386.7112215521066,
 * 137.40911926818373, 7.0554159455922285) -> (416.0, 142.0, 0.0)}. The Java port answers all
 * three without harm, and the filter stays for the answers the flight wants.
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
