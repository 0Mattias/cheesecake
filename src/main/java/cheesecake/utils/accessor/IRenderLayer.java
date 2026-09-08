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

package cheesecake.utils.accessor;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

/**
 * Exposes {@code RenderLayer.of(String, RenderSetup)}, which is not public, so that we can build our own
 * line render layers instead of poking raw GL state around vanilla draw calls.
 */
public interface IRenderLayer {

    RenderLayer cheesecake$createRenderLayer(String name, RenderSetup setup);
}
