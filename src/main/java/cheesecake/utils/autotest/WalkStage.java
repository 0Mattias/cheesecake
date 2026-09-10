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

import cheesecake.api.pathing.goals.GoalXZ;
import cheesecake.api.utils.BetterBlockPos;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Screenshot;

/**
 * Walks a fixed distance along +x from wherever the world put the player. The goal is drawn as the
 * box for the first half and as the beacon beam for the second, and a screenshot taken in front of
 * the beam checks that the beam actually reached the screen.
 */
public final class WalkStage extends Stage {

    private static final int DISTANCE = 120;
    /**
     * Remaining distance at which the goal rendering switches from the box to the beacon beam.
     */
    private static final int BEACON_DISTANCE = 60;
    private static final int SCREENSHOT_DISTANCE = 30;
    /**
     * The beam is drawn in the goal colour, pure green. Nothing else in a plains world at noon is
     * saturated green with no red or blue, so counting such pixels tells the beam apart from grass.
     */
    private static final int MIN_BEAM_PIXELS = 300;

    private BetterBlockPos start;
    private GoalXZ goal;
    private boolean beacon;
    private int beaconTick;
    private volatile int screenshot; // 0 not taken, 1 requested, 2 done
    private volatile int beamPixels;
    private volatile String screenshotError;
    private volatile Path screenshotFile;

    @Override
    public String name() {
        return "walk";
    }

    @Override
    protected int timeoutTicks() {
        return 4800;
    }

    @Override
    protected void start() {
        // With freeLook, the default, the camera keeps the yaw the spawn gave it while only the
        // body turns to walk. The screenshot needs the camera on the path, so let the mod turn it.
        settings().freeLook.value = false;
        this.start = feet();
        this.goal = new GoalXZ(this.start.x + DISTANCE, this.start.z);
        this.t.log("starting at " + this.start + ", goal " + this.goal);
        this.t.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    protected boolean tick() {
        BetterBlockPos feet = feet();
        int remaining = this.goal.getX() - feet.x;
        if (!this.beacon && remaining < BEACON_DISTANCE) {
            // The goal is a GoalXZ, so from here on every frame draws the beam through the custom
            // pipelines instead of the box through the line layers.
            this.t.log("switching the goal rendering from the box to the beacon beam, " + remaining + " blocks out");
            settings().renderGoalXZBeacon.value = true;
            this.beacon = true;
            this.beaconTick = ticks();
        }
        if (this.beacon && this.screenshot == 0 && remaining < SCREENSHOT_DISTANCE && ticks() - this.beaconTick >= 20) {
            takeScreenshot();
        }
        logProgress();
        if (this.goal.isInGoal(feet)) {
            check(this.screenshot != 0, "reached the goal before a screenshot of the beam was taken");
            if (this.screenshot != 2) {
                return false; // the framebuffer readback completes on a later frame
            }
            check(this.screenshotError == null, "screenshot failed: " + this.screenshotError);
            check(this.beamPixels >= MIN_BEAM_PIXELS, "only " + this.beamPixels + " beam-coloured pixels in " + this.screenshotFile);
            this.t.log("reached " + feet + " from " + this.start + " after " + ticks() + " ticks; " + this.beamPixels + " beam pixels in " + this.screenshotFile);
            settings().freeLook.value = true;
            return true;
        }
        if (!this.t.cheesecake.getCustomGoalProcess().isActive() && ticks() % 40 == 0) {
            // The process gives up after a failed calculation; ask again until the failure budget runs out.
            this.t.log("goal process is idle, re-issuing the goal");
            this.t.cheesecake.getCustomGoalProcess().setGoalAndPath(this.goal);
        }
        return false;
    }

    private void takeScreenshot() {
        this.screenshot = 1;
        this.t.log("taking a screenshot of the beam");
        try {
            Path dir = this.t.mc.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            Path file = dir.resolve("goal-beacon.png");
            Screenshot.takeScreenshot(this.t.mc.gameRenderer.mainRenderTarget(), image -> {
                try (NativeImage img = image) {
                    int count = 0;
                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            int argb = img.getPixel(x, y);
                            int r = (argb >> 16) & 0xFF;
                            int g = (argb >> 8) & 0xFF;
                            int b = argb & 0xFF;
                            if (g > 150 && r < 80 && b < 80) {
                                count++;
                            }
                        }
                    }
                    img.writeToFile(file);
                    this.beamPixels = count;
                    this.screenshotFile = file;
                } catch (Exception e) {
                    this.screenshotError = e.toString();
                } finally {
                    this.screenshot = 2;
                }
            });
        } catch (Exception e) {
            this.screenshotError = e.toString();
            this.screenshot = 2;
        }
    }
}
