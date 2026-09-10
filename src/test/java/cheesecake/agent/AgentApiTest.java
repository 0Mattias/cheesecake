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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentApiTest {

    private static JsonObject read(BufferedReader in) throws IOException {
        String line = in.readLine();
        assertNotNull("connection closed early", line);
        return JsonParser.parseString(line).getAsJsonObject();
    }

    private static void write(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write('\n');
        out.flush();
    }

    @Test(timeout = 20000)
    public void commandsStatusAndBroadcastsOverTheSocket() throws Exception {
        List<String> executed = new ArrayList<>();
        AgentApi api = new AgentApi(
                Runnable::run,
                command -> {
                    executed.add(command);
                    return command.startsWith("goto");
                },
                () -> {
                    JsonObject status = new JsonObject();
                    status.addProperty("inWorld", false);
                    return status;
                },
                "test"
        );
        api.start(0);
        assertTrue(api.isRunning());
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), api.getPort())) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            JsonObject hello = read(in);
            assertEquals("hello", hello.get("type").getAsString());
            assertEquals(AgentApi.PROTOCOL_VERSION, hello.get("protocol").getAsInt());
            assertEquals("test", hello.get("version").getAsString());

            write(out, "{\"id\": 7, \"command\": \"goto 1 2 3\"}");
            JsonObject result = read(in);
            assertEquals("result", result.get("type").getAsString());
            assertEquals(7, result.get("id").getAsInt());
            assertEquals("goto 1 2 3", result.get("command").getAsString());
            assertTrue(result.get("ok").getAsBoolean());

            write(out, "  nonsense here  ");
            result = read(in);
            assertEquals("result", result.get("type").getAsString());
            assertFalse(result.has("id"));
            assertFalse(result.get("ok").getAsBoolean());
            assertEquals(Arrays.asList("goto 1 2 3", "nonsense here"), executed);

            write(out, "{\"id\": \"s1\", \"status\": true}");
            JsonObject status = read(in);
            assertEquals("status", status.get("type").getAsString());
            assertEquals("s1", status.get("id").getAsString());
            assertFalse(status.get("inWorld").getAsBoolean());

            write(out, "{\"id\": 9, \"nope\": 1}");
            JsonObject error = read(in);
            assertEquals("error", error.get("type").getAsString());
            assertEquals(9, error.get("id").getAsInt());

            write(out, "{ broken");
            error = read(in);
            assertEquals("error", error.get("type").getAsString());
            assertTrue(error.get("message").getAsString().startsWith("malformed JSON"));

            write(out, "");   // blank lines are ignored, nothing comes back for them

            api.broadcastLog("hello there");
            JsonObject log = read(in);
            assertEquals("log", log.get("type").getAsString());
            assertEquals("hello there", log.get("text").getAsString());

            api.broadcastPathEvent(PathEvent.AT_GOAL);
            JsonObject path = read(in);
            assertEquals("path", path.get("type").getAsString());
            assertEquals("AT_GOAL", path.get("event").getAsString());

            assertEquals(1, api.getClientCount());
        } finally {
            api.stop();
        }
        assertFalse(api.isRunning());
        assertEquals(0, api.getClientCount());
    }

    @Test
    public void parsesRequests() {
        assertNull(AgentApi.Request.parse("   "));

        AgentApi.Request bare = AgentApi.Request.parse("goto 1 2 3");
        assertEquals("goto 1 2 3", bare.command);
        assertNull(bare.id);
        assertFalse(bare.status);
        assertNull(bare.error);

        AgentApi.Request json = AgentApi.Request.parse("{\"id\": \"abc\", \"command\": \" mine 8 iron_ore \"}");
        assertEquals("mine 8 iron_ore", json.command);
        assertEquals("abc", json.id.getAsString());

        AgentApi.Request status = AgentApi.Request.parse("{\"status\": true}");
        assertTrue(status.status);
        assertNull(status.command);

        assertNotNull(AgentApi.Request.parse("{\"status\": false}").error);
        assertNotNull(AgentApi.Request.parse("{\"command\": \"\"}").error);
        assertNotNull(AgentApi.Request.parse("[1, 2]").error);
        assertNotNull(AgentApi.Request.parse("{").error);
    }

    @Test(timeout = 20000)
    public void refusesToStartTwiceAndStopIsIdempotent() throws Exception {
        AgentApi api = new AgentApi(Runnable::run, c -> true, JsonObject::new, "test");
        api.start(0);
        try {
            api.start(0);
            throw new AssertionError("second start should fail");
        } catch (IllegalStateException expected) {
            // expected
        } finally {
            api.stop();
            api.stop();
        }
        assertEquals(-1, api.getPort());
    }
}
