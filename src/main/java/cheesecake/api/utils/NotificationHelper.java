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

import org.apache.commons.lang3.SystemUtils;

// import java.awt.Color;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;

/**
 * This class is not called from the main game thread.
 * Do not refer to any Minecraft classes, it wouldn't be thread safe.
 *
 * @author aUniqueUser
 */
public class NotificationHelper {

    private static TrayIcon trayIcon;

    public static void notify(String text, boolean error) {
        if (SystemUtils.IS_OS_WINDOWS) {
            windows(text, error);
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            mac(text);
        } else if (SystemUtils.IS_OS_LINUX) {
            linux(text);
        }
    }

    private static void windows(String text, boolean error) {
        if (SystemTray.isSupported()) {
            try {
                if (trayIcon == null) {
                    SystemTray tray = SystemTray.getSystemTray();
                    Image image = Toolkit.getDefaultToolkit().createImage("");

                    trayIcon = new TrayIcon(image, "Cheesecake");
                    trayIcon.setImageAutoSize(true);
                    trayIcon.setToolTip("Cheesecake");
                    tray.add(trayIcon);
                }

                trayIcon.displayMessage("Cheesecake", text,
                        error ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("SystemTray is not supported");
        }
    }

    private static void mac(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("osascript", "-e", "display notification \"" + text + "\" with title \"Cheesecake\"");
        try {
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // The only way to display notifications on linux is to use the java-gnome
    // library,
    // or send notify-send to shell with a ProcessBuilder. Unfortunately the
    // java-gnome
    // library is licenced under the GPL, see
    // (https://en.wikipedia.org/wiki/Java-gnome)
    private static void linux(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("notify-send", "-a", "Cheesecake", text);
        try {
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
