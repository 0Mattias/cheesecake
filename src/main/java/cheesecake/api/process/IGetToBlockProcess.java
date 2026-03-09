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

package cheesecake.api.process;

import cheesecake.api.utils.BlockOptionalMeta;
import net.minecraft.block.Block;

/**
 * but it rescans the world every once in a while so it doesn't get fooled by its cache
 */
public interface IGetToBlockProcess extends ICheesecakeProcess {

    void getToBlock(BlockOptionalMeta block);

    default void getToBlock(Block block) {
        getToBlock(new BlockOptionalMeta(block));
    }

    boolean blacklistClosest();
}
