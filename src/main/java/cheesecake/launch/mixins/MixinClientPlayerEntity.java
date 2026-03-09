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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Brady
 * @since 8/1/2018
 */
@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreUpdate(CallbackInfo ci) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer((ClientPlayerEntity) (Object) this);
        if (cheesecake != null) {
            cheesecake.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
        }
    }

    @Redirect(method = "tickMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerAbilities;allowFlying:Z"))
    private boolean isAllowFlying(PlayerAbilities capabilities) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer((ClientPlayerEntity) (Object) this);
        if (cheesecake == null) {
            return capabilities.allowFlying;
        }
        return !cheesecake.getPathingBehavior().isPathing() && capabilities.allowFlying;
    }

    @Inject(method = "tickRiding", at = @At(value = "HEAD"))
    private void updateRidden(CallbackInfo cb) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer((ClientPlayerEntity) (Object) this);
        if (cheesecake != null) {
            ((LookBehavior) cheesecake.getLookBehavior()).pig();
        }
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;checkGliding()Z"))
    private boolean tryToStartFallFlying(final ClientPlayerEntity instance) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getCheesecakeForPlayer(instance);
        if (cheesecake != null && cheesecake.getPathingBehavior().isPathing()) {
            return false;
        }
        return instance.checkGliding();
    }
}
