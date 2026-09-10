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

package cheesecake.api.utils;

import cheesecake.api.CheesecakeAPI;
import cheesecake.api.Settings;
import java.util.Arrays;
import java.util.Calendar;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * An ease-of-access interface to provide the {@link Minecraft} game instance,
 * chat and console logging mechanisms, and the Cheesecake chat prefix.
 *
 * @author Brady
 * @since 8/1/2018
 */
public interface Helper {

    /**
     * Instance of {@link Helper}. Used for static-context reference.
     */
    Helper HELPER = new Helper() {};

    /**
     * The main game instance returned by {@link Minecraft#getInstance()}.
     * Deprecated since {@link IPlayerContext#minecraft()} should be used instead (In the majority of cases).
     */
    @Deprecated
    Minecraft mc = Minecraft.getInstance();

    /**
     * The tag to assign to chat messages when {@link Settings#useMessageTag} is {@code true}.
     */
    GuiMessageTag MESSAGE_TAG = new GuiMessageTag(0xFF55FF, null, Component.literal("Cheesecake message."), "Cheesecake");

    static Component getPrefix() {
        // Inner text component
        final Calendar now = Calendar.getInstance();
        final boolean xd = now.get(Calendar.MONTH) == Calendar.APRIL && now.get(Calendar.DAY_OF_MONTH) <= 3;
        MutableComponent cheesecake = Component.literal(xd ? "Baritoe" : CheesecakeAPI.getSettings().shortCheesecakePrefix.value ? "B" : "Cheesecake");
        cheesecake.setStyle(cheesecake.getStyle().withColor(ChatFormatting.LIGHT_PURPLE));

        // Outer brackets
        MutableComponent prefix = Component.literal("");
        prefix.setStyle(cheesecake.getStyle().withColor(ChatFormatting.DARK_PURPLE));
        prefix.append("[");
        prefix.append(cheesecake);
        prefix.append("]");

        return prefix;
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param title   The title to display in the popup
     * @param message The message to display in the popup
     */
    default void logToast(Component title, Component message) {
        Minecraft.getInstance().execute(() -> CheesecakeAPI.getSettings().toaster.value.accept(title, message));
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param title   The title to display in the popup
     * @param message The message to display in the popup
     */
    default void logToast(String title, String message) {
        logToast(Component.literal(title), Component.literal(message));
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param message The message to display in the popup
     */
    default void logToast(String message) {
        logToast(Helper.getPrefix(), Component.literal(message));
    }

    /**
     * Send a message as a desktop notification
     *
     * @param message The message to display in the notification
     */
    default void logNotification(String message) {
        logNotification(message, false);
    }

    /**
     * Send a message as a desktop notification
     *
     * @param message The message to display in the notification
     * @param error   Whether to log as an error
     */
    default void logNotification(String message, boolean error) {
        if (CheesecakeAPI.getSettings().desktopNotifications.value) {
            logNotificationDirect(message, error);
        }
    }

    /**
     * Send a message as a desktop notification regardless of desktopNotifications
     * (should only be used for critically important messages)
     *
     * @param message The message to display in the notification
     */
    default void logNotificationDirect(String message) {
        logNotificationDirect(message, false);
    }

    /**
     * Send a message as a desktop notification regardless of desktopNotifications
     * (should only be used for critically important messages)
     *
     * @param message The message to display in the notification
     * @param error   Whether to log as an error
     */
    default void logNotificationDirect(String message, boolean error) {
        Minecraft.getInstance().execute(() -> CheesecakeAPI.getSettings().notifier.value.accept(message, error));
    }

    /**
     * Send a message to chat only if chatDebug is on
     *
     * @param message The message to display in chat
     */
    default void logDebug(String message) {
        if (!CheesecakeAPI.getSettings().chatDebug.value) {
            //System.out.println("Suppressed debug message:");
            //System.out.println(message);
            return;
        }
        // We won't log debug chat into toasts
        // Because only a madman would want that extreme spam -_-
        logDirect(message, false);
    }

    /**
     * Send components to chat with the [Cheesecake] prefix
     *
     * @param logAsToast Whether to log as a toast notification
     * @param components The components to send
     */
    default void logDirect(boolean logAsToast, Component... components) {
        MutableComponent component = Component.literal("");
        if (!logAsToast && !CheesecakeAPI.getSettings().useMessageTag.value) {
            component.append(getPrefix());
            component.append(Component.literal(" "));
        }
        Arrays.asList(components).forEach(component::append);
        if (logAsToast) {
            logToast(getPrefix(), component);
        } else {
            Minecraft.getInstance().execute(() -> CheesecakeAPI.getSettings().logger.value.accept(component));
        }
    }

    /**
     * Send components to chat with the [Cheesecake] prefix
     *
     * @param components The components to send
     */
    default void logDirect(Component... components) {
        logDirect(CheesecakeAPI.getSettings().logAsToast.value, components);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message    The message to display in chat
     * @param color      The color to print that message in
     * @param logAsToast Whether to log as a toast notification
     */
    default void logDirect(String message, ChatFormatting color, boolean logAsToast) {
        Stream.of(message.split("\n")).forEach(line -> {
            MutableComponent component = Component.literal(line.replace("\t", "    "));
            component.setStyle(component.getStyle().withColor(color));
            logDirect(logAsToast, component);
        });
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     * @param color   The color to print that message in
     */
    default void logDirect(String message, ChatFormatting color) {
        logDirect(message, color, CheesecakeAPI.getSettings().logAsToast.value);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message    The message to display in chat
     * @param logAsToast Whether to log as a toast notification
     */
    default void logDirect(String message, boolean logAsToast) {
        logDirect(message, ChatFormatting.GRAY, logAsToast);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     */
    default void logDirect(String message) {
        logDirect(message, CheesecakeAPI.getSettings().logAsToast.value);
    }

    default void logUnhandledException(final Throwable exception) {
        HELPER.logDirect("An unhandled exception occurred. " +
                        "The error is in your game's log, please report this at https://github.com/cabaletta/cheesecake/issues",
                ChatFormatting.RED);
        exception.printStackTrace();
    }
}
