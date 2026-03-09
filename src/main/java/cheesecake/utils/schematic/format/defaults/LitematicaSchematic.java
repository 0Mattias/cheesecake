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

package cheesecake.utils.schematic.format.defaults;

import cheesecake.api.schematic.CompositeSchematic;
import cheesecake.api.schematic.IStaticSchematic;
import cheesecake.utils.schematic.StaticSchematic;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3i;
import java.util.Collections;
import java.util.Optional;

/**
 * Based on EmersonDove's work
 * <a href="https://github.com/cabaletta/cheesecake/pull/2544">...</a>
 *
 * @author rycbar
 * @since 22.09.2022
 */
@SuppressWarnings({  })
public final class LitematicaSchematic extends CompositeSchematic implements IStaticSchematic {

    /**
     * @param nbtTagCompound a decompressed file stream aka nbt data.
     * @param rotated        if the schematic is rotated by 90°.
     */
    public LitematicaSchematic(NbtCompound nbt) {
        super(0, 0, 0);
        fillInSchematic(nbt);
    }

    /**
     * @return Array of subregion tags.
     */
    private static NbtCompound[] getRegions(NbtCompound nbt) {
        NbtCompound regions = nbt.getCompound("Regions").orElse(new NbtCompound());
        return regions.getKeys().stream()
                .map(key -> regions.getCompound(key).orElse(new NbtCompound()))
                .toArray(NbtCompound[]::new);
    }

    /**
     * Gets both ends from a region box for a given axis and returns the lower one.
     *
     * @param s axis that should be read.
     * @return the lower coord of the requested axis.
     */
    private static int getMinOfSubregion(NbtCompound subReg, String s) {
        int a = subReg.getCompound("Position").orElse(new NbtCompound()).getInt(s).orElse(0);
        int b = subReg.getCompound("Size").orElse(new NbtCompound()).getInt(s).orElse(0);
        return Math.min(a, a + b + 1);
    }

    /**
     * @param blockStatePalette List of all different block types used in the
     *                          schematic.
     * @return Array of BlockStates.
     */
    private static BlockState[] getBlockList(NbtList blockStatePalette) {
        BlockState[] blockList = new BlockState[blockStatePalette.size()];

        for (int i = 0; i < blockStatePalette.size(); i++) {
            Block block = Registries.BLOCK
                    .getEntry(Identifier.of((((NbtCompound) blockStatePalette.get(i)).getString("Name").orElse(""))))
                    .map(ref -> ref.value()).orElseThrow();
            NbtCompound properties = ((NbtCompound) blockStatePalette.get(i)).getCompound("Properties")
                    .orElse(new NbtCompound());

            blockList[i] = getBlockState(block, properties);
        }
        return blockList;
    }

    /**
     * @param block      block.
     * @param properties List of Properties the block has.
     * @return A blockState.
     */
    private static BlockState getBlockState(Block block, NbtCompound properties) {
        BlockState blockState = block.getDefaultState();

        for (Object key : properties.getKeys()) {
            Property<?> property = block.getStateManager().getProperty((String) key);
            String propertyValue = properties.getString((String) key).orElse("");
            if (property != null) {
                blockState = setPropertyValue(blockState, property, propertyValue);
            }
        }
        return blockState;
    }

    /**
     * @author Emerson
     */
    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property,
            String value) {
        Optional<T> parsed = property.parse(value);
        if (parsed.isPresent()) {
            return state.with(property, parsed.get());
        } else {
            throw new IllegalArgumentException("Invalid value for property " + property);
        }
    }

    /**
     * @param amountOfBlockTypes amount of block types in the schematic.
     * @return amount of bits used to encode a block.
     */
    private static int getBitsPerBlock(int amountOfBlockTypes) {
        return (int) Math.max(2, Math.ceil(Math.log(amountOfBlockTypes) / Math.log(2)));
    }

    /**
     * Calculates the volume of the subregion. As size can be a negative value we
     * take the absolute value of the
     * multiplication as the volume still holds a positive amount of blocks.
     *
     * @return the volume of the subregion.
     */
    private static long getVolume(NbtCompound subReg) {
        NbtCompound size = subReg.getCompound("Size").orElse(new NbtCompound());
        return Math.abs(size.getInt("x").orElse(0) * size.getInt("y").orElse(0) * size.getInt("z").orElse(0));
    }

    /**
     * @param s axis.
     * @return the lowest coordinate of that axis of the schematic.
     */
    private static int getMinOfSchematic(NbtCompound nbt, String s) {
        int n = Integer.MAX_VALUE;
        for (NbtCompound subReg : getRegions(nbt)) {
            n = Math.min(n, getMinOfSubregion(subReg, s));
        }
        return n;
    }

    /**
     * reads the file data.
     */
    private void fillInSchematic(NbtCompound nbt) {
        Vec3i offsetMinCorner = new Vec3i(getMinOfSchematic(nbt, "x"), getMinOfSchematic(nbt, "y"),
                getMinOfSchematic(nbt, "z"));
        for (NbtCompound subReg : getRegions(nbt)) {
            NbtList usedBlockTypes = subReg.getList("BlockStatePalette").orElse(new NbtList());
            BlockState[] blockList = getBlockList(usedBlockTypes);

            int bitsPerBlock = getBitsPerBlock(usedBlockTypes.size());
            long regionVolume = getVolume(subReg);
            long[] blockStateArray = subReg.getLongArray("BlockStates").orElse(new long[0]);

            LitematicaBitArray bitArray = new LitematicaBitArray(bitsPerBlock, regionVolume, blockStateArray);
            writeSubregionIntoSchematic(subReg, offsetMinCorner, blockList, bitArray);
        }
    }

    /**
     * Writes the file data in to the IBlockstate array.
     *
     * @param blockList list with the different block types used in the schematic.
     * @param bitArray  bit array that holds the placement pattern.
     */
    private void writeSubregionIntoSchematic(NbtCompound subReg, Vec3i offsetMinCorner, BlockState[] blockList,
            LitematicaBitArray bitArray) {
        int offsetX = getMinOfSubregion(subReg, "x") - offsetMinCorner.getX();
        int offsetY = getMinOfSubregion(subReg, "y") - offsetMinCorner.getY();
        int offsetZ = getMinOfSubregion(subReg, "z") - offsetMinCorner.getZ();
        NbtCompound size = subReg.getCompound("Size").orElse(new NbtCompound());
        int sizeX = Math.abs(size.getInt("x").orElse(0));
        int sizeY = Math.abs(size.getInt("y").orElse(0));
        int sizeZ = Math.abs(size.getInt("z").orElse(0));
        BlockState[][][] states = new BlockState[sizeX][sizeZ][sizeY];
        int index = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    states[x][z][y] = blockList[bitArray.getAt(index)];
                    index++;
                }
            }
        }
        this.put(new StaticSchematic(states), offsetX, offsetY, offsetZ);
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        return desiredState(x, y, z, null, Collections.emptyList());
    }

    /**
     * @author maruohon
     *         Class from the Litematica mod by maruohon
     *         Usage under LGPLv3 with the permission of the author.
     *         <a href="https://github.com/maruohon/litematica">...</a>
     */
    private static class LitematicaBitArray {
        /**
         * The long array that is used to store the data for this BitArray.
         */
        private final long[] longArray;
        /**
         * Number of bits a single entry takes up
         */
        private final int bitsPerEntry;
        /**
         * The maximum value for a single entry. This also works as a bitmask for a
         * single entry.
         * For instance, if bitsPerEntry were 5, this value would be 31 (ie,
         * {@code 0b00011111}).
         */
        private final long maxEntryValue;
        /**
         * Number of entries in this array (<b>not</b> the length of the long array that
         * internally backs this array)
         */
        private final long arraySize;

        public LitematicaBitArray(int bitsPerEntryIn, long arraySizeIn, @Nullable long[] longArrayIn) {
            Validate.inclusiveBetween(1L, 32L, bitsPerEntryIn);
            this.arraySize = arraySizeIn;
            this.bitsPerEntry = bitsPerEntryIn;
            this.maxEntryValue = (1L << bitsPerEntryIn) - 1L;

            if (longArrayIn != null) {
                this.longArray = longArrayIn;
            } else {
                this.longArray = new long[(int) (roundUp(arraySizeIn * (long) bitsPerEntryIn, 64L) / 64L)];
            }
        }

        public static long roundUp(long number, long interval) {
            int sign = 1;
            if (interval == 0) {
                return 0;
            } else if (number == 0) {
                return interval;
            } else {
                if (number < 0) {
                    sign = -1;
                }

                long i = number % (interval * sign);
                return i == 0 ? number : number + (interval * sign) - i;
            }
        }

        public int getAt(long index) {
            Validate.inclusiveBetween(0L, this.arraySize - 1L, index);
            long startOffset = index * (long) this.bitsPerEntry;
            int startArrIndex = (int) (startOffset >> 6); // startOffset / 64
            int endArrIndex = (int) (((index + 1L) * (long) this.bitsPerEntry - 1L) >> 6);
            int startBitOffset = (int) (startOffset & 0x3F); // startOffset % 64

            if (startArrIndex == endArrIndex) {
                return (int) (this.longArray[startArrIndex] >>> startBitOffset & this.maxEntryValue);
            } else {
                int endOffset = 64 - startBitOffset;
                return (int) ((this.longArray[startArrIndex] >>> startBitOffset
                        | this.longArray[endArrIndex] << endOffset) & this.maxEntryValue);
            }
        }

        // public long size() {
        // return this.arraySize;
        // }
    }
}