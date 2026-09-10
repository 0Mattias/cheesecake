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

package cheesecake.utils.autotest;

import cheesecake.Cheesecake;
import cheesecake.api.Settings;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.api.utils.IPlayerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the stages of the in-world test share: the mod, the client, everything the mod has said in
 * chat, and the two ways of giving orders. Chat commands go to the mod exactly as a player would
 * type them, and console commands go to the integrated server to build the scenario a stage needs.
 */
public final class AutoTestContext {

    public static final String TAG = "[cheesecake-autotest]";
    /**
     * The seed of the world the test creates. The Nether stages hand it to the elytra pathfinder,
     * which predicts unloaded terrain from it.
     */
    public static final long SEED = -928872506371745L;

    public final Cheesecake cheesecake;
    public final Minecraft mc;
    /**
     * Top block of the obsidian platform that {@link PlatformStage} builds in the sky, where the
     * later stages set up their scenarios.
     */
    public BetterBlockPos platform;

    private final List<String> chat = new ArrayList<>();
    private int chatMark;
    private String stage = "setup";

    public AutoTestContext(Cheesecake cheesecake, Minecraft mc) {
        this.cheesecake = cheesecake;
        this.mc = mc;
        // Record everything the mod says in chat so stages can assert on the messages a player
        // would see. The previous logger still runs. The agent API wraps and later restores whatever
        // logger it finds, so this one survives the socket stage.
        Settings settings = Cheesecake.settings();
        Consumer<Component> previous = settings.logger.value;
        settings.logger.value = message -> {
            String text = message.getString();
            synchronized (this.chat) {
                this.chat.add(text);
            }
            log("chat: " + text);
            previous.accept(message);
        };
    }

    void setStage(String name) {
        this.stage = name;
    }

    public void log(String message) {
        System.out.println(TAG + " " + this.stage + ": " + message);
    }

    public IPlayerContext ctx() {
        return this.cheesecake.getPlayerContext();
    }

    public LocalPlayer player() {
        return this.mc.player;
    }

    public String playerName() {
        return this.mc.player.getName().getString();
    }

    /**
     * Runs a command as the integrated server's console. The future completes on the server thread
     * once the command has run, and exceptionally if the command reported an error.
     */
    public CompletableFuture<Void> serverCommand(String command) {
        IntegratedServer server = this.mc.getSingleplayerServer();
        if (server == null) {
            throw new AutoTestFailure("no integrated server is running");
        }
        log("server command: /" + command);
        return server.submit(() -> {
            List<String> errors = new ArrayList<>();
            CommandSource output = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    String text = message.getString();
                    log("server: " + text);
                    // sendError wraps its message in red; plain feedback carries no colour.
                    TextColor color = message.getStyle().getColor();
                    if (color != null && color.getValue() == TextColor.RED.getValue()) {
                        errors.add(text);
                    }
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(output), command);
            if (!errors.isEmpty()) {
                throw new AutoTestFailure("/" + command + " failed: " + String.join("; ", errors));
            }
        });
    }

    /**
     * Runs one of the mod's chat commands, without the prefix, as if a player had typed it.
     */
    public void chatCommand(String command) {
        log("chat command: #" + command);
        if (!this.cheesecake.getCommandManager().execute(command)) {
            throw new AutoTestFailure("not a command: " + command);
        }
    }

    /**
     * How many of an item the player carries, hotbar and main inventory together.
     */
    public int countItems(Item item) {
        int count = 0;
        for (ItemStack stack : player().getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Forgets the chat so far for the purposes of {@link #saidSinceMark}.
     */
    public void markChat() {
        synchronized (this.chat) {
            this.chatMark = this.chat.size();
        }
    }

    /**
     * Whether the mod has said something containing the fragment since the last {@link #markChat()}.
     */
    public boolean saidSinceMark(String fragment) {
        synchronized (this.chat) {
            for (int i = this.chatMark; i < this.chat.size(); i++) {
                if (this.chat.get(i).contains(fragment)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cancels whatever the mod is doing, like {@code #stop}.
     */
    public void stopEverything() {
        this.cheesecake.getPathingBehavior().cancelEverything();
    }
}
