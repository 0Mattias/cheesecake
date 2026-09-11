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

import cheesecake.api.utils.BetterBlockPos;

/**
 * Runs {@code #explore} on the terrain the seed generated and watches the bot set off. Exploring
 * has no end a test could wait for: it stops when every chunk in range is cached, which takes far
 * longer than a test run. What this checks is that the process picks an uncached chunk, produces a
 * goal for it and walks, which is where it would break.
 * <p>
 * This stage runs before the platform is built, while the player is still on the ground. On the
 * platform the bot would walk off the edge and fall to its death.
 */
public final class ExploreStage extends Stage {

    /**
     * Far enough to be past the chunk it started in, so it is really heading for another one, and
     * near enough to be a short walk over whatever the seed put there.
     */
    private static final double TRAVELLED = 16.0D;

    private BetterBlockPos origin;
    private boolean exploring;
    private boolean returning;

    @Override
    public String name() {
        return "explore";
    }

    @Override
    protected int timeoutTicks() {
        return 2400;
    }

    @Override
    protected void start() {
        this.t.stopEverything();
    }

    @Override
    protected boolean tick() {
        logProgress();
        if (!this.exploring) {
            if (ticks() < 20 || !this.t.player().onGround()) {
                return false;
            }
            this.origin = feet();
            this.t.chatCommand("explore");
            check(this.t.saidSinceMark("Exploring from"), "the command did not report where it was exploring from");
            check(this.t.cheesecake.getExploreProcess().isActive(), "the explore process did not start");
            this.t.log("exploring from " + this.origin);
            this.exploring = true;
            return false;
        }
        check(!this.t.saidSinceMark("Failed"), "the explore process reported a failure");
        double travelled = xzDistance(this.origin, feet().x, feet().z);
        if (this.returning) {
            if (!commandsDone() || !feet().equals(this.origin) || !this.t.player().onGround()) {
                return false;
            }
            this.t.log("back at " + this.origin + " after " + ticks() + " ticks");
            return true;
        }
        if (travelled >= TRAVELLED) {
            this.t.log("walked " + travelled + " blocks from where it started exploring after " + ticks() + " ticks");
            this.t.stopEverything();
            this.t.cheesecake.getExploreProcess().onLostControl();
            check(!this.t.cheesecake.getExploreProcess().isActive(), "the explore process kept going after cancel");
            // Walk back to where the stage found the player. This is the one stage that runs before
            // the platform is built, and the platform is built around wherever the player is
            // standing, so wandering off here would move every stage after it onto different
            // terrain -- including the Nether the elytra flights cross.
            teleport(this.origin.x + 0.5, this.origin.y, this.origin.z + 0.5);
            this.returning = true;
            return false;
        }
        check(this.t.cheesecake.getExploreProcess().isActive(),
                "the explore process stopped after " + travelled + " blocks");
        return false;
    }
}
