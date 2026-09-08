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

import net.minecraft.util.math.Box;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests for Box collision volume behavior in elytra flight simulation.
 * Verifies that stretch produces correct swept volumes for all
 * motion vector directions, preventing both tunneling (#5049) and
 * hitbox collapse (#5047).
 */
public class ElytraHitboxTest {

    /**
     * Test swept volume with positive motion vectors.
     * stretch(2, 0, 3) should extend the Box in +X and +Z directions.
     */
    @Test
    public void testPositiveMotionVectors() {
        Box hitbox = new Box(0, 0, 0, 0.6, 1.8, 0.6);
        Box expanded = hitbox.stretch(2, 0, 3);

        // Expanded box should cover start and end positions
        assertTrue("minX should be at or below start", expanded.minX <= hitbox.minX);
        assertTrue("maxX should be at or above end", expanded.maxX >= hitbox.maxX + 2);
        assertTrue("minZ should be at or below start", expanded.minZ <= hitbox.minZ);
        assertTrue("maxZ should be at or above end", expanded.maxZ >= hitbox.maxZ + 3);

        // Y should remain unchanged (zero motion)
        assertTrue("minY should be unchanged", expanded.minY == hitbox.minY);
        assertTrue("maxY should be unchanged", expanded.maxY == hitbox.maxY);

        // Box invariants must hold
        assertTrue("minX < maxX", expanded.minX < expanded.maxX);
        assertTrue("minY < maxY", expanded.minY < expanded.maxY);
        assertTrue("minZ < maxZ", expanded.minZ < expanded.maxZ);
    }

    /**
     * Test swept volume with zero motion on one axis.
     * stretch(2, 0, 0) should only extend X, not Y or Z.
     */
    @Test
    public void testZeroMotionAxis() {
        Box hitbox = new Box(0, 0, 0, 0.6, 1.8, 0.6);
        Box expanded = hitbox.stretch(2, 0, 0);

        // X should extend
        assertTrue("maxX should extend", expanded.maxX >= hitbox.maxX + 2);

        // Y and Z should remain unchanged
        assertTrue("minY unchanged", expanded.minY == hitbox.minY);
        assertTrue("maxY unchanged", expanded.maxY == hitbox.maxY);
        assertTrue("minZ unchanged", expanded.minZ == hitbox.minZ);
        assertTrue("maxZ unchanged", expanded.maxZ == hitbox.maxZ);

        // Box invariants
        assertTrue("minX < maxX", expanded.minX < expanded.maxX);
        assertTrue("minY < maxY", expanded.minY < expanded.maxY);
        assertTrue("minZ < maxZ", expanded.minZ < expanded.maxZ);
    }

    /**
     * Test swept volume with negative motion vectors.
     * stretch(-2, -1, -3) should extend toward negative coordinates
     * without collapsing. This is the regression test for #5047.
     */
    @Test
    public void testNegativeMotionVectors() {
        Box hitbox = new Box(5, 10, 5, 5.6, 11.8, 5.6);
        Box expanded = hitbox.stretch(-2, -1, -3);

        // Should extend toward negative
        assertTrue("minX should decrease", expanded.minX < hitbox.minX);
        assertTrue("minY should decrease", expanded.minY < hitbox.minY);
        assertTrue("minZ should decrease", expanded.minZ < hitbox.minZ);

        // Box invariants MUST hold — this is the critical regression test
        assertTrue("minX < maxX (no collapse)", expanded.minX < expanded.maxX);
        assertTrue("minY < maxY (no collapse)", expanded.minY < expanded.maxY);
        assertTrue("minZ < maxZ (no collapse)", expanded.minZ < expanded.maxZ);
    }

    /**
     * Test swept volume with mixed positive/negative motion.
     * stretch(2, -1, 0) should handle mixed axes correctly.
     */
    @Test
    public void testMixedMotionVectors() {
        Box hitbox = new Box(0, 5, 0, 0.6, 6.8, 0.6);
        Box expanded = hitbox.stretch(2, -1, 0);

        // X extends positive
        assertTrue("maxX extends positive", expanded.maxX >= hitbox.maxX + 2);

        // Y extends negative
        assertTrue("minY extends negative", expanded.minY < hitbox.minY);

        // Z unchanged
        assertTrue("minZ unchanged", expanded.minZ == hitbox.minZ);
        assertTrue("maxZ unchanged", expanded.maxZ == hitbox.maxZ);

        // Box invariants
        assertTrue("minX < maxX", expanded.minX < expanded.maxX);
        assertTrue("minY < maxY", expanded.minY < expanded.maxY);
        assertTrue("minZ < maxZ", expanded.minZ < expanded.maxZ);
    }

    /**
     * Test that stretch with the expand(0.01) safety padding
     * produces valid boxes for all motion directions.
     * This matches the actual usage in ElytraBehavior.simulate().
     */
    @Test
    public void testExpandTowardsWithSafetyPadding() {
        Box hitbox = new Box(0, 0, 0, 0.6, 1.8, 0.6);

        // Positive motion
        Box posMotion = hitbox.stretch(2, 0, 3).expand(0.01);
        assertTrue("Positive: minX < maxX", posMotion.minX < posMotion.maxX);
        assertTrue("Positive: minY < maxY", posMotion.minY < posMotion.maxY);
        assertTrue("Positive: minZ < maxZ", posMotion.minZ < posMotion.maxZ);

        // Negative motion
        Box negMotion = hitbox.stretch(-2, -1, -3).expand(0.01);
        assertTrue("Negative: minX < maxX", negMotion.minX < negMotion.maxX);
        assertTrue("Negative: minY < maxY", negMotion.minY < negMotion.maxY);
        assertTrue("Negative: minZ < maxZ", negMotion.minZ < negMotion.maxZ);

        // Zero motion
        Box zeroMotion = hitbox.stretch(0, 0, 0).expand(0.01);
        assertTrue("Zero: minX < maxX", zeroMotion.minX < zeroMotion.maxX);
        assertTrue("Zero: minY < maxY", zeroMotion.minY < zeroMotion.maxY);
        assertTrue("Zero: minZ < maxZ", zeroMotion.minZ < zeroMotion.maxZ);
    }
}
