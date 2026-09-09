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

import cheesecake.api.utils.accessor.IItemStack;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked"})
public final class BlockOptionalMeta {
    // id or id[] or id[properties] where id and properties are any text with at
    // least one character
    private static final Pattern PATTERN = Pattern.compile("^(?<id>.+?)(?:\\[(?<properties>.+?)?\\])?$");

    private final Block block;
    private final String propertiesDescription; // exists so toString() can return something more useful than a list of
                                                // all blockstates
    private final Set<BlockState> blockstates;
    private final Set<Integer> stateHashes;
    private final Set<Integer> stackHashes;
    private static final LootTableDrops LOOT = new LootTableDrops(LootTableDrops.CLASSPATH);
    private static Map<Block, List<Item>> drops = new HashMap<>();

    public BlockOptionalMeta(@Nonnull Block block) {
        this.block = block;
        this.propertiesDescription = "{}";
        this.blockstates = getStates(block, Collections.emptyMap());
        this.stateHashes = getStateHashes(blockstates);
        this.stackHashes = getStackHashes(blockstates);
    }

    public BlockOptionalMeta(@Nonnull String selector) {
        Matcher matcher = PATTERN.matcher(selector);

        if (!matcher.find()) {
            throw new IllegalArgumentException("invalid block selector");
        }

        block = BlockUtils.stringToBlockRequired(matcher.group("id"));

        String props = matcher.group("properties");
        Map<Property<?>, ?> properties = props == null || props.equals("") ? Collections.emptyMap()
                : parseProperties(block, props);

        propertiesDescription = props == null ? "{}" : "{" + props.replace("=", ":") + "}";
        blockstates = getStates(block, properties);
        stateHashes = getStateHashes(blockstates);
        stackHashes = getStackHashes(blockstates);
    }

    private static <C extends Comparable<C>, P extends Property<C>> P castToIProperty(Object value) {
        // noinspection unchecked
        return (P) value;
    }

    private static Map<Property<?>, ?> parseProperties(Block block, String raw) {
        ImmutableMap.Builder<Property<?>, Object> builder = ImmutableMap.builder();
        for (String pair : raw.split(",")) {
            String[] parts = pair.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException(String.format("\"%s\" is not a valid property-value pair", pair));
            }
            String rawKey = parts[0];
            String rawValue = parts[1];
            Property<?> key = block.getStateManager().getProperty(rawKey);
            Comparable<?> value = castToIProperty(key).parse(rawValue)
                    .orElseThrow(() -> new IllegalArgumentException(String.format(
                            "\"%s\" is not a valid value for %s on %s",
                            rawValue, key, block)));
            builder.put(key, value);
        }
        return builder.build();
    }

    private static Set<BlockState> getStates(@Nonnull Block block, @Nonnull Map<Property<?>, ?> properties) {
        return block.getStateManager().getStates().stream()
                .filter(blockstate -> properties.entrySet().stream()
                        .allMatch(entry -> blockstate.get(entry.getKey()) == entry.getValue()))
                .collect(Collectors.toSet());
    }

    private static ImmutableSet<Integer> getStateHashes(Set<BlockState> blockstates) {
        return ImmutableSet.copyOf(
                blockstates.stream()
                        .map(BlockState::hashCode)
                        .toArray(Integer[]::new));
    }

    private static ImmutableSet<Integer> getStackHashes(Set<BlockState> blockstates) {
        // noinspection ConstantConditions
        return ImmutableSet.copyOf(
                blockstates.stream()
                        .flatMap(state -> drops(state.getBlock())
                                .stream()
                                .map(item -> new ItemStack(item, 1)))
                        .map(stack -> ((IItemStack) (Object) stack).getCheesecakeHash())
                        .toArray(Integer[]::new));
    }

    public Block getBlock() {
        return block;
    }

    public boolean matches(@Nonnull Block block) {
        return block == this.block;
    }

    public boolean matches(@Nonnull BlockState blockstate) {
        Block block = blockstate.getBlock();
        return block == this.block && stateHashes.contains(blockstate.hashCode());
    }

    public boolean matches(ItemStack stack) {
        // noinspection ConstantConditions
        int hash = ((IItemStack) (Object) stack).getCheesecakeHash();

        hash -= stack.getDamage();

        return stackHashes.contains(hash);
    }

    @Override
    public String toString() {
        return String.format("BlockOptionalMeta{block=%s,properties=%s}", block, propertiesDescription);
    }

    public BlockState getAnyBlockState() {
        if (blockstates.size() > 0) {
            return blockstates.iterator().next();
        }

        return null;
    }

    public Set<BlockState> getAllBlockStates() {
        return blockstates;
    }

    public Set<Integer> stackHashes() {
        return stackHashes;
    }

    /**
     * Every item the block can drop, so that {@code #mine <quantity> <block>} recognises raw iron as
     * the product of iron ore and cobblestone as the product of stone, and
     * {@code mineScanDroppedItems} targets those drops on the ground.
     * <p>
     * Resolved by walking the block's loot table definition from the game and mod jars (see
     * {@link LootTableDrops}), which lists every item the table can produce under any tool or
     * enchantment. Upstream instead rolls the table once through a faked server world; that needs
     * mixins into loot internals and only sees vanilla tables. Blocks whose table lives solely in a
     * server-side data pack cannot be resolved on the client and are assumed to drop themselves.
     */
    private static synchronized List<Item> drops(Block b) {
        return drops.computeIfAbsent(b, block -> {
            Optional<RegistryKey<LootTable>> key = block.getLootTableKey();
            if (key.isEmpty()) {
                return Collections.emptyList(); // dropsNothing()
            }
            Optional<Set<String>> ids = LOOT.itemIds(key.get().getValue().toString());
            if (ids.isEmpty()) {
                Item item = block.asItem();
                // Blocks with no item form map to AIR; letting that through would make every empty
                // inventory slot look like a match.
                return item == Items.AIR ? Collections.emptyList() : Collections.singletonList(item);
            }
            List<Item> items = new ArrayList<>();
            for (String id : ids.get()) {
                Identifier identifier = Identifier.tryParse(id);
                if (identifier != null && Registries.ITEM.containsId(identifier)) {
                    items.add(Registries.ITEM.get(identifier));
                }
            }
            return items;
        });
    }

}
