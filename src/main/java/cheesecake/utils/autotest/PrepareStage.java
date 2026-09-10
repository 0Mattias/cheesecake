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

/**
 * Pins the world to a clear noon so the run, and the screenshot it takes, do not depend on when the
 * weather or the night would have come.
 */
public final class PrepareStage extends Stage {

    @Override
    public String name() {
        return "prepare";
    }

    @Override
    protected int timeoutTicks() {
        return 200;
    }

    @Override
    protected void start() {
        command("time set noon");
        command("gamerule advance_time false");
        command("weather clear");
        command("gamerule advance_weather false");
    }

    @Override
    protected boolean tick() {
        return commandsDone();
    }
}
