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

package cheesecake.command;

import cheesecake.Cheesecake;
import cheesecake.api.CheesecakeAPI;
import cheesecake.api.Settings;
import cheesecake.api.command.argument.ICommandArgument;
import cheesecake.api.command.exception.CommandNotEnoughArgumentsException;
import cheesecake.api.command.exception.CommandNotFoundException;
import cheesecake.api.command.helpers.TabCompleteHelper;
import cheesecake.api.command.manager.ICommandManager;
import cheesecake.api.event.events.ChatEvent;
import cheesecake.api.event.events.TabCompleteEvent;
import cheesecake.api.utils.Helper;
import cheesecake.api.utils.SettingsUtil;
import cheesecake.behavior.Behavior;
import cheesecake.command.argument.ArgConsumer;
import cheesecake.command.argument.CommandArguments;
import cheesecake.command.manager.CommandManager;
import cheesecake.utils.accessor.IGuiScreen;
// import net.minecraft.text.*;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static cheesecake.api.command.ICheesecakeChatControl.FORCE_COMMAND_PREFIX;

@SuppressWarnings({"rawtypes"})
public class ExampleCheesecakeControl extends Behavior implements Helper {

    private static final Settings settings = CheesecakeAPI.getSettings();
    private final ICommandManager manager;

    public ExampleCheesecakeControl(Cheesecake cheesecake) {
        super(cheesecake);
        this.manager = cheesecake.getCommandManager();
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        String msg = event.getMessage();
        String prefix = settings.prefix.value;
        boolean forceRun = msg.startsWith(FORCE_COMMAND_PREFIX);
        if ((settings.prefixControl.value && msg.startsWith(prefix)) || forceRun) {
            event.cancel();
            String commandStr = msg.substring(forceRun ? FORCE_COMMAND_PREFIX.length() : prefix.length());
            if (!runCommand(commandStr) && !commandStr.trim().isEmpty()) {
                new CommandNotFoundException(CommandManager.expand(commandStr).getLeft()).handle(null, null);
            }
        } else if ((settings.chatControl.value || settings.chatControlAnyway.value) && runCommand(msg)) {
            event.cancel();
        }
    }

    private void logRanCommand(String command, String rest) {
        if (settings.echoCommands.value) {
            String msg = command + rest;
            String toDisplay = settings.censorRanCommands.value ? command + " ..." : msg;
            MutableText component = Text.literal(String.format("> %s", toDisplay));
            component.setStyle(component.getStyle()
                    .withColor(Formatting.WHITE)
                    .withHoverEvent(new HoverEvent.ShowText(
                            Text.literal("Click to rerun command")))
                    .withClickEvent(new ClickEvent.RunCommand(
                            FORCE_COMMAND_PREFIX + msg + " ")))
                    .append(" ");
            logDirect(component);
        }
    }

    public boolean runCommand(String msg) {
        if (msg.trim().equalsIgnoreCase("damn")) {
            logDirect("daniel");
            return false;
        } else if (msg.trim().equalsIgnoreCase("orderpizza")) {
            try {
                ((IGuiScreen) ctx.minecraft().currentScreen)
                        .openLinkInvoker(new URI("https://www.dominos.com/en/pages/order/"));
            } catch (NullPointerException | URISyntaxException ignored) {
            }
            return false;
        }
        if (msg.isEmpty()) {
            return this.runCommand("help");
        }
        Pair<String, List<ICommandArgument>> pair = CommandManager.expand(msg);
        String command = pair.getLeft();
        String rest = msg.substring(pair.getLeft().length());
        ArgConsumer argc = new ArgConsumer(this.manager, pair.getRight());
        if (!argc.hasAny()) {
            Settings.Setting setting = settings.byLowerName.get(command.toLowerCase(Locale.US));
            if (setting != null) {
                logRanCommand(command, rest);
                if (setting.getValueClass() == Boolean.class) {
                    this.manager.execute(String.format("set toggle %s", setting.getName()));
                } else {
                    this.manager.execute(String.format("set %s", setting.getName()));
                }
                return true;
            }
        } else if (argc.hasExactlyOne()) {
            for (Settings.Setting setting : settings.allSettings) {
                if (setting.isJavaOnly()) {
                    continue;
                }
                if (setting.getName().equalsIgnoreCase(pair.getLeft())) {
                    logRanCommand(command, rest);
                    try {
                        this.manager.execute(String.format("set %s %s", setting.getName(), argc.getString()));
                    } catch (CommandNotEnoughArgumentsException ignored) {
                    } // The operation is safe
                    return true;
                }
            }
        }

        // If the command exists, then handle echoing the input
        if (this.manager.getCommand(pair.getLeft()) != null) {
            logRanCommand(command, rest);
        }

        return this.manager.execute(pair);
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        if (!settings.prefixControl.value) {
            return;
        }
        String prefix = event.prefix;
        String commandPrefix = settings.prefix.value;
        if (!prefix.startsWith(commandPrefix)) {
            return;
        }
        String msg = prefix.substring(commandPrefix.length());
        List<ICommandArgument> args = CommandArguments.from(msg, true);
        Stream<String> stream = tabComplete(msg);
        if (args.size() == 1) {
            stream = stream.map(x -> commandPrefix + x);
        }
        event.completions = stream.toArray(String[]::new);
    }

    public Stream<String> tabComplete(String msg) {
        try {
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            ArgConsumer argc = new ArgConsumer(this.manager, args);
            if (argc.hasAtMost(2)) {
                if (argc.hasExactly(1)) {
                    return new TabCompleteHelper()
                            .addCommands(this.manager)
                            .addSettings()
                            .filterPrefix(argc.getString())
                            .stream();
                }
                Settings.Setting setting = settings.byLowerName.get(argc.getString().toLowerCase(Locale.US));
                if (setting != null && !setting.isJavaOnly()) {
                    if (setting.getValueClass() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.value) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(argc.getString()).stream();
                    } else {
                        return Stream.of(SettingsUtil.settingValueToString(setting));
                    }
                }
            }
            return this.manager.tabComplete(msg);
        } catch (CommandNotEnoughArgumentsException ignored) { // Shouldn't happen, the operation is safe
            return Stream.empty();
        }
    }
}
