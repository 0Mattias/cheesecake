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

import cheesecake.api.process.IElytraProcess;
import cheesecake.api.utils.BetterBlockPos;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Flies to a goal with {@code #elytra}. Each trip covers a different part of the process: the climb
 * above the build limit on a long Overworld trip, the automatic takeoff from a ledge, routing
 * through Nether terrain below the roof, flight over the roof, and each time the landing search at
 * the end, which has to report a spot and put the player on the ground.
 */
public final class ElytraStage extends Stage {

    public enum Trip {
        /**
         * From a lone pillar with nothing above it, dropped into the air to start gliding, more
         * than {@code elytraLongDistanceThreshold} blocks east to a pad built for the landing.
         * The pathfinder has to climb above the build limit for the straight part of the trip.
         */
        OVERWORLD_ABOVE_LIMIT,
        /**
         * Back from the pad to the platform, with {@code elytraAutoJump} finding the edge of the
         * pad and jumping off it.
         */
        OVERWORLD_AUTO_JUMP,
        /**
         * Below the bedrock roof, from an open spot the stage finds in the terrain, through terrain
         * the pathfinder predicts from the seed, landing wherever the process finds room. The
         * takeoff is a fall: the player stands on a block placed in the spot until the process has
         * its path, and the block is then taken away.
         */
        NETHER_BELOW_ROOF,
        /**
         * On top of the bedrock roof, with {@code elytraAllowAboveRoof} on, landing on the roof.
         */
        NETHER_ABOVE_ROOF
    }

    private enum Step { SETUP, ARMED, FLYING }

    private static final int OVERWORLD_DISTANCE = 600;
    private static final int NETHER_DISTANCE = 400;
    /**
     * Where the trip below the roof may end, relative to its start, tried in this order: the
     * landing search only accepts a few kinds of floor, and whole biomes have none.
     */
    private static final int[][] DESTINATION_OFFSETS = {{NETHER_DISTANCE, 0}, {0, NETHER_DISTANCE}, {-NETHER_DISTANCE, 0}, {0, -NETHER_DISTANCE}};
    private static final int PAD_RADIUS = 8;
    /**
     * Where the pillar for the first trip stands, south of the platform: outside the platform's
     * chunks, so the chunk's heightmap is below the pillar and the sky counts as clear.
     */
    private static final int PILLAR_DZ = 48;
    private static final int PILLAR_Y = 270;
    private static final int ROOF_Y = 128;
    /**
     * How far above the floor of the landing column the goal is put, inside its clear air.
     */
    private static final int LANDING_GOAL_ABOVE_FLOOR = 8;
    /**
     * What the landing search wants above a floor: this many clear blocks straight up, and a
     * bubble of {@link #LANDING_BUBBLE} blocks in every direction around the point that high up.
     */
    private static final int LANDING_COLUMN = 15;
    private static final int LANDING_BUBBLE = 4;
    private static final int START_CAVERN_HEIGHT = 12;
    /**
     * No landing floor closer to the lava sea than this. The sea is at y 31.
     */
    private static final int LOWEST_LANDING_FLOOR = 56;
    /**
     * How far around an anchor the scans look. The client keeps four chunks around the player
     * loaded, which is just enough.
     */
    private static final int SCAN_RADIUS = 52;
    /**
     * How far above the ground the player is dropped to start gliding when the trip does not use
     * the automatic takeoff.
     */
    private static final int DROP_HEIGHT = 40;
    private static final int MIN_GLIDING_TICKS = 60;
    /** What the process says when a path calculation has hung; see armed(). */
    private static final String PATH_HANG = "did not answer for thirty seconds";
    /** What the process says when it gives up. Any of them fails the stage on the spot. */
    private static final String[] GIVING_UP = {
            "Failed to compute path to destination", "Failed to compute a walking path", "no fireworks", "Not taking off",
            "Still on the ground after thirty seconds",
    };

    private final Trip trip;
    private Step step = Step.SETUP;
    private int stepTick;
    private BetterBlockPos origin;
    private BetterBlockPos pad;
    private BetterBlockPos netherAnchor;
    /**
     * For the trip below the roof: the roof above the destination area, and the air inside the
     * cavern found there to fly to.
     */
    private BetterBlockPos netherDestinationAnchor;
    private BetterBlockPos landing;
    /** For the trip below the roof: the block the player stands on until the process has a path. */
    private BetterBlockPos perch;
    private int netherPhase;
    private int candidate;
    private int goalX;
    private Integer goalY;
    private int goalZ;
    private boolean relaunched;
    private double maxY;
    /**
     * The highest node the process planned, which says whether it routed above the build limit
     * even when the player flies a little under the nodes.
     */
    private int maxPathY = Integer.MIN_VALUE;
    private int glidingTicks;
    private boolean padBuilt;

    public ElytraStage(Trip trip) {
        this.trip = trip;
    }

    @Override
    public String name() {
        return "elytra-" + this.trip.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    protected int timeoutTicks() {
        return 6000;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.pad = new BetterBlockPos(p.x + OVERWORLD_DISTANCE, p.y, p.z);
        this.netherAnchor = new BetterBlockPos(p.x >> 3, ROOF_Y, p.z >> 3);
        this.netherDestinationAnchor = destinationAnchor(0);
        switch (this.trip) {
            case OVERWORLD_ABOVE_LIMIT:
                command("setblock " + p.x + " " + (PILLAR_Y - 1) + " " + (p.z + PILLAR_DZ) + " minecraft:obsidian");
                teleport(p.x + 0.5, PILLAR_Y, p.z + PILLAR_DZ + 0.5);
                equip();
                // Load the chunks around the destination and put an obsidian pad there for the
                // landing search to find. Nothing else that high is a safe block.
                command("forceload add " + (this.pad.x - PAD_RADIUS) + " " + (this.pad.z - PAD_RADIUS) + " " + (this.pad.x + PAD_RADIUS) + " " + (this.pad.z + PAD_RADIUS));
                break;
            case OVERWORLD_AUTO_JUMP:
                teleport(this.pad.x + 0.5, this.pad.y + 1, this.pad.z + 0.5);
                equip();
                break;
            case NETHER_BELOW_ROOF:
                // The roof is the one place in the Nether that is safe to arrive at without knowing
                // the terrain: bedrock at 127, air above. Look at the destination area first: the
                // landing search needs a cavern there, and the terrain is only known once loaded.
                teleport("minecraft:the_nether", this.netherDestinationAnchor.x + 0.5, ROOF_Y, this.netherDestinationAnchor.z + 0.5);
                equip();
                break;
            case NETHER_ABOVE_ROOF:
                teleport("minecraft:the_nether", this.netherAnchor.x + 0.5, ROOF_Y, this.netherAnchor.z + 0.5);
                equip();
                break;
        }
    }

    private void equip() {
        command("item replace entity " + this.t.playerName() + " armor.chest with minecraft:elytra");
        command("give " + this.t.playerName() + " minecraft:firework_rocket 64");
        if (this.trip == Trip.NETHER_BELOW_ROOF || this.trip == Trip.NETHER_ABOVE_ROOF) {
            // On a slow runner the flight is imprecise enough to clip lava now and then; the trip
            // is about routing and landing, so a dip must not end the run.
            command("effect give " + this.t.playerName() + " minecraft:fire_resistance 1200 0 true");
        }
    }

    @Override
    protected boolean tick() {
        logProgress();
        switch (this.step) {
            case SETUP:
                return setup();
            case ARMED:
                return armed();
            default:
                return flying();
        }
    }

    private void advance(Step step) {
        this.step = step;
        this.stepTick = ticks();
    }

    private int ticksInStep() {
        return ticks() - this.stepTick;
    }

    private boolean inNether() {
        return this.t.ctx().world() != null && this.t.ctx().world().dimension() == Level.NETHER;
    }

    /**
     * Gets the world and the player ready for the trip, then issues the goal and {@code #elytra}.
     */
    private boolean setup() {
        if (!commandsDone()) {
            return false;
        }
        switch (this.trip) {
            case OVERWORLD_ABOVE_LIMIT:
                if (!this.padBuilt && ticksInStep() >= 60) {
                    // Give the forced chunks time to generate before building on them.
                    command("fill " + (this.pad.x - PAD_RADIUS) + " " + this.pad.y + " " + (this.pad.z - PAD_RADIUS) + " "
                            + (this.pad.x + PAD_RADIUS) + " " + this.pad.y + " " + (this.pad.z + PAD_RADIUS) + " minecraft:obsidian");
                    command("forceload remove all");
                    this.padBuilt = true;
                    this.stepTick = ticks();
                    return false;
                }
                if (!this.padBuilt || ticksInStep() < 20 || !this.t.player().onGround()) {
                    return false;
                }
                check(feet().y == PILLAR_Y, "expected to stand on the pillar at y " + PILLAR_Y + ", standing at " + feet());
                settings().elytraAutoJump.value = false;
                settings().elytraAllowAboveBuildLimit.value = true;
                launch(this.pad.x, null, this.pad.z);
                return false;
            case OVERWORLD_AUTO_JUMP:
                if (ticksInStep() < 20 || !this.t.player().onGround()) {
                    return false;
                }
                check(feet().y == this.pad.y + 1, "expected to stand on the pad, standing at " + feet());
                settings().elytraAutoJump.value = true;
                launch(this.t.platform.x, null, this.t.platform.z);
                return false;
            case NETHER_BELOW_ROOF:
                if (!inNether() || ticksInStep() < 60) {
                    return false;
                }
                if (this.netherPhase < 2 && !areaLoaded(this.netherPhase == 0 ? this.netherDestinationAnchor : this.netherAnchor)) {
                    // Fresh Nether chunks take a while to generate and arrive; scan once they have.
                    check(ticksInStep() < 1200, "the chunks around " + (this.netherPhase == 0 ? this.netherDestinationAnchor : this.netherAnchor) + " never loaded");
                    return false;
                }
                if (this.netherPhase == 0) {
                    // A place to land: a safe floor with the air the landing search wants above it.
                    // The goal goes into that air, so the search starts right above the floor.
                    this.landing = findLandingColumn(this.netherDestinationAnchor);
                    if (this.landing == null) {
                        this.candidate++;
                        check(this.candidate < DESTINATION_OFFSETS.length, "found nowhere to land around any of the destinations tried");
                        this.netherDestinationAnchor = destinationAnchor(this.candidate);
                        this.t.log("nowhere to land there; looking around " + this.netherDestinationAnchor + " instead");
                        teleport(this.netherDestinationAnchor.x + 0.5, ROOF_Y, this.netherDestinationAnchor.z + 0.5);
                        this.stepTick = ticks();
                        return false;
                    }
                    this.t.log("will land in the cavern at " + this.landing + "; going back to the start");
                    teleport(this.netherAnchor.x + 0.5, ROOF_Y, this.netherAnchor.z + 0.5);
                    this.netherPhase = 1;
                    this.stepTick = ticks();
                    return false;
                }
                if (this.netherPhase == 1) {
                    if (!this.t.player().onGround() || feet().y != ROOF_Y) {
                        return false;
                    }
                    BetterBlockPos spot = findOpenSpot(this.netherAnchor);
                    check(spot != null, "found no open spot in the loaded Nether terrain around " + this.netherAnchor);
                    // The player stands on a block placed under the spot while the process works
                    // out its path, and armed() takes the block away once it has one. Dropped
                    // straight in, the glide was a race against that calculation: the process
                    // neither steers nor fires a rocket until the path exists, so a calculation
                    // slow enough -- it queues behind the repacking of every loaded chunk, on a
                    // runner that software rendering already keeps busy -- let the player fall
                    // untouched to the cavern floor, and with elytraAutoJump off there is no
                    // getting up from there.
                    this.perch = spot.below();
                    this.t.log("found an open spot at " + spot + "; standing on a block under it, facing " + this.landing
                            + ", until the process has a path");
                    setBlock(this.perch, "minecraft:barrier");
                    // Facing the landing column, for the same reason the other trips face their
                    // goal before dropping: the glide begins in whatever direction the camera is
                    // pointing. Level with the spot, so the pitch stays flat and only the yaw is set.
                    teleportFacing(spot.x + 0.5, spot.y, spot.z + 0.5,
                            this.landing.x + 0.5, spot.y, this.landing.z + 0.5);
                    this.netherPhase = 2;
                    return false;
                }
                if (!commandsDone() || !this.t.player().onGround() || feet().y != this.perch.y + 1) {
                    return false; // not standing on the block yet
                }
                settings().elytraAutoJump.value = false;
                settings().elytraNetherSeed.value = AutoTestContext.SEED;
                settings().elytraPredictTerrain.value = true;
                settings().elytraAllowAboveRoof.value = false;
                launch(this.landing.x, this.landing.y, this.landing.z);
                return false;
            default:
                if (!inNether() || ticksInStep() < 40 || !this.t.player().onGround()) {
                    return false;
                }
                check(feet().y == ROOF_Y, "expected to stand on the roof at y " + ROOF_Y + ", standing at " + feet());
                settings().elytraAutoJump.value = false;
                settings().elytraNetherSeed.value = AutoTestContext.SEED;
                settings().elytraPredictTerrain.value = true;
                settings().elytraAllowAboveRoof.value = true;
                launch(feet().x + NETHER_DISTANCE, null, feet().z);
                return false;
        }
    }

    /**
     * Sets the goal and starts the process. With a y the goal is a GoalBlock and the process flies
     * to that altitude; without one it is a GoalXZ, flown at y 64 in the Nether and wherever the
     * pathfinder likes elsewhere.
     */
    private void launch(int x, Integer y, int z) {
        check(this.t.player().getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), "no elytra equipped");
        check(this.t.countItems(Items.FIREWORK_ROCKET) >= 32, "only " + this.t.countItems(Items.FIREWORK_ROCKET) + " fireworks");
        settings().elytraTermsAccepted.value = true;
        this.origin = feet();
        this.goalX = x;
        this.goalY = y;
        this.goalZ = z;
        String goal = y == null ? x + " " + z : x + " " + y + " " + z;
        this.t.markChat();
        this.t.log("flying from " + this.origin + " to " + goal);
        this.t.chatCommand("goal " + goal);
        this.t.chatCommand("elytra");
        check(this.t.cheesecake.getElytraProcess().isActive(), "the elytra process did not start");
        advance(Step.ARMED);
    }

    /**
     * The process is waiting to fly. Until it has a path it does nothing for a player in the air --
     * no steering, no rocket -- so the trips that take off by being dropped wait here, on whatever
     * they are standing on, until the path exists, and only then let go. Dropped before that, the
     * glide was a race against the calculation, and on a slow runner the calculation lost. The
     * automatic takeoff finds its own way off the pad.
     */
    private boolean armed() {
        if (this.trip == Trip.OVERWORLD_AUTO_JUMP) {
            advance(Step.FLYING);
            return false;
        }
        if (this.t.saidSinceMark(PATH_HANG)) {
            // A known nether-pathfinder defect, recorded in the README: a worker thread the
            // library starts can read a stale stop flag and exit at birth, after which the terrain
            // generator waits for ever. The process has abandoned that context; a fresh one is
            // built at another address. Once is the library's fault; twice would be ours.
            check(!this.relaunched, "the path calculation hung twice");
            this.relaunched = true;
            this.t.log("the path calculation hung, which is the nether-pathfinder worker-thread defect; starting the process again on a fresh context");
            launch(this.goalX, this.goalY, this.goalZ);
            return false;
        }
        checkNotGivenUp();
        IElytraProcess elytra = this.t.cheesecake.getElytraProcess();
        check(elytra.isActive(), "the elytra process stopped before it had a path");
        if (elytra.getPath().isEmpty()) {
            return false;
        }
        this.t.log("the process has a path of " + elytra.getPath().size() + " nodes after " + ticksInStep() + " ticks");
        if (this.trip == Trip.NETHER_BELOW_ROOF) {
            this.t.log("taking the block away; the fall into the cavern is the takeoff");
            setBlock(this.perch, "minecraft:air");
        } else {
            BetterBlockPos feet = feet();
            this.t.log("dropping the player from " + DROP_HEIGHT + " blocks up to start gliding, facing "
                    + this.goalX + " " + this.goalZ);
            // Facing the goal, because a glide begins in whatever direction the camera happens to
            // point and the camera is wherever the last stage left it. Dropped facing away, the bot
            // spends the first seconds of the flight turning around while it loses height, and on a
            // slow runner it reaches the ground before it reaches the goal: one run started at yaw
            // 115 with the goal due south, flew to z -73 instead of z +348 and finished the stage
            // standing in a cavern at y 23. The flight and the landing are what this stage is for,
            // not recovering from a takeoff pointed the wrong way.
            teleportFacing(feet.x + 0.5, feet.y + DROP_HEIGHT, feet.z + 0.5,
                    this.goalX + 0.5, feet.y + DROP_HEIGHT, this.goalZ + 0.5);
        }
        advance(Step.FLYING);
        return false;
    }

    private void checkNotGivenUp() {
        check(!this.t.saidSinceMark(PATH_HANG), "the path calculation hung");
        for (String failure : GIVING_UP) {
            check(!this.t.saidSinceMark(failure), "the process gave up: " + failure);
        }
    }

    /** Sets a block in the dimension the player is in; a plain setblock from the console acts in the Overworld. */
    private void setBlock(BetterBlockPos pos, String block) {
        command("execute in " + this.t.ctx().world().dimension().identifier()
                + " run setblock " + pos.x + " " + pos.y + " " + pos.z + " " + block);
    }

    private boolean flying() {
        BetterBlockPos feet = feet();
        this.maxY = Math.max(this.maxY, this.t.player().getY());
        for (BetterBlockPos node : this.t.cheesecake.getElytraProcess().getPath()) {
            this.maxPathY = Math.max(this.maxPathY, node.y);
        }
        if (this.t.player().isFallFlying()) {
            this.glidingTicks++;
        }
        checkNotGivenUp();
        if (!this.t.saidSinceMark("Done :)") || this.t.cheesecake.getElytraProcess().isActive() || !this.t.player().onGround()) {
            return false;
        }
        double travelled = xzDistance(feet, this.origin.x, this.origin.z);
        double left = xzDistance(feet, this.goalX, this.goalZ);
        this.t.log("landed at " + feet + " after " + ticks() + " ticks, " + this.glidingTicks + " of them gliding; travelled "
                + (int) travelled + " blocks, " + (int) left + " from the goal, highest point y " + (int) this.maxY + ", highest path node y " + this.maxPathY);
        check(this.glidingTicks >= MIN_GLIDING_TICKS, "glided for only " + this.glidingTicks + " ticks");
        check(this.t.saidSinceMark("searching for safe landing spot"), "no landing search was reported");
        check(this.t.saidSinceMark("Found potential landing spot"), "no landing spot was reported");
        switch (this.trip) {
            case OVERWORLD_ABOVE_LIMIT:
                check(this.maxPathY > this.t.ctx().world().getMaxY(), "the path never went above the build limit; highest node y " + this.maxPathY);
                check(feet.y == this.pad.y + 1 && left <= PAD_RADIUS + 4, "did not land on the pad at " + this.pad);
                break;
            case OVERWORLD_AUTO_JUMP:
                check(feet.y == this.t.platform.y + 1 && left <= PlatformStage.RADIUS + 4, "did not land on the platform at " + this.t.platform);
                break;
            case NETHER_BELOW_ROOF:
                check(this.maxY < ROOF_Y, "flew above the roof with elytraAllowAboveRoof off; highest point y " + (int) this.maxY);
                check(travelled >= NETHER_DISTANCE * 0.6, "covered only " + (int) travelled + " of " + NETHER_DISTANCE + " blocks");
                check(left <= 64, "landed " + (int) left + " blocks from the goal");
                break;
            default:
                check(feet.y == ROOF_Y, "did not land on the roof");
                check(left <= 64, "landed " + (int) left + " blocks from the goal");
                break;
        }
        return true;
    }

    /**
     * Looks through the loaded chunks around a point on the roof for an open spot to drop into: a
     * column of air at least {@link #START_CAVERN_HEIGHT} blocks tall and thirteen wide all the
     * way. The terrain is fixed by the seed, so the result is the same on every run.
     *
     * @return the point two blocks under the ceiling of the deepest such spot, or null
     */
    private BetterBlockPos findOpenSpot(BetterBlockPos anchor) {
        Level world = this.t.ctx().world();
        int radius = SCAN_RADIUS;
        int half = 6;
        BetterBlockPos best = null;
        int bestHeight = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Every second column, not every fourth. The anchor follows the platform, which follows
        // where the player happened to stand, so it moves by a block or two between worlds; on a
        // four-block grid that shift put the only columns that qualify between the samples, and
        // a run failed here with the chunks loaded and the cavern present.
        for (int x = anchor.x - radius; x <= anchor.x + radius; x += 2) {
            for (int z = anchor.z - radius; z <= anchor.z + radius; z += 2) {
                if (!chunksLoaded(world, x - half, z - half, x + half, z + half)) {
                    continue;
                }
                int top = -1;
                for (int y = ROOF_Y - 8; y >= 32; y--) {
                    if (world.getBlockState(pos.set(x, y, z)).isAir()) {
                        if (top < 0) {
                            top = y;
                        }
                        continue;
                    }
                    if (top >= 0) {
                        int run = top - y;
                        if (run > bestHeight && run >= START_CAVERN_HEIGHT && isAirBox(world, x - half, y + 1, z - half, x + half, top, z + half)) {
                            best = new BetterBlockPos(x, top - 2, z);
                            bestHeight = run;
                        }
                        top = -1;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Looks through the loaded chunks around a point on the roof for a column the landing search
     * accepts: a floor the search treats as safe with safe blocks all around it,
     * {@link #LANDING_COLUMN} blocks of air above it, and an all-air bubble around the point that
     * high up.
     *
     * @return the point {@link #LANDING_GOAL_ABOVE_FLOOR} blocks above the floor, or null
     */
    private BetterBlockPos findLandingColumn(BetterBlockPos anchor) {
        Level world = this.t.ctx().world();
        int radius = SCAN_RADIUS;
        int safeFloors = 0;
        int clearColumns = 0;
        BetterBlockPos best = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = anchor.x - radius; x <= anchor.x + radius; x += 2) {
            for (int z = anchor.z - radius; z <= anchor.z + radius; z += 2) {
                if (!chunksLoaded(world, x - LANDING_BUBBLE, z - LANDING_BUBBLE, x + LANDING_BUBBLE, z + LANDING_BUBBLE)) {
                    continue;
                }
                // Highest floor first: a route to a low cavern skims the lava sea, and on a slow
                // runner the flight is not precise enough for that.
                for (int floor = ROOF_Y - 8 - LANDING_COLUMN - LANDING_BUBBLE; floor >= LOWEST_LANDING_FLOOR; floor--) {
                    if (best != null && floor <= best.y - LANDING_GOAL_ABOVE_FLOOR) {
                        break;
                    }
                    if (!isSafeFloor(world.getBlockState(pos.set(x, floor, z)).getBlock())
                            || !world.getBlockState(pos.set(x, floor + 1, z)).isAir()) {
                        continue;
                    }
                    boolean ok = true;
                    for (int dx = -1; dx <= 1 && ok; dx++) {
                        for (int dz = -1; dz <= 1 && ok; dz++) {
                            ok = isSafeFloor(world.getBlockState(pos.set(x + dx, floor, z + dz)).getBlock());
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    safeFloors++;
                    if (!isAirBox(world, x, floor + 1, z, x, floor + LANDING_COLUMN, z)) {
                        continue;
                    }
                    clearColumns++;
                    int bubble = floor + LANDING_COLUMN;
                    if (isAirBox(world, x - LANDING_BUBBLE, bubble - LANDING_BUBBLE, z - LANDING_BUBBLE, x + LANDING_BUBBLE, bubble + LANDING_BUBBLE, z + LANDING_BUBBLE)) {
                        best = new BetterBlockPos(x, floor + LANDING_GOAL_ABOVE_FLOOR, z);
                        break;
                    }
                }
            }
        }
        if (best == null) {
            this.t.log("no landing column: " + safeFloors + " safe floors, " + clearColumns + " of them with " + LANDING_COLUMN + " blocks of air, none with the bubble");
        } else {
            this.t.log("landing column with its floor at " + (best.y - LANDING_GOAL_ABOVE_FLOOR) + " under " + best + ", the highest of " + clearColumns + " clear columns");
        }
        return best;
    }

    /**
     * The Nether blocks the landing search treats as safe to land on.
     */
    private static boolean isSafeFloor(Block block) {
        return block == Blocks.NETHERRACK || block == Blocks.GRAVEL || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL;
    }

    private BetterBlockPos destinationAnchor(int candidate) {
        return new BetterBlockPos(this.netherAnchor.x + DESTINATION_OFFSETS[candidate][0], ROOF_Y, this.netherAnchor.z + DESTINATION_OFFSETS[candidate][1]);
    }

    /**
     * Whether every chunk the scans look at around the anchor has arrived.
     */
    private boolean areaLoaded(BetterBlockPos anchor) {
        Level world = this.t.ctx().world();
        for (int cx = (anchor.x - SCAN_RADIUS - LANDING_BUBBLE) >> 4; cx <= (anchor.x + SCAN_RADIUS + LANDING_BUBBLE) >> 4; cx++) {
            for (int cz = (anchor.z - SCAN_RADIUS - LANDING_BUBBLE) >> 4; cz <= (anchor.z + SCAN_RADIUS + LANDING_BUBBLE) >> 4; cz++) {
                if (!world.getChunkSource().hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean chunksLoaded(Level world, int minX, int minZ, int maxX, int maxZ) {
        return world.getChunkSource().hasChunk(minX >> 4, minZ >> 4) && world.getChunkSource().hasChunk(maxX >> 4, maxZ >> 4)
                && world.getChunkSource().hasChunk(minX >> 4, maxZ >> 4) && world.getChunkSource().hasChunk(maxX >> 4, minZ >> 4);
    }

    private static boolean isAirBox(Level world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!world.getBlockState(pos.set(x, y, z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
