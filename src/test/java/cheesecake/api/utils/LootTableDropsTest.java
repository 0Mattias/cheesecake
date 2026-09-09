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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LootTableDropsTest {

    /**
     * The real vanilla data, read from the Minecraft jar on the test classpath.
     */
    private static final LootTableDrops VANILLA = new LootTableDrops(LootTableDrops.CLASSPATH);

    private static Set<String> vanilla(String block) {
        Optional<Set<String>> ids = VANILLA.itemIds("minecraft:blocks/" + block);
        assertTrue("vanilla loot table for " + block + " should be on the test classpath", ids.isPresent());
        return ids.get();
    }

    @Test
    public void oreDropsRawResourceAndItselfWithSilkTouch() {
        assertEquals(Set.of("minecraft:iron_ore", "minecraft:raw_iron"), vanilla("iron_ore"));
        assertEquals(Set.of("minecraft:diamond_ore", "minecraft:diamond"), vanilla("diamond_ore"));
    }

    @Test
    public void stoneDropsCobblestone() {
        assertEquals(Set.of("minecraft:stone", "minecraft:cobblestone"), vanilla("stone"));
    }

    @Test
    public void grassBlockDropsDirt() {
        assertEquals(Set.of("minecraft:grass_block", "minecraft:dirt"), vanilla("grass_block"));
    }

    @Test
    public void silkTouchOnlyBlocksStillListThemselves() {
        assertEquals(Set.of("minecraft:glass"), vanilla("glass"));
    }

    @Test
    public void plainBlocksDropThemselves() {
        assertEquals(Set.of("minecraft:cobblestone"), vanilla("cobblestone"));
        assertEquals(Set.of("minecraft:oak_planks"), vanilla("oak_planks"));
    }

    @Test
    public void missingTableIsAbsentNotEmpty() {
        assertFalse(VANILLA.itemIds("minecraft:blocks/definitely_not_a_block").isPresent());
        assertFalse(VANILLA.itemIds("somemod:blocks/whatever").isPresent());
    }

    // ---- hermetic cases against hand-written tables ----

    private static LootTableDrops inMemory(Map<String, String> files) {
        return new LootTableDrops(path -> {
            String body = files.get(path);
            return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    public void followsTableReferencesAndInlineTables() {
        Map<String, String> files = new HashMap<>();
        files.put("data/mod/loot_table/blocks/outer.json", "{\"pools\":[{\"entries\":[" +
                "{\"type\":\"minecraft:loot_table\",\"value\":\"mod:blocks/inner\"}," +
                "{\"type\":\"minecraft:loot_table\",\"value\":{\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mod:inline\"}]}]}}" +
                "]}]}");
        files.put("data/mod/loot_table/blocks/inner.json", "{\"pools\":[{\"entries\":[" +
                "{\"type\":\"minecraft:group\",\"children\":[{\"type\":\"minecraft:item\",\"name\":\"mod:nested\"}]}," +
                "{\"type\":\"minecraft:loot_table\",\"value\":\"mod:blocks/outer\"}" +   // cycle
                "]}]}");
        assertEquals(Set.of("mod:nested", "mod:inline"), inMemory(files).itemIds("mod:blocks/outer").orElseThrow());
    }

    @Test
    public void expandsItemTagsIncludingNestedTags() {
        Map<String, String> files = new HashMap<>();
        files.put("data/mod/loot_table/blocks/tagged.json", "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:tag\",\"name\":\"mod:gems\",\"expand\":true}]}]}");
        files.put("data/mod/tags/item/gems.json", "{\"values\":[\"mod:ruby\",{\"id\":\"mod:sapphire\",\"required\":false},\"#minecraft:diamonds\"]}");
        files.put("data/minecraft/tags/item/diamonds.json", "{\"values\":[\"diamond\",\"#minecraft:diamonds\"]}");   // self reference
        assertEquals(Set.of("mod:ruby", "mod:sapphire", "minecraft:diamond"), inMemory(files).itemIds("mod:blocks/tagged").orElseThrow());
    }

    @Test
    public void namespacesUnqualifiedIds() {
        Map<String, String> files = new HashMap<>();
        files.put("data/minecraft/loot_table/blocks/plain.json", "{\"pools\":[{\"entries\":[{\"type\":\"item\",\"name\":\"stone\"}]}]}");
        assertEquals(Set.of("minecraft:stone"), inMemory(files).itemIds("blocks/plain").orElseThrow());
    }

    @Test
    public void tableWithoutPoolsIsPresentAndEmpty() {
        Map<String, String> files = new HashMap<>();
        files.put("data/minecraft/loot_table/blocks/nothing.json", "{\"type\":\"minecraft:block\"}");
        Optional<Set<String>> ids = inMemory(files).itemIds("minecraft:blocks/nothing");
        assertTrue(ids.isPresent());
        assertTrue(ids.get().isEmpty());
    }

    @Test
    public void ignoresUnknownAndDynamicEntries() {
        Map<String, String> files = new HashMap<>();
        files.put("data/minecraft/loot_table/blocks/pot.json", "{\"pools\":[{\"entries\":[" +
                "{\"type\":\"minecraft:dynamic\",\"name\":\"minecraft:sherds\"}," +
                "{\"type\":\"minecraft:empty\"}," +
                "{\"type\":\"somemod:weird\",\"name\":\"somemod:thing\"}," +
                "{\"type\":\"minecraft:item\",\"name\":\"minecraft:decorated_pot\"}" +
                "]}]}");
        assertEquals(Set.of("minecraft:decorated_pot"), inMemory(files).itemIds("minecraft:blocks/pot").orElseThrow());
    }

    @Test
    public void malformedJsonIsTreatedAsAbsent() {
        Map<String, String> files = new HashMap<>();
        files.put("data/minecraft/loot_table/blocks/broken.json", "{\"pools\": [");
        assertFalse(inMemory(files).itemIds("minecraft:blocks/broken").isPresent());
    }

    @Test
    public void pathMapping() {
        assertEquals("data/minecraft/loot_table/blocks/iron_ore.json", LootTableDrops.tablePath("minecraft:blocks/iron_ore"));
        assertEquals("data/mod/tags/item/gems.json", LootTableDrops.tagPath("mod:gems"));
        assertNotNull(LootTableDrops.normalize(null));
    }
}
