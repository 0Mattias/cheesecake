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
import cheesecake.api.event.events.ChatEvent;
import cheesecake.utils.accessor.IGuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.net.URI;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;

import static cheesecake.api.command.ICheesecakeChatControl.FORCE_COMMAND_PREFIX;

@Mixin(Screen.class)
public abstract class MixinScreen implements IGuiScreen {

    @Override
    public void openLinkInvoker(URI url) {
        net.minecraft.util.Util.getOperatingSystem().open(url);
    }

    @Inject(method = "handleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void handleCustomClickEvent(ClickEvent clickEvent, net.minecraft.client.MinecraftClient client,
            Screen screen, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (clickEvent == null) {
            return;
        }
        if (!(clickEvent instanceof ClickEvent.RunCommand runCommand)) {
            return;
        }
        String command = runCommand.command();
        if (command == null || !command.startsWith(FORCE_COMMAND_PREFIX)) {
            return;
        }
        ICheesecake cheesecake = CheesecakeAPI.getProvider().getPrimaryCheesecake();
        if (cheesecake != null) {
            cheesecake.getGameEventHandler().onSendChatMessage(new ChatEvent(command));
        }
        ci.cancel();
    }
}
