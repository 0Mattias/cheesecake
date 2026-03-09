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
// import io.netty.util.concurrent.ThreadPerTaskExecutor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
// import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
// import net.minecraft.loot.context.LootContext;
// import net.minecraft.loot.context.LootContextParameters;
// import net.minecraft.loot.context.LootContextTypes;
// import net.minecraft.registry.RegistryKey;
// import net.minecraft.resource.DefaultResourcePack;
// import net.minecraft.resource.LifecycledResourceManagerImpl;
// import net.minecraft.resource.ReloadableResourceManagerImpl;
// import net.minecraft.resource.ResourceType;
// import net.minecraft.resource.VanillaDataPackProvider;
// import net.minecraft.resource.featuretoggle.FeatureSet;
// import net.minecraft.server.MinecraftServer;
// import net.minecraft.resource.*;
// import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
// import net.minecraft.util.Identifier;
// import net.minecraft.util.Unit;
// import net.minecraft.util.math.BlockPos;
// import net.minecraft.util.math.Vec3d;
// import net.minecraft.world.World;
// import net.minecraft.world.dimension.DimensionOptions;
// import net.minecraft.world.level.ServerWorldProperties;
// import net.minecraft.world.level.storage.LevelStorage;
// import net.minecraft.world.spawner.SpecialSpawner;
// import sun.misc.Unsafe;

import javax.annotation.Nonnull;
// import java.lang.reflect.Field;
// import java.lang.reflect.Method;
// import java.util.ArrayList;
// import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.Random;
import java.util.Set;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.Executor;
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
//     private static Object lootTables;
//     private static Object predicate = new Object();
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

    private static synchronized List<Item> drops(Block b) {
        return drops.computeIfAbsent(b, block -> {
            return Collections.singletonList(block.asItem());
        });
    }

}
