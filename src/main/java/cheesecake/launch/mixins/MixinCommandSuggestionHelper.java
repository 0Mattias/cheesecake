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
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;

/**
 * @author Brady
 * @since 10/9/2019
 */
@Mixin(CommandSuggestions.class)
@SuppressWarnings({ "rawtypes" })
public class MixinCommandSuggestionHelper {

    @Shadow
    @Final
    EditBox input;

    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;

    @Shadow
    private ParseResults currentParse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private CommandSuggestions.SuggestionsList suggestions;

    @Shadow
    boolean keepSuggestions;

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void preUpdateSuggestion(CallbackInfo ci) {
        // Anything that is present in the chatField text before the cursor position
        String prefix = this.input.getValue().substring(0,
                Math.min(this.input.getValue().length(), this.input.getCursorPosition()));

        TabCompleteEvent event = new TabCompleteEvent(prefix);
        CheesecakeAPI.getProvider().getPrimaryCheesecake().getGameEventHandler().onPreTabComplete(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.completions != null) {
            ci.cancel();

            this.currentParse = null; // stop coloring

            if (this.keepSuggestions) { // Supress pendingSuggestions update when cycling pendingSuggestions.
                return;
            }

            this.input.setSuggestion(null); // clear old pendingSuggestions
            this.pendingSuggestions = null;
            // TODO: Support populating the command usage
            this.commandUsage.clear();

            if (event.completions.length == 0) {
                this.suggestions = null;
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
            ((CommandSuggestions) (Object) this).showSuggestions(true); // actually populate the pendingSuggestions list from the
                                                             // pendingSuggestions future
        }
    }
}
