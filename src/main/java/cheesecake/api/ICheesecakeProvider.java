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

package cheesecake.api;

import cheesecake.api.cache.IWorldScanner;
import cheesecake.api.command.ICommand;
import cheesecake.api.command.ICommandSystem;
import cheesecake.api.schematic.ISchematicSystem;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Provides the present {@link ICheesecake} instances, as well as non-cheesecake instance related APIs.
 *
 * @author leijurv
 */
public interface ICheesecakeProvider {

    /**
     * Returns the primary {@link ICheesecake} instance. This instance is persistent, and
     * is represented by the local player that is created by the game itself, not a "bot"
     * player through Cheesecake.
     *
     * @return The primary {@link ICheesecake} instance.
     */
    ICheesecake getPrimaryCheesecake();

    /**
     * Returns all of the active {@link ICheesecake} instances. This includes the local one
     * returned by {@link #getPrimaryCheesecake()}.
     *
     * @return All active {@link ICheesecake} instances.
     * @see #getCheesecakeForPlayer(ClientPlayerEntity)
     */
    List<ICheesecake> getAllCheesecakes();

    /**
     * Provides the {@link ICheesecake} instance for a given {@link ClientPlayerEntity}.
     *
     * @param player The player
     * @return The {@link ICheesecake} instance.
     */
    default ICheesecake getCheesecakeForPlayer(ClientPlayerEntity player) {
        for (ICheesecake cheesecake : this.getAllCheesecakes()) {
            if (Objects.equals(player, cheesecake.getPlayerContext().player())) {
                return cheesecake;
            }
        }
        return null;
    }

    /**
     * Provides the {@link ICheesecake} instance for a given {@link MinecraftClient}.
     *
     * @param minecraft The minecraft
     * @return The {@link ICheesecake} instance.
     */
    default ICheesecake getCheesecakeForMinecraft(MinecraftClient minecraft) {
        for (ICheesecake cheesecake : this.getAllCheesecakes()) {
            if (Objects.equals(minecraft, cheesecake.getPlayerContext().minecraft())) {
                return cheesecake;
            }
        }
        return null;
    }

    /**
     * Provides the {@link ICheesecake} instance for the player with the specified connection.
     *
     * @param connection The connection
     * @return The {@link ICheesecake} instance.
     */
    default ICheesecake getCheesecakeForConnection(ClientPlayNetworkHandler connection) {
        for (ICheesecake cheesecake : this.getAllCheesecakes()) {
            final ClientPlayerEntity player = cheesecake.getPlayerContext().player();
            if (player != null && player.networkHandler == connection) {
                return cheesecake;
            }
        }
        return null;
    }

    /**
     * Creates and registers a new {@link ICheesecake} instance using the specified {@link MinecraftClient}. The existing
     * instance is returned if already registered.
     *
     * @param minecraft The minecraft
     * @return The {@link ICheesecake} instance
     */
    ICheesecake createCheesecake(MinecraftClient minecraft);

    /**
     * Destroys and removes the specified {@link ICheesecake} instance. If the specified instance is the
     * {@link #getPrimaryCheesecake() primary cheesecake}, this operation has no effect and will return {@code false}.
     *
     * @param cheesecake The cheesecake instance to remove
     * @return Whether the cheesecake instance was removed
     */
    boolean destroyCheesecake(ICheesecake cheesecake);

    /**
     * Returns the {@link IWorldScanner} instance. This is not a type returned by
     * {@link ICheesecake} implementation, because it is not linked with {@link ICheesecake}.
     *
     * @return The {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ICommandSystem} instance. This is not bound to a specific {@link ICheesecake}
     * instance because {@link ICommandSystem} itself controls global behavior for {@link ICommand}s.
     *
     * @return The {@link ICommandSystem} instance.
     */
    ICommandSystem getCommandSystem();

    /**
     * @return The {@link ISchematicSystem} instance.
     */
    ISchematicSystem getSchematicSystem();
}
