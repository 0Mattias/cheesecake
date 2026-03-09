package cheesecake.launch.mixins;

import cheesecake.api.CheesecakeAPI;
import cheesecake.api.ICheesecake;
import cheesecake.api.event.events.SprintStateEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z"))
    private boolean isKeyDown(KeyBinding keyBinding) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getPrimaryCheesecake();
        if (cheesecake == null) {
            return keyBinding.isPressed();
        }

        // Only handle sprint key overrides
        if (keyBinding == MinecraftClient.getInstance().options.sprintKey) {
            SprintStateEvent event = new SprintStateEvent();
            cheesecake.getGameEventHandler().onPlayerSprintState(event);
            if (event.getState() != null) {
                return event.getState();
            }
        }

        return keyBinding.isPressed();
    }
}
