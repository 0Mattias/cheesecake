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

import cheesecake.api.event.events.PathEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A control socket for programs that drive the bot: newline-delimited JSON over TCP, bound to the
 * loopback interface only.
 * <p>
 * Each request is one line. {@code {"id": 7, "command": "goto 100 64 100"}} runs a chat command
 * (without the prefix) and answers with {@code {"type": "result", "id": 7, "command": ..., "ok": true}},
 * where {@code ok} says whether the text matched a command. A line that is not a JSON object is
 * treated as a bare command. {@code {"id": "s", "status": true}} answers with a status snapshot.
 * Every client also receives, unprompted, {@code {"type": "log", "text": ...}} for each message the
 * mod writes to chat and {@code {"type": "path", "event": ...}} for path events. On connect the
 * server sends {@code {"type": "hello", "protocol": 1, "version": ...}}.
 * <p>
 * This class is transport only. Commands, status snapshots and the game thread are supplied by the
 * caller so the socket protocol can be exercised without Minecraft; {@link AgentApiBehavior} does
 * the wiring in the game.
 */
public final class AgentApi {

    public static final int PROTOCOL_VERSION = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger("cheesecake-agent-api");
    private static final int OUTBOX_CAPACITY = 1024;
    private static final String CLOSE = " close";

    private final Executor gameThread;
    private final Function<String, Boolean> commands;
    private final Supplier<JsonObject> status;
    private final String version;
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger dropped = new AtomicInteger();

    @Nullable
    private ServerSocket server;

    /**
     * @param gameThread Runs work on the game thread; commands and snapshots must not touch the
     *                   world from a socket thread
     * @param commands   Executes one chat command (without prefix); returns whether it matched
     * @param status     Builds a status snapshot, called on the game thread
     * @param version    Reported in the hello message
     */
    public AgentApi(Executor gameThread, Function<String, Boolean> commands, Supplier<JsonObject> status, String version) {
        this.gameThread = gameThread;
        this.commands = commands;
        this.status = status;
        this.version = version;
    }

    /**
     * Binds 127.0.0.1 on the port, or an ephemeral port for 0, and starts accepting connections.
     */
    public synchronized void start(int port) throws IOException {
        if (this.server != null) {
            throw new IllegalStateException("already running on port " + getPort());
        }
        ServerSocket socket = new ServerSocket(port, 8, InetAddress.getLoopbackAddress());
        this.server = socket;
        Thread accept = new Thread(() -> acceptLoop(socket), "cheesecake-agent-api");
        accept.setDaemon(true);
        accept.start();
        LOGGER.info("Agent API listening on 127.0.0.1:{}", socket.getLocalPort());
    }

    public synchronized void stop() {
        ServerSocket socket = this.server;
        if (socket == null) {
            return;
        }
        this.server = null;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        for (Client client : this.clients) {
            client.close();
        }
        this.clients.clear();
        LOGGER.info("Agent API stopped");
    }

    public synchronized boolean isRunning() {
        return this.server != null;
    }

    public synchronized int getPort() {
        ServerSocket socket = this.server;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public int getClientCount() {
        return this.clients.size();
    }

    /**
     * Messages dropped because a client was not reading fast enough.
     */
    public int getDroppedMessages() {
        return this.dropped.get();
    }

    public void broadcastLog(String text) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "log");
        message.addProperty("text", text);
        broadcast(message);
    }

    public void broadcastPathEvent(PathEvent event) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "path");
        message.addProperty("event", event.name());
        broadcast(message);
    }

    private void broadcast(JsonObject message) {
        String line = message.toString();
        for (Client client : this.clients) {
            client.send(line);
        }
    }

    private void acceptLoop(ServerSocket socket) {
        while (!socket.isClosed()) {
            try {
                Socket connection = socket.accept();
                Client client = new Client(connection);
                this.clients.add(client);
                client.start();
            } catch (SocketException e) {
                // closed by stop()
                return;
            } catch (IOException e) {
                LOGGER.warn("Agent API accept failed", e);
            }
        }
    }

    private void handle(Client client, String line) {
        Request request = Request.parse(line);
        if (request == null) {
            return;
        }
        if (request.error != null) {
            client.send(error(request.id, request.error));
            return;
        }
        if (request.status) {
            this.gameThread.execute(() -> {
                JsonObject snapshot;
                try {
                    snapshot = this.status.get();
                } catch (Throwable t) {
                    LOGGER.warn("Agent API status snapshot failed", t);
                    client.send(error(request.id, "status failed: " + t));
                    return;
                }
                snapshot.addProperty("type", "status");
                if (request.id != null) {
                    snapshot.add("id", request.id);
                }
                client.send(snapshot.toString());
            });
            return;
        }
        final String command = request.command;
        this.gameThread.execute(() -> {
            boolean ok;
            try {
                ok = this.commands.apply(command);
            } catch (Throwable t) {
                LOGGER.warn("Agent API command failed: {}", command, t);
                client.send(error(request.id, "command failed: " + t));
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("type", "result");
            if (request.id != null) {
                result.add("id", request.id);
            }
            result.addProperty("command", command);
            result.addProperty("ok", ok);
            client.send(result.toString());
        });
    }

    private static String error(@Nullable JsonElement id, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        if (id != null) {
            error.add("id", id);
        }
        error.addProperty("message", message);
        return error.toString();
    }

    private String hello() {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("protocol", PROTOCOL_VERSION);
        hello.addProperty("version", this.version);
        return hello.toString();
    }

    /**
     * One parsed request line.
     */
    public static final class Request {

        @Nullable
        public final JsonElement id;
        @Nullable
        public final String command;
        public final boolean status;
        @Nullable
        public final String error;

        private Request(@Nullable JsonElement id, @Nullable String command, boolean status, @Nullable String error) {
            this.id = id;
            this.command = command;
            this.status = status;
            this.error = error;
        }

        /**
         * @return The request, or {@code null} for a blank line
         */
        @Nullable
        public static Request parse(String line) {
            String text = line.trim();
            if (text.isEmpty()) {
                return null;
            }
            if (!text.startsWith("{") && !text.startsWith("[")) {
                return new Request(null, text, false, null);
            }
            JsonObject object;
            try {
                JsonElement parsed = JsonParser.parseString(text);
                if (!parsed.isJsonObject()) {
                    return new Request(null, null, false, "request must be a JSON object");
                }
                object = parsed.getAsJsonObject();
            } catch (JsonSyntaxException e) {
                return new Request(null, null, false, "malformed JSON: " + e.getMessage());
            }
            JsonElement id = object.has("id") && !object.get("id").isJsonNull() ? object.get("id") : null;
            JsonElement status = object.get("status");
            if (status != null && status.isJsonPrimitive() && status.getAsJsonPrimitive().isBoolean() && status.getAsBoolean()) {
                return new Request(id, null, true, null);
            }
            JsonElement command = object.get("command");
            if (command != null && command.isJsonPrimitive() && command.getAsJsonPrimitive().isString()) {
                String value = command.getAsString().trim();
                if (!value.isEmpty()) {
                    return new Request(id, value, false, null);
                }
            }
            return new Request(id, null, false, "request needs a \"command\" string or \"status\": true");
        }
    }

    private final class Client {

        private final Socket socket;
        private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>(OUTBOX_CAPACITY);
        private volatile boolean closed;

        Client(Socket socket) {
            this.socket = socket;
        }

        void start() {
            Thread writer = new Thread(this::writeLoop, "cheesecake-agent-api-writer");
            writer.setDaemon(true);
            writer.start();
            Thread reader = new Thread(this::readLoop, "cheesecake-agent-api-reader");
            reader.setDaemon(true);
            reader.start();
            send(hello());
        }

        void send(String line) {
            if (this.closed) {
                return;
            }
            if (!this.outbox.offer(line)) {
                AgentApi.this.dropped.incrementAndGet();
            }
        }

        private void readLoop() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    handle(this, line);
                }
            } catch (IOException e) {
                // the peer went away, or we closed the socket
            } finally {
                close();
            }
        }

        private void writeLoop() {
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream(), StandardCharsets.UTF_8))) {
                while (true) {
                    String line = this.outbox.take();
                    if (CLOSE.equals(line)) {
                        return;
                    }
                    out.write(line);
                    out.write('\n');
                    out.flush();
                }
            } catch (IOException | InterruptedException e) {
                // the peer went away, or we closed the socket
            } finally {
                close();
            }
        }

        void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            AgentApi.this.clients.remove(this);
            this.outbox.clear();
            this.outbox.offer(CLOSE);
            try {
                this.socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
