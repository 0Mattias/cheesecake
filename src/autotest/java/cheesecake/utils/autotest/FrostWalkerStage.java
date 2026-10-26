package cheesecake.utils.autotest;

import cheesecake.api.pathing.movement.ActionCosts;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.pathing.movement.CalculationContext;
import cheesecake.pathing.movement.movements.MovementTraverse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prices four one-block walks with the pathfinder's own cost function, {@link MovementTraverse#cost},
 * first with nothing on the player's feet and then wearing Frost Walker II: into a water source,
 * onto a lava source, onto a waterlogged stair and onto plain stone. The rigs float above the
 * platform, out of the way of the stages after this one and taken away again at the end, with
 * every destination cell walled in so that nothing flows; the cost function reads only the
 * destination column and the block under the source, so the source's feet cell is stone too.
 * What has to hold: a walk into water costs WALK_ONE_IN_WATER_COST while the player has no
 * Depth Strider, a lava source is never walkable, a waterlogged block prices like the block it is
 * and throws nothing, and the Frost Walker level comes from the boots.
 */
public final class FrostWalkerStage extends Stage {

    private static final int DY = 8;
    private static final int SPACING = 4;
    private static final String[] RIGS = {"water", "lava", "waterlogged stairs", "stone"};
    private static final int WATER = 0;
    private static final int LAVA = 1;
    private static final int STAIRS = 2;
    private static final int STONE = 3;
    private static final String BOOTS = "minecraft:diamond_boots[minecraft:enchantments={\"minecraft:frost_walker\":2}]";

    private BetterBlockPos base;
    private int phase;
    private int phaseTick;
    private final List<String> wrong = new ArrayList<>();

    @Override
    public String name() {
        return "frost-walker";
    }

    @Override
    protected int timeoutTicks() {
        return 1200;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.base = new BetterBlockPos(p.x - 6, p.y + DY, p.z - 4);
        String me = this.t.playerName();
        // a clear of an empty inventory is reported as a failure, so give it something to clear
        command("give " + me + " minecraft:stick 1");
        command("clear " + me);
        teleport(p.x + 0.5, p.y + 1, p.z + 0.5);
        for (int i = 0; i < RIGS.length; i++) {
            int sx = this.base.x + i * SPACING;
            int y = this.base.y;
            int z = this.base.z;
            set(sx, y - 1, z, "minecraft:stone");
            set(sx, y, z, "minecraft:stone");
            for (int level = y - 1; level <= y; level++) {
                set(sx - 1, level, z - 1, "minecraft:stone");
                set(sx + 1, level, z - 1, "minecraft:stone");
                set(sx, level, z - 2, "minecraft:stone");
            }
            set(sx, y - 2, z - 1, "minecraft:stone");
            String floor = switch (i) {
                case LAVA -> "minecraft:lava";
                case STAIRS -> "minecraft:oak_stairs[waterlogged=true]";
                default -> "minecraft:stone";
            };
            set(sx, y - 1, z - 1, floor);
            if (i == WATER) {
                set(sx, y, z - 1, "minecraft:water");
            }
        }
    }

    private void set(int x, int y, int z, String block) {
        command("setblock " + x + " " + y + " " + z + " " + block);
    }

    @Override
    protected boolean tick() {
        logProgress();
        this.phaseTick++;
        switch (this.phase) {
            case 0: {
                if (!commandsDone()) {
                    return false;
                }
                if (!rigsVisible()) {
                    check(this.phaseTick < 200, "the rigs never reached the client: " + rigReport());
                    return false;
                }
                this.t.log("rigs: " + rigReport());
                price("without boots", 0);
                command("item replace entity " + this.t.playerName() + " armor.feet with " + BOOTS);
                this.phase = 1;
                this.phaseTick = 0;
                return false;
            }
            case 1: {
                if (!commandsDone()) {
                    return false;
                }
                int level = frostWalkerOnFeet();
                if (level != 2) {
                    check(this.phaseTick < 200, "the boots never reached the client; feet=" + this.t.player().getItemBySlot(EquipmentSlot.FEET));
                    return false;
                }
                this.t.log("wearing " + this.t.player().getItemBySlot(EquipmentSlot.FEET) + " with Frost Walker " + level);
                price("with Frost Walker II", 2);
                // take the rigs and the boots away again for the stages after this one
                command("fill " + (this.base.x - 1) + " " + (this.base.y - 2) + " " + (this.base.z - 2) + " "
                        + (this.base.x + 3 * SPACING + 1) + " " + this.base.y + " " + this.base.z + " minecraft:air");
                command("item replace entity " + this.t.playerName() + " armor.feet with minecraft:air");
                this.phase = 2;
                this.phaseTick = 0;
                return false;
            }
            default: {
                if (!commandsDone()) {
                    return false;
                }
                check(this.wrong.isEmpty(), this.wrong.size() + " wrong: " + String.join("; ", this.wrong));
                this.t.log("the costs are right without boots and with Frost Walker");
                return true;
            }
        }
    }

    private void price(String label, int expectedLevel) {
        CalculationContext c = new CalculationContext(this.t.cheesecake);
        this.t.log(label + ": frostWalker=" + c.frostWalker + " waterWalkSpeed=" + f(c.waterWalkSpeed)
                + " (WALK_ONE_BLOCK_COST=" + f(ActionCosts.WALK_ONE_BLOCK_COST)
                + ", WALK_ONE_IN_WATER_COST=" + f(ActionCosts.WALK_ONE_IN_WATER_COST) + ")");
        expect(c.frostWalker == expectedLevel, label + ": frostWalker is " + c.frostWalker + ", expected " + expectedLevel);
        Double water = cost(c, label, WATER);
        Double lava = cost(c, label, LAVA);
        Double stairs = cost(c, label, STAIRS);
        Double stone = cost(c, label, STONE);
        expect(water != null && Math.abs(water - ActionCosts.WALK_ONE_IN_WATER_COST) < 1e-6,
                label + ": a walk into water costs " + f(water) + ", expected WALK_ONE_IN_WATER_COST "
                        + f(ActionCosts.WALK_ONE_IN_WATER_COST) + " as the player has no Depth Strider");
        expect(lava != null && lava >= ActionCosts.COST_INF,
                label + ": a walk onto a lava source costs " + f(lava) + ", expected COST_INF");
        expect(stairs != null && stairs < ActionCosts.COST_INF,
                label + ": a walk onto waterlogged stairs " + (stairs == null ? "threw" : "costs " + f(stairs)) + ", expected the cost of a stair");
        expect(stone != null && stone < ActionCosts.COST_INF,
                label + ": a walk onto stone " + (stone == null ? "threw" : "costs " + f(stone)));
        expect(stairs != null && stone != null && Math.abs(stairs - stone) < 1e-6,
                label + ": waterlogged stairs and stone should price alike");
    }

    private Double cost(CalculationContext c, String label, int rig) {
        int sx = this.base.x + rig * SPACING;
        try {
            double cost = MovementTraverse.cost(c, sx, this.base.y, this.base.z, sx, this.base.z - 1);
            this.t.log(label + ", one block onto " + RIGS[rig] + ": " + (cost >= ActionCosts.COST_INF ? "COST_INF" : f(cost)));
            return cost;
        } catch (RuntimeException e) {
            this.t.log(label + ", one block onto " + RIGS[rig] + ": threw " + e);
            return null;
        }
    }

    private void expect(boolean ok, String message) {
        if (!ok) {
            this.wrong.add(message);
            this.t.log("WRONG: " + message);
        }
    }

    private static String f(Double d) {
        return d == null ? "null" : String.format(Locale.ROOT, "%.3f", d);
    }

    private BlockState at(int x, int y, int z) {
        return this.t.mc.level.getBlockState(new BlockPos(x, y, z));
    }

    private BlockState floor(int rig) {
        return at(this.base.x + rig * SPACING, this.base.y - 1, this.base.z - 1);
    }

    private boolean rigsVisible() {
        BlockState waterFeet = at(this.base.x + WATER * SPACING, this.base.y, this.base.z - 1);
        return waterFeet.getBlock() == Blocks.WATER && waterFeet.getFluidState().isSource()
                && floor(LAVA).getBlock() == Blocks.LAVA && floor(LAVA).getFluidState().isSource()
                && floor(STAIRS).getBlock() instanceof StairBlock && floor(STAIRS).getValue(BlockStateProperties.WATERLOGGED)
                && floor(STONE).getBlock() == Blocks.STONE;
    }

    private String rigReport() {
        return "water feet " + at(this.base.x + WATER * SPACING, this.base.y, this.base.z - 1)
                + "; lava floor " + floor(LAVA) + "; stairs floor " + floor(STAIRS) + "; stone floor " + floor(STONE);
    }

    private int frostWalkerOnFeet() {
        ItemStack boots = this.t.player().getItemBySlot(EquipmentSlot.FEET);
        ItemEnchantments enchantments = boots.getEnchantments();
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(Enchantments.FROST_WALKER)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }
}
