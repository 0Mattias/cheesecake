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

import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Exposes the private vanilla pipeline snippets so our render layers inherit the vanilla shaders and
 * vertex formats instead of guessing at them, and the private registration method so our pipelines
 * sit in the same registry as vanilla's.
 */
public interface IRenderPipelines {

    RenderPipeline.Snippet cheesecake$getLinesSnippet();

    RenderPipeline.Snippet cheesecake$getMatricesFogSnippet();

    RenderPipeline cheesecake$registerPipeline(RenderPipeline pipeline);
}
