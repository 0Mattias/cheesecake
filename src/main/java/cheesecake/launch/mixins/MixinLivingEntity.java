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
import cheesecake.api.event.events.RotationMoveEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * @author Brady
 * @since 9/10/2018
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    /**
     * Event called to override the movement direction when jumping
     */
    @Unique
    private RotationMoveEvent jumpRotationEvent;

    @Unique
    private RotationMoveEvent elytraRotationEvent;

    private MixinLivingEntity(EntityType<?> entityTypeIn, World worldIn) {
        super(entityTypeIn, worldIn);
    }

    @Inject(method = "jump", at = @At("HEAD"))
    private void preMoveRelative(CallbackInfo ci) {
        this.getCheesecake().ifPresent(cheesecake -> {
            this.jumpRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.JUMP, this.getYaw(), this.getPitch());
            cheesecake.getGameEventHandler().onPlayerRotationMove(this.jumpRotationEvent);
        });
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "net/minecraft/entity/LivingEntity.getYaw()F"))
    private float overrideYaw(LivingEntity self) {
        if (self instanceof ClientPlayerEntity
                && CheesecakeAPI.getProvider().getCheesecakeForPlayer((ClientPlayerEntity) (Object) this) != null) {
            return this.jumpRotationEvent.getYaw();
        }
        return self.getYaw();
    }

    @Inject(method = "travelGliding", at = @At(value = "INVOKE", target = "net/minecraft/entity/LivingEntity.calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private void onPreElytraMove(Vec3d direction, CallbackInfo ci) {
        this.getCheesecake().ifPresent(cheesecake -> {
            this.elytraRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, this.getYaw(),
                    this.getPitch());
            cheesecake.getGameEventHandler().onPlayerRotationMove(this.elytraRotationEvent);
            this.setYaw(this.elytraRotationEvent.getYaw());
            this.setPitch(this.elytraRotationEvent.getPitch());
        });
    }

    @Inject(method = "travelGliding", at = @At(value = "INVOKE", target = "net/minecraft/entity/LivingEntity.calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", shift = At.Shift.AFTER))
    private void onPostElytraMove(Vec3d direction, CallbackInfo ci) {
        if (this.elytraRotationEvent != null) {
            this.setYaw(this.elytraRotationEvent.getOriginal().getYaw());
            this.setPitch(this.elytraRotationEvent.getOriginal().getPitch());
            this.elytraRotationEvent = null;
        }
    }

    @Unique
    private Optional<ICheesecake> getCheesecake() {
        // noinspection ConstantConditions
        if (ClientPlayerEntity.class.isInstance(this)) {
            return Optional
                    .ofNullable(CheesecakeAPI.getProvider().getCheesecakeForPlayer((ClientPlayerEntity) (Object) this));
        } else {
            return Optional.empty();
        }
    }
}
