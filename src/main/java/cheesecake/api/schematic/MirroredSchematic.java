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

package cheesecake.api.schematic;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockMirror;

public class MirroredSchematic implements ISchematic {

    private final ISchematic schematic;
    private final BlockMirror mirror;

    public MirroredSchematic(ISchematic schematic, BlockMirror mirror) {
        this.schematic = schematic;
        this.mirror = mirror;
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        return schematic.inSchematic(
                mirrorX(x, widthX(), mirror),
                y,
                mirrorZ(z, lengthZ(), mirror),
                mirror(currentState, mirror)
        );
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return mirror(schematic.desiredState(
                mirrorX(x, widthX(), mirror),
                y,
                mirrorZ(z, lengthZ(), mirror),
                mirror(current, mirror),
                mirror(approxPlaceable, mirror)
        ), mirror);
    }

    @Override
    public void reset() {
        schematic.reset();
    }

    @Override
    public int widthX() {
        return schematic.widthX();
    }

    @Override
    public int heightY() {
        return schematic.heightY();
    }

    @Override
    public int lengthZ() {
        return schematic.lengthZ();
    }

    private static int mirrorX(int x, int sizeX, BlockMirror mirror) {
        switch (mirror) {
            case NONE:
            case LEFT_RIGHT:
                return x;
            case FRONT_BACK:
                return sizeX - x - 1;
        }
        throw new IllegalArgumentException("Unknown mirror");
    }

    private static int mirrorZ(int z, int sizeZ, BlockMirror mirror) {
        switch (mirror) {
            case NONE:
            case FRONT_BACK:
                return z;
            case LEFT_RIGHT:
                return sizeZ - z - 1;
        }
        throw new IllegalArgumentException("Unknown mirror");
    }

    private static BlockState mirror(BlockState state, BlockMirror mirror) {
        if (state == null) {
            return null;
        }
        return state.mirror(mirror);
    }

    private static List<BlockState> mirror(List<BlockState> states, BlockMirror mirror) {
        if (states == null) {
            return null;
        }
        return states.stream()
                .map(s -> mirror(s, mirror))
                .collect(Collectors.toList());
    }
}
