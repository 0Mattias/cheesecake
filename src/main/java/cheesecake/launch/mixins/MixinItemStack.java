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

import cheesecake.api.utils.accessor.IItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class MixinItemStack implements IItemStack {

    @Shadow
    @Final
    private Item item;

    @Unique
    private int cheesecakeHash;

    @Shadow
    public abstract int getDamage();

    private void recalculateHash() {
        cheesecakeHash = item == null ? -1 : item.hashCode() + getDamage();
    }

    @Inject(method = "setDamage", at = @At("TAIL"))
    private void onItemDamageSet(CallbackInfo ci) {
        recalculateHash();
    }

    @Override
    public int getCheesecakeHash() {
        // cannot do this in an init mixin because silentlib likes creating new
        // items in getDamageValue, which we call in recalculateHash
        if (cheesecakeHash == 0)
            recalculateHash();
        return cheesecakeHash;
    }
}
