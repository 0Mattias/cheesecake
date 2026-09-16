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

package cheesecake.pathing.movement;

import cheesecake.Cheesecake;
import cheesecake.api.ICheesecake;
import cheesecake.api.pathing.movement.ActionCosts;
import cheesecake.cache.WorldData;
import cheesecake.pathing.precompute.PrecomputedData;
import cheesecake.utils.BlockStateInterface;
import cheesecake.utils.ToolSet;
import cheesecake.utils.pathing.BetterWorldBorder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import static cheesecake.api.pathing.movement.ActionCosts.COST_INF;

/**
 * @author Brady
 * @since 8/7/2018
 */
public class CalculationContext {

    private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);

    public final boolean safeForThreadedUse;
    public final ICheesecake cheesecake;
    public final Level world;
    public final WorldData worldData;
    public final BlockStateInterface bsi;
    public final ToolSet toolSet;
    public final boolean hasWaterBucket;
    public final boolean hasThrowaway;
    public final boolean canSprint;
    protected final double placeBlockCost; // protected because you should call the function instead
    public final boolean allowBreak;
    public final List<Block> allowBreakAnyway;
    public final boolean allowParkour;
    public final boolean allowParkourPlace;
    public final boolean allowJumpAtBuildLimit;
    public final boolean allowParkourAscend;
    public final boolean assumeWalkOnWater;
    public boolean allowFallIntoLava;
    public final int frostWalker;
    public final boolean allowDiagonalDescend;
    public final boolean allowDiagonalAscend;
    public final boolean allowDownward;
    public int minFallHeight;
    public int maxFallHeightNoWater;
    public final int maxFallHeightBucket;
    public final double waterWalkSpeed;
    public final double breakBlockAdditionalCost;
    public double backtrackCostFavoringCoefficient;
    public double jumpPenalty;
    public final double walkOnWaterOnePenalty;
    public final boolean allowWalkOnMagmaBlocks;
    public final BetterWorldBorder worldBorder;

    public final PrecomputedData precomputedData;

    public CalculationContext(ICheesecake cheesecake) {
        this(cheesecake, false);
    }

    public CalculationContext(ICheesecake cheesecake, boolean forUseOnAnotherThread) {
        this.precomputedData = new PrecomputedData();
        this.safeForThreadedUse = forUseOnAnotherThread;
        this.cheesecake = cheesecake;
        LocalPlayer player = cheesecake.getPlayerContext().player();
        this.world = cheesecake.getPlayerContext().world();
        this.worldData = (WorldData) cheesecake.getPlayerContext().worldData();
        this.bsi = new BlockStateInterface(cheesecake.getPlayerContext(), forUseOnAnotherThread);
        this.toolSet = new ToolSet(player);
        this.hasThrowaway = Cheesecake.settings().allowPlace.value && ((Cheesecake) cheesecake).getInventoryBehavior().hasGenericThrowaway();
        this.hasWaterBucket = Cheesecake.settings().allowWaterBucketFall.value && Inventory.isHotbarSlot(player.getInventory().findSlotMatchingItem(STACK_BUCKET_WATER)) && world.dimension() != Level.NETHER;
        this.canSprint = Cheesecake.settings().allowSprint.value && player.getFoodData().getFoodLevel() > 6;
        this.placeBlockCost = Cheesecake.settings().blockPlacementPenalty.value;
        this.allowBreak = Cheesecake.settings().allowBreak.value;
        this.allowBreakAnyway = new ArrayList<>(Cheesecake.settings().allowBreakAnyway.value);
        this.allowParkour = Cheesecake.settings().allowParkour.value;
        this.allowParkourPlace = Cheesecake.settings().allowParkourPlace.value;
        this.allowJumpAtBuildLimit = Cheesecake.settings().allowJumpAtBuildLimit.value;
        this.allowParkourAscend = Cheesecake.settings().allowParkourAscend.value;
        this.assumeWalkOnWater = Cheesecake.settings().assumeWalkOnWater.value;
        this.allowFallIntoLava = false; // Super secret internal setting for ElytraBehavior
        // todo: technically there can now be datapack enchants that replace blocks with any other at any range
        int frostWalkerLevel = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments = cheesecake.getPlayerContext()
                .player()
                .getItemBySlot(slot)
                .getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                if (enchant.is(Enchantments.FROST_WALKER)) {
                    frostWalkerLevel = itemEnchantments.getLevel(enchant);
                }
            }
        }
        this.frostWalker = frostWalkerLevel;
        this.allowDiagonalDescend = Cheesecake.settings().allowDiagonalDescend.value;
        this.allowDiagonalAscend = Cheesecake.settings().allowDiagonalAscend.value;
        this.allowDownward = Cheesecake.settings().allowDownward.value;
        this.minFallHeight = 3; // Minimum fall height used by MovementFall
        this.maxFallHeightNoWater = Cheesecake.settings().maxFallHeightNoWater.value;
        this.maxFallHeightBucket = Cheesecake.settings().maxFallHeightBucket.value;
        float waterSpeedMultiplier = 1.0f;
        OUTER: for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments = cheesecake.getPlayerContext()
                .player()
                .getItemBySlot(slot)
                .getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                List<EnchantmentAttributeEffect> effects = enchant.value()
                    .getEffects(EnchantmentEffectComponents.ATTRIBUTES);
                for (EnchantmentAttributeEffect effect : effects) {
                    if (effect.attribute().is(Attributes.WATER_MOVEMENT_EFFICIENCY.unwrapKey().get())) {
                        waterSpeedMultiplier = effect.amount().calculate(itemEnchantments.getLevel(enchant));
                        break OUTER;
                    }
                }
            }
        }
        this.waterWalkSpeed = ActionCosts.WALK_ONE_IN_WATER_COST * (1 - waterSpeedMultiplier) + ActionCosts.WALK_ONE_BLOCK_COST * waterSpeedMultiplier;
        this.breakBlockAdditionalCost = Cheesecake.settings().blockBreakAdditionalPenalty.value;
        this.backtrackCostFavoringCoefficient = Cheesecake.settings().backtrackCostFavoringCoefficient.value;
        this.jumpPenalty = Cheesecake.settings().jumpPenalty.value;
        this.walkOnWaterOnePenalty = Cheesecake.settings().walkOnWaterOnePenalty.value;
        this.allowWalkOnMagmaBlocks = Cheesecake.settings().allowWalkOnMagmaBlocks.value;
        // why cache these things here, why not let the movements just get directly from settings?
        // because if some movements are calculated one way and others are calculated another way,
        // then you get a wildly inconsistent path that isn't optimal for either scenario.
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
    }

    public final ICheesecake getCheesecake() {
        return cheesecake;
    }

    public BlockState get(int x, int y, int z) {
        return bsi.get0(x, y, z); // laughs maniacally
    }

    public boolean isLoaded(int x, int z) {
        return bsi.isLoaded(x, z);
    }

    public BlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    public Block getBlock(int x, int y, int z) {
        return get(x, y, z).getBlock();
    }

    public double costOfPlacingAt(int x, int y, int z, BlockState current) {
        if (!hasThrowaway) { // only true if allowPlace is true, see constructor
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        if (!worldBorder.canPlaceAt(x, z)) {
            return COST_INF;
        }
        if (!Cheesecake.settings().allowPlaceInFluidsSource.value && current.getFluidState().isSource()) {
            return COST_INF;
        }
        if (!Cheesecake.settings().allowPlaceInFluidsFlow.value && !current.getFluidState().isEmpty() && !current.getFluidState().isSource()) {
            return COST_INF;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
        if (!allowBreak && !allowBreakAnyway.contains(current.getBlock())) {
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        return 1;
    }

    public double placeBucketCost() {
        return placeBlockCost; // shrug
    }

    public boolean isPossiblyProtected(int x, int y, int z) {
        // TODO more protection logic here; see #220
        return false;
    }
}
