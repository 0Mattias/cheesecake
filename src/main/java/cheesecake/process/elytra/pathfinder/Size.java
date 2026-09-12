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

package cheesecake.process.elytra.pathfinder;

/** The edge length of a cube in the octree, as a power of two. */
enum Size {
    X1, X2, X4, X8, X16;

    private static final Size[] VALUES = values();

    int shift() {
        return ordinal();
    }

    int width() {
        return 1 << ordinal();
    }

    Size smaller() {
        return VALUES[ordinal() - 1];
    }

    Size larger() {
        return VALUES[ordinal() + 1];
    }
}
