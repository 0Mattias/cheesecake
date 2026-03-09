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

package cheesecake.launch.mixins;

import cheesecake.utils.accessor.IEntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderManager.class)
public class MixinEntityRenderManager implements IEntityRenderManager {

    @Override
    public double renderPosX() {
        return ((EntityRenderManager) (Object) this).camera.getCameraPos().x;
    }

    @Override
    public double renderPosY() {
        return ((EntityRenderManager) (Object) this).camera.getCameraPos().y;
    }

    @Override
    public double renderPosZ() {
        return ((EntityRenderManager) (Object) this).camera.getCameraPos().z;
    }
}
