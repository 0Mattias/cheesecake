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
import cheesecake.api.event.events.TabCompleteEvent;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;

/**
 * @author Brady
 * @since 10/9/2019
 */
@Mixin(ChatInputSuggestor.class)
@SuppressWarnings({ "rawtypes" })
public class MixinCommandSuggestionHelper {

    @Shadow
    @Final
    TextFieldWidget textField;

    @Shadow
    @Final
    private List<OrderedText> messages;

    @Shadow
    private ParseResults parse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private ChatInputSuggestor.SuggestionWindow window;

    @Shadow
    boolean completingSuggestions;

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void preUpdateSuggestion(CallbackInfo ci) {
        // Anything that is present in the chatField text before the cursor position
        String prefix = this.textField.getText().substring(0,
                Math.min(this.textField.getText().length(), this.textField.getCursor()));

        TabCompleteEvent event = new TabCompleteEvent(prefix);
        CheesecakeAPI.getProvider().getPrimaryCheesecake().getGameEventHandler().onPreTabComplete(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.completions != null) {
            ci.cancel();

            this.parse = null; // stop coloring

            if (this.completingSuggestions) { // Supress pendingSuggestions update when cycling pendingSuggestions.
                return;
            }

            this.textField.setSuggestion(null); // clear old pendingSuggestions
            this.pendingSuggestions = null;
            // TODO: Support populating the command usage
            this.messages.clear();

            if (event.completions.length == 0) {
                this.window = null;
            } else {
                StringRange range = StringRange.between(prefix.lastIndexOf(" ") + 1, prefix.length()); // if there is no
                                                                                                       // space this
                                                                                                       // starts at 0

                List<Suggestion> suggestionList = Stream.of(event.completions)
                        .map(s -> new Suggestion(range, s))
                        .collect(Collectors.toList());

                Suggestions pendingSuggestions = new Suggestions(range, suggestionList);

                this.pendingSuggestions = new CompletableFuture<>();
                this.pendingSuggestions.complete(pendingSuggestions);
            }
            ((ChatInputSuggestor) (Object) this).show(true); // actually populate the pendingSuggestions list from the
                                                             // pendingSuggestions future
        }
    }
}
