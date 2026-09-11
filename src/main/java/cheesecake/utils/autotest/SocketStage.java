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

import cheesecake.api.event.events.PathEvent;
import cheesecake.api.pathing.goals.GoalXZ;
import cheesecake.api.utils.BetterBlockPos;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Drives the mod through the control socket the way a program would: opens it with the
 * {@code agentApiPort} setting, connects, sends a goto and a status request, and expects the result,
 * the status, log messages and path events including AT_GOAL to arrive. Closing the port must then
 * close the connection.
 */
public final class SocketStage extends Stage {

    private static final int DISTANCE = 12;

    private int port;
    private GoalXZ goal;
    private int phase;
    private volatile boolean stopping;
    private volatile boolean resultOk;
    private volatile boolean statusOk;
    private volatile boolean atGoal;
    /**
     * Where the player stood when the path event fired, which is not where it stands by the time
     * the socket has delivered the message and this stage next runs: arriving carries the player on
     * for a few ticks, which is enough to leave a GoalXZ behind.
     */
    private volatile BetterBlockPos atGoalPos;
    private volatile boolean closed;
    private volatile int logs;
    private volatile int pathEvents;
    private volatile String error;

    @Override
    public void onPathEvent(PathEvent event) {
        super.onPathEvent(event);
        // The driver forwards the event on the game thread as it fires, so this is the one place
        // the arrival position can be read before the player has moved on.
        if (event == PathEvent.AT_GOAL && this.atGoalPos == null && this.t != null) {
            this.atGoalPos = feet();
        }
    }

    @Override
    public String name() {
        return "socket";
    }

    @Override
    protected int timeoutTicks() {
        return 1500;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos feet = feet();
        this.goal = new GoalXZ(feet.x + DISTANCE, feet.z);
        this.port = freePort();
        this.t.log("opening the control socket on port " + this.port);
        settings().agentApiPort.value = this.port;
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (this.error != null) {
            check(false, "client error: " + this.error);
        }
        switch (this.phase) {
            case 0:
                // The behaviour opens the socket on the tick after the setting changed.
                if (ticks() >= 5) {
                    Thread thread = new Thread(this::drive, "cheesecake-autotest-socket");
                    thread.setDaemon(true);
                    thread.start();
                    this.phase = 1;
                }
                return false;
            case 1:
                if (this.atGoal && this.atGoalPos != null && this.resultOk && this.statusOk && this.logs > 0) {
                    check(this.goal.isInGoal(this.atGoalPos),
                            "AT_GOAL was reported at " + this.atGoalPos + " for " + this.goal);
                    this.t.log("got the result, the status, " + this.logs + " log messages and " + this.pathEvents + " path events; closing the port");
                    this.stopping = true;
                    settings().agentApiPort.value = 0;
                    this.phase = 2;
                }
                return false;
            default:
                if (this.closed) {
                    this.t.log("the connection was closed when the port was set to 0");
                    return true;
                }
                return false;
        }
    }

    private void drive() {
        try (Socket socket = connect();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            JsonObject hello = parse(in.readLine());
            if (!"hello".equals(type(hello))) {
                this.error = "expected a hello, got " + hello;
                return;
            }
            this.t.log("hello: " + hello);
            write(out, "{\"id\": 1, \"command\": \"goto " + this.goal.getX() + " " + this.goal.getZ() + "\"}");
            write(out, "{\"id\": 2, \"status\": true}");
            String line;
            while ((line = in.readLine()) != null) {
                JsonObject message = parse(line);
                if (!"log".equals(type(message))) {
                    this.t.log("received " + line);
                }
                switch (type(message)) {
                    case "result":
                        if (message.get("id").getAsInt() == 1 && message.get("ok").getAsBoolean()) {
                            this.resultOk = true;
                        } else {
                            this.error = "unexpected result " + line;
                        }
                        break;
                    case "status":
                        if (message.get("id").getAsInt() == 2 && message.get("inWorld").getAsBoolean()) {
                            this.statusOk = true;
                        } else {
                            this.error = "unexpected status " + line;
                        }
                        break;
                    case "log":
                        this.logs++;
                        break;
                    case "path":
                        this.pathEvents++;
                        if ("AT_GOAL".equals(message.get("event").getAsString())) {
                            this.atGoal = true;
                        }
                        break;
                    default:
                        this.error = "unexpected message " + line;
                }
            }
            this.closed = true;
        } catch (IOException e) {
            if (this.stopping) {
                this.closed = true; // the mod closed the socket under the reader
            } else {
                this.error = e.toString();
            }
        } catch (Exception e) {
            this.error = e.toString();
        }
    }

    private Socket connect() throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                return new Socket(InetAddress.getLoopbackAddress(), this.port);
            } catch (IOException e) {
                last = e;
                Thread.sleep(250);
            }
        }
        throw last;
    }

    private static JsonObject parse(String line) throws IOException {
        if (line == null) {
            throw new IOException("connection closed");
        }
        return JsonParser.parseString(line).getAsJsonObject();
    }

    private static String type(JsonObject message) {
        return message.has("type") ? message.get("type").getAsString() : "";
    }

    private static void write(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write('\n');
        out.flush();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new AutoTestFailure("no free port: " + e, e);
        }
    }
}
