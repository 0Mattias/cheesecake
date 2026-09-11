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
 * Handed a ray whose start is its end, nether-pathfinder prints "raytrace whiffed" and calls
 * exit(696969), ending the game. A shell reports that status as 137, which is also what a process
 * killed by SIGKILL reports, so it reads as an out-of-memory kill and is nothing of the kind.
 * These cover picking those segments out before they reach the library; the library itself cannot
 * be exercised from a test, because a miss would take the test runner with it.
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
        assertEquals(1, UnusableRays.countNaN(1, src, new double[] { Double.NaN, 64.0, -2.5 }));
        assertEquals(1, UnusableRays.countNaN(1, src, new double[] { 1.5, Double.NaN, -2.5 }));
        assertEquals(1, UnusableRays.countNaN(1, new double[] { 1.5, Double.NaN, -2.5 }, src));
        assertEquals(0, UnusableRays.countNaN(1, src, new double[] { 1.5, 65.0, -2.5 }));
    }

    /** Infinities are accepted by the library, so they must not be dropped. */
    @Test
    public void testInfinityIsNotTreatedAsUnusable() {
        final double[] src = { 1.5, 64.0, -2.5 };
        final double[] dst = { Double.POSITIVE_INFINITY, 64.0, -2.5 };
        assertEquals(0, UnusableRays.countNaN(1, src, dst));
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
