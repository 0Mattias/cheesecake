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

package cheesecake.api.schematic.mask.shape;

import cheesecake.api.schematic.mask.AbstractMask;
import cheesecake.api.schematic.mask.StaticMask;
import net.minecraft.util.math.Direction;

/**
 * @author Brady
 */
public final class CylinderMask extends AbstractMask implements StaticMask {

    private final double centerA;
    private final double centerB;
    private final double radiusSqA;
    private final double radiusSqB;
    private final boolean filled;
    private final Direction.Axis alignment;

    public CylinderMask(int widthX, int heightY, int lengthZ, boolean filled, Direction.Axis alignment) {
        super(widthX, heightY, lengthZ);
        this.centerA = CylinderMask.getA(widthX, heightY, alignment) / 2.0;
        this.centerB = CylinderMask.getB(heightY, lengthZ, alignment) / 2.0;
        this.radiusSqA = (this.centerA - 1) * (this.centerA - 1);
        this.radiusSqB = (this.centerB - 1) * (this.centerB - 1);
        this.filled = filled;
        this.alignment = alignment;
    }

    @Override
    public boolean partOfMask(int x, int y, int z) {
        double da = Math.abs((CylinderMask.getA(x, y, this.alignment) + 0.5) - this.centerA);
        double db = Math.abs((CylinderMask.getB(y, z, this.alignment) + 0.5) - this.centerB);
        if (this.outside(da, db)) {
            return false;
        }
        return this.filled
                || this.outside(da + 1, db)
                || this.outside(da, db + 1);
    }

    private boolean outside(double da, double db) {
        return da * da / this.radiusSqA + db * db / this.radiusSqB > 1;
    }

    private static int getA(int x, int y, Direction.Axis alignment) {
        return alignment == Direction.Axis.X ? y : x;
    }

    private static int getB(int y, int z, Direction.Axis alignment) {
        return alignment == Direction.Axis.Z ? y : z;
    }
}
