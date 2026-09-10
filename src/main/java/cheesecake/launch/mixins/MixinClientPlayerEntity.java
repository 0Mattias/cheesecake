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

import cheesecake.api.CheesecakeAPI;
import cheesecake.api.ICheesecake;
import cheesecake.api.event.events.PlayerUpdateEvent;
import cheesecake.api.event.events.type.EventState;
import cheesecake.behavior.LookBehavior;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Brady
 * @since 8/1/2018
 */
@Mixin(LocalPlayer.class)
public class MixinClientPlayerEntity {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreUpdate(CallbackInfo ci) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer((LocalPlayer) (Object) this);
        if (cheesecake != null) {
            cheesecake.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
    private boolean isAllowFlying(Abilities capabilities) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer((LocalPlayer) (Object) this);
        if (cheesecake == null) {
            return capabilities.mayfly;
        }
        return !cheesecake.getPathingBehavior().isPathing() && capabilities.mayfly;
    }

    @Inject(method = "rideTick", at = @At(value = "HEAD"))
    private void updateRidden(CallbackInfo cb) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer((LocalPlayer) (Object) this);
        if (cheesecake != null) {
            ((LookBehavior) cheesecake.getLookBehavior()).pig();
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
    private boolean tryToStartFallFlying(final LocalPlayer instance) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer(instance);
        if (cheesecake != null && cheesecake.getPathingBehavior().isPathing()) {
            return false;
        }
        return instance.tryToStartFallFlying();
    }
}
