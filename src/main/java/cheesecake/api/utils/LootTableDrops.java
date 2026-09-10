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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Resolves which items a loot table can produce by reading its JSON definition and walking every
 * entry, instead of rolling the table.
 * <p>
 * The client never receives loot tables from the server, but the definitions of every vanilla and
 * jar-shipped modded table are on the classpath as {@code data/<namespace>/loot_table/<path>.json}.
 * Walking them statically yields every item the table can drop under any condition (silk touch,
 * fortune, tool), which is what mining wants: an ore counts as mined whether it dropped the raw
 * resource or, with silk touch, itself. Tables that only exist in a server-side data pack are not
 * visible; {@link #itemIds} reports those as absent so the caller can fall back.
 * <p>
 * This class deliberately depends on nothing from Minecraft so it can be unit tested against the
 * real vanilla data.
 */
public final class LootTableDrops {

    /**
     * Opens a classpath-style resource path such as {@code data/minecraft/loot_table/blocks/iron_ore.json},
     * returning {@code null} when it does not exist.
     */
    @FunctionalInterface
    public interface ResourceReader {

        @Nullable
        InputStream open(String path) throws IOException;
    }

    /**
     * Reads from the class loader that loaded this mod, which on Fabric also sees the game jar and
     * every other mod jar.
     */
    public static final ResourceReader CLASSPATH = path -> LootTableDrops.class.getClassLoader().getResourceAsStream(path);

    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final int MAX_DEPTH = 16;

    private final ResourceReader reader;

    public LootTableDrops(ResourceReader reader) {
        this.reader = reader;
    }

    /**
     * Every item id any entry of the table can produce, following references to other tables and
     * expanding item tags.
     *
     * @param tableId The loot table id, e.g. {@code minecraft:blocks/iron_ore}
     * @return The item ids, or empty if the table definition could not be read at all. A table that
     * exists but produces nothing yields a present, empty set.
     */
    public Optional<Set<String>> itemIds(String tableId) {
        JsonObject table = readJson(tablePath(normalize(tableId)));
        if (table == null) {
            return Optional.empty();
        }
        Set<String> items = new LinkedHashSet<>();
        walkTable(table, items, new HashSet<>(), 0);
        return Optional.of(items);
    }

    private void walkTable(JsonObject table, Set<String> items, Set<String> visited, int depth) {
        JsonArray pools = array(table, "pools");
        if (pools == null) {
            return;
        }
        for (JsonElement pool : pools) {
            if (pool.isJsonObject()) {
                walkEntries(array(pool.getAsJsonObject(), "entries"), items, visited, depth);
            }
        }
    }

    private void walkEntries(@Nullable JsonArray entries, Set<String> items, Set<String> visited, int depth) {
        if (entries == null || depth > MAX_DEPTH) {
            return;
        }
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            switch (normalize(string(entry, "type"))) {
                case "minecraft:item":
                    String name = string(entry, "name");
                    if (name != null) {
                        items.add(normalize(name));
                    }
                    break;
                case "minecraft:alternatives":
                case "minecraft:group":
                case "minecraft:sequence":
                    walkEntries(array(entry, "children"), items, visited, depth + 1);
                    break;
                case "minecraft:loot_table":
                    JsonElement value = entry.get("value");
                    if (value == null) {
                        break;
                    }
                    if (value.isJsonObject()) {
                        walkTable(value.getAsJsonObject(), items, visited, depth + 1);
                    } else if (value.isJsonPrimitive()) {
                        String id = normalize(value.getAsString());
                        if (visited.add("table:" + id)) {
                            JsonObject referenced = readJson(tablePath(id));
                            if (referenced != null) {
                                walkTable(referenced, items, visited, depth + 1);
                            }
                        }
                    }
                    break;
                case "minecraft:tag":
                    String tag = string(entry, "name");
                    if (tag != null) {
                        walkTag(normalize(tag), items, visited, depth + 1);
                    }
                    break;
                default:
                    // minecraft:empty, minecraft:dynamic (container contents) and anything modded we don't know
                    break;
            }
        }
    }

    private void walkTag(String tagId, Set<String> items, Set<String> visited, int depth) {
        if (depth > MAX_DEPTH || !visited.add("tag:" + tagId)) {
            return;
        }
        JsonObject tag = readJson(tagPath(tagId));
        if (tag == null) {
            return;
        }
        JsonArray values = array(tag, "values");
        if (values == null) {
            return;
        }
        for (JsonElement element : values) {
            String value;
            if (element.isJsonObject()) {
                value = string(element.getAsJsonObject(), "id");
            } else if (element.isJsonPrimitive()) {
                value = element.getAsString();
            } else {
                continue;
            }
            if (value == null) {
                continue;
            }
            if (value.startsWith("#")) {
                walkTag(normalize(value.substring(1)), items, visited, depth + 1);
            } else {
                items.add(normalize(value));
            }
        }
    }

    @Nullable
    private JsonObject readJson(String path) {
        try (InputStream in = reader.open(path)) {
            if (in == null) {
                return null;
            }
            try (Reader text = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(text);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (IOException | RuntimeException e) {
            // unreadable or malformed: treat as absent
            return null;
        }
    }

    static String tablePath(String tableId) {
        return "data/" + namespace(tableId) + "/loot_table/" + path(tableId) + ".json";
    }

    static String tagPath(String tagId) {
        return "data/" + namespace(tagId) + "/tags/item/" + path(tagId) + ".json";
    }

    /**
     * Adds the {@code minecraft:} namespace when an id has none.
     */
    static String normalize(@Nullable String id) {
        if (id == null) {
            return "";
        }
        return id.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + id : id;
    }

    private static String namespace(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    @Nullable
    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    @Nullable
    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
