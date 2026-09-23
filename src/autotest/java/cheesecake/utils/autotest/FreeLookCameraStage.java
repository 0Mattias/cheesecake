package cheesecake.utils.autotest;

import cheesecake.api.pathing.goals.GoalBlock;
import cheesecake.api.utils.BetterBlockPos;
import net.minecraft.util.Mth;

/**
 * Walks north with free look on while the camera looks east, and at the start of every tick
 * reads the player's previous rotation, the value the renderer interpolates the camera from.
 * With free look the bot's aim is put on the player for the length of the entity tick and the
 * camera's own rotation restored afterwards, so the previous rotation must be the camera's as
 * well: if it is the bot's aim, the camera sweeps between the two directions on every frame.
 */
public final class FreeLookCameraStage extends Stage {

    private static final int SAMPLES = 40;
    private static final int START_DZ = 8;
    private static final int GOAL_DZ = -12;

    private GoalBlock goal;
    private boolean started;
    private boolean previousFreeLook;
    private int sampled;
    private int swept;

    @Override
    public String name() {
        return "free-look-camera";
    }

    @Override
    protected int timeoutTicks() {
        return 1200;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
        BetterBlockPos p = this.t.platform;
        this.previousFreeLook = settings().freeLook.value;
        settings().freeLook.value = true;
        // look east, along +x; the walk goes north, along -z, so the bot's aim is a quarter turn away
        teleportFacing(p.x + 0.5, p.y + 1, p.z + START_DZ + 0.5, p.x + 20, p.y + 1, p.z + START_DZ);
        this.goal = new GoalBlock(p.x, p.y + 1, p.z + GOAL_DZ);
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.started) {
            if (!commandsDone() || ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            this.t.log("camera yaw " + this.t.player().getYRot() + " at " + feet() + ", walking to " + this.goal + " with freeLook on");
            this.t.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
            this.started = true;
            return false;
        }
        if (this.sampled < SAMPLES) {
            if (!this.t.cheesecake.getPathingBehavior().isPathing()) {
                check(!this.goal.isInGoal(feet()), "arrived before " + SAMPLES + " ticks could be sampled");
                return false;
            }
            float yaw = this.t.player().getYRot();
            float previous = this.t.player().yRotO;
            float apart = Math.abs(Mth.wrapDegrees(previous - yaw));
            if (this.sampled < 8) {
                this.t.log("tick " + ticks() + ": camera yaw " + yaw + ", previous yaw " + previous + ", " + apart + " degrees apart");
            }
            if (apart > 30) {
                this.swept++;
            }
            this.sampled++;
            return false;
        }
        settings().freeLook.value = this.previousFreeLook;
        this.t.stopEverything();
        this.t.log(this.swept + " of " + this.sampled + " ticks began with the previous yaw more than 30 degrees from the camera's");
        check(this.swept == 0, this.swept + " of " + this.sampled + " ticks began with the previous rotation at the bot's aim instead of the camera's, so the camera sweeps between the two every frame");
        return true;
    }
}
