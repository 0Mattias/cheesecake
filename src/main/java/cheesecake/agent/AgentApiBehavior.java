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

package cheesecake.agent;

import cheesecake.Cheesecake;
import cheesecake.api.Settings;
import cheesecake.api.event.events.PathEvent;
import cheesecake.api.event.events.TickEvent;
import cheesecake.api.event.listener.AbstractGameEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/**
 * Runs the {@link AgentApi} inside the game: starts and stops it to follow the {@code agentApiPort}
 * setting, forwards chat output and path events to connected clients, and executes their commands on
 * the game thread.
 */
public final class AgentApiBehavior implements AbstractGameEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("cheesecake-agent-api");

    private final Settings settings;
    private final AgentApi api;
    private int failedPort = -1;

    // The chat logger is wrapped so clients see what the mod says; the wrapped logger is restored
    // when the api stops.
    private Consumer<Component> wrappedLogger;
    private Consumer<Component> hook;

    public AgentApiBehavior(Cheesecake cheesecake) {
        this.settings = Cheesecake.settings();
        this.api = new AgentApi(
                r -> cheesecake.getPlayerContext().minecraft().execute(r),
                cheesecake.getCommandManager()::execute,
                () -> AgentStatus.snapshot(cheesecake),
                version()
        );
    }

    public AgentApi getApi() {
        return this.api;
    }

    @Override
    public void onTick(TickEvent event) {
        int port = this.settings.agentApiPort.value;
        if (this.api.isRunning() && (port <= 0 || port != this.api.getPort())) {
            this.api.stop();
        }
        if (!this.api.isRunning() && port > 0 && port != this.failedPort) {
            try {
                this.api.start(port);
                this.failedPort = -1;
            } catch (IOException e) {
                this.failedPort = port; // try again once the setting changes
                LOGGER.warn("Could not start the agent API on port {}: {}", port, e.toString());
            }
        }
        if (this.api.isRunning()) {
            if (this.settings.logger.value != this.hook) {
                this.wrappedLogger = this.settings.logger.value;
                final Consumer<Component> wrapped = this.wrappedLogger;
                this.hook = message -> {
                    wrapped.accept(message);
                    this.api.broadcastLog(message.getString());
                };
                this.settings.logger.value = this.hook;
            }
        } else if (this.hook != null && this.settings.logger.value == this.hook) {
            this.settings.logger.value = this.wrappedLogger;
            this.hook = null;
        }
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if (this.api.isRunning()) {
            this.api.broadcastPathEvent(event);
        }
    }

    private static String version() {
        String version = Cheesecake.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }
}
