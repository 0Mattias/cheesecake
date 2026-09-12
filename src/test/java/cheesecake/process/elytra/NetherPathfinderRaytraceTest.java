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

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The rays the elytra process answers itself: a ray whose start is its end, one with a coordinate
 * that is not a finite number, and one that ends on a voxel boundary. These cover picking them out
 * and moving the ends; what the pathfinder answers for the rays it is given is tested in
 * {@code cheesecake.process.elytra.pathfinder}.
 */
public class NetherPathfinderRaytraceTest {

    /** Eight rays, as the hitbox raytrace sends, none of them degenerate. */
    @Test
    public void testNothingToDropWhenEveryRayHasLength() {
        final double[] src = new double[24];
        final double[] dst = new double[24];
        for (int i = 0; i < 8; i++) {
            src[i * 3] = i;
            dst[i * 3] = i + 1;
        }
        assertEquals(0, UnusableRays.countZeroLength(8, src, dst));
    }

    /** The case that ends the game: the solver measures the way to where the player already is. */
    @Test
    public void testEveryRayDegenerateWhenDestinationIsThePlayer() {
        final double[] src = { 1.5, 64.0, -2.5, 1.5, 65.8, -2.5 };
        final double[] dst = src.clone();
        assertEquals(2, UnusableRays.countZeroLength(2, src, dst));
    }

    /** A single coordinate apart is enough for the library, so it must not be dropped. */
    @Test
    public void testSmallestDifferenceIsNotDegenerate() {
        final double[] src = { 1.5, 64.0, -2.5 };
        assertEquals(0, UnusableRays.countZeroLength(1, src, new double[] { 1.5, Math.nextUp(64.0), -2.5 }));
        assertEquals(0, UnusableRays.countZeroLength(1, src, new double[] { Math.nextUp(1.5), 64.0, -2.5 }));
        assertEquals(0, UnusableRays.countZeroLength(1, src, new double[] { 1.5, 64.0, Math.nextUp(-2.5) }));
    }

    /** The other input that ends the game. NaN is not equal to itself, so the length check misses it. */
    @Test
    public void testNaNIsCaughtEvenThoughItIsNotEqualToItself() {
        final double[] src = { 1.5, 64.0, -2.5 };
        assertEquals(0, UnusableRays.countZeroLength(1, src, new double[] { Double.NaN, 64.0, -2.5 }));
        assertEquals(1, UnusableRays.countNonFinite(1, src, new double[] { Double.NaN, 64.0, -2.5 }));
        assertEquals(1, UnusableRays.countNonFinite(1, src, new double[] { 1.5, Double.NaN, -2.5 }));
        assertEquals(1, UnusableRays.countNonFinite(1, new double[] { 1.5, Double.NaN, -2.5 }, src));
        assertEquals(0, UnusableRays.countNonFinite(1, src, new double[] { 1.5, 65.0, -2.5 }));
    }

    /**
     * An infinite coordinate was let through on the grounds that the library accepts it, which is
     * true only of the axis that was tried. Against 1.6, an infinity in x or y returns; one in z
     * never returns at all, and the thread that called stays inside the library for good. Every
     * non-finite coordinate is refused now, which is the same rule NaN already fell under.
     */
    @Test
    public void testInfinityIsRefusedOnEveryAxis() {
        final double[] src = { 1.5, 64.0, -2.5 };
        for (final double infinity : new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertEquals(1, UnusableRays.countNonFinite(1, src, new double[] { infinity, 64.0, -2.5 }));
            assertEquals(1, UnusableRays.countNonFinite(1, src, new double[] { 40.5, infinity, -2.5 }));
            assertEquals(1, UnusableRays.countNonFinite(1, src, new double[] { 40.5, 64.0, infinity }));
            assertEquals(1, UnusableRays.countNonFinite(1, new double[] { infinity, 64.0, -2.5 }, src));
        }
        // and it is not the length check that catches them: an infinity apart is still a length
        assertEquals(0, UnusableRays.countZeroLength(1, src, new double[] { Double.POSITIVE_INFINITY, 64.0, -2.5 }));
    }

    /**
     * The ray that ended CI run 34632119143, with the read lock held and nothing else wrong: it
     * ends exactly on the corner where two chunk boundaries meet, and the library exits. Moving
     * that end a billionth of a block on any axis returns true, so the end is moved a millionth,
     * towards the start on every axis it sits on a boundary in.
     */
    @Test
    public void testAnEndOnAVoxelBoundaryIsMovedTowardsTheStart() {
        final double sx = 386.7112215521066, sy = 137.40911926818373, sz = 7.0554159455922285;
        assertEquals(416.0 - UnusableRays.OFF_BOUNDARY, UnusableRays.offBoundary(416.0, sx), 0.0);
        assertEquals(142.0 - UnusableRays.OFF_BOUNDARY, UnusableRays.offBoundary(142.0, sy), 0.0);
        assertEquals(0.0 + UnusableRays.OFF_BOUNDARY, UnusableRays.offBoundary(0.0, sz), 0.0);
        // and from the other side it moves the other way, including at a negative boundary
        assertEquals(-16.0 + UnusableRays.OFF_BOUNDARY, UnusableRays.offBoundary(-16.0, -3.5), 0.0);
        assertEquals(-16.0 - UnusableRays.OFF_BOUNDARY, UnusableRays.offBoundary(-16.0, -20.25), 0.0);
    }

    /** An end inside a voxel, or one the ray does not move towards, is left exactly as it is. */
    @Test
    public void testEndsOffBoundariesAreLeftAlone() {
        assertEquals(416.5, UnusableRays.offBoundary(416.5, 3.0), 0.0);
        assertEquals(0.25, UnusableRays.offBoundary(0.25, -8.0), 0.0);
        assertEquals(64.0, UnusableRays.offBoundary(64.0, 64.0), 0.0); // no extent along this axis
        final double[] src = { 1.5, 64.0, -2.5, 10.0, 70.0, 20.0 };
        final double[] dst = { 1.5, 66.0, -2.5, 16.0, 70.0, 32.5 };
        UnusableRays.endsOffBoundary(2, src, dst);
        assertArrayEquals(new double[] { 1.5, 66.0 - UnusableRays.OFF_BOUNDARY, -2.5, 16.0 - UnusableRays.OFF_BOUNDARY, 70.0, 32.5 }, dst, 0.0);
    }

    /** The largest coordinates a world can hold are ordinary rays and must still be asked about. */
    @Test
    public void testFiniteExtremesAreStillUsable() {
        final double[] src = { -30_000_000.0, -64.0, -30_000_000.0 };
        final double[] dst = { 30_000_000.0, 320.0, 30_000_000.0 };
        assertEquals(0, UnusableRays.countNonFinite(1, src, dst));
        assertEquals(0, UnusableRays.countZeroLength(1, src, dst));
    }

    /** What is left keeps its order and its pairing, and the degenerate middle ray is gone. */
    @Test
    public void testDegenerateSegmentsAreRemovedInPlaceOrder() {
        final double[] src = { 0, 0, 0, 7, 7, 7, 2, 2, 2 };
        final double[] dst = { 1, 1, 1, 7, 7, 7, 3, 3, 3 };
        final int degenerate = UnusableRays.countZeroLength(3, src, dst);
        assertEquals(1, degenerate);
        assertArrayEquals(new double[] { 0, 0, 0, 2, 2, 2 },
                UnusableRays.withoutZeroLength(3, src, dst, src, degenerate), 0.0);
        assertArrayEquals(new double[] { 1, 1, 1, 3, 3, 3 },
                UnusableRays.withoutZeroLength(3, src, dst, dst, degenerate), 0.0);
    }
}
