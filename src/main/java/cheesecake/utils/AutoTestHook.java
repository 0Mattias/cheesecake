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

package cheesecake.utils;

import cheesecake.Cheesecake;
import cheesecake.api.event.listener.IGameEventListener;

import java.util.Optional;

/**
 * The seam between the mod and its in-world test harness.
 * <p>
 * The harness lives in the {@code autotest} source set, which {@code runClient} has on its
 * classpath and the jar task does not, so {@code cheesecake.utils.autotest} is absent from a
 * released jar. That keeps the mod from referring to the driver by type: this class looks it up by
 * name instead, and quietly does nothing when it is not there.
 */
public final class AutoTestHook {

    /**
     * Whether the environment asked for the in-world test. Reading the variable here rather than in
     * the driver keeps the check available to a jar that does not carry the driver at all.
     */
    public static final boolean ENABLED = "true".equals(System.getenv("CHEESECAKE_AUTO_TEST"));

    private static final String DRIVER = "cheesecake.utils.autotest.CheesecakeAutoTest";

    private AutoTestHook() {}

    /**
     * The test driver as an event listener, or empty when the test was not asked for or the harness
     * is not on the classpath.
     */
    public static Optional<IGameEventListener> listener(Cheesecake cheesecake) {
        if (!ENABLED) {
            return Optional.empty();
        }
        try {
            return Optional.of((IGameEventListener) Class.forName(DRIVER)
                    .getConstructor(Cheesecake.class)
                    .newInstance(cheesecake));
        } catch (ClassNotFoundException e) {
            // A released jar. Say so, because the variable was set and nothing is about to happen.
            System.out.println("[cheesecake-autotest] CHEESECAKE_AUTO_TEST is set, but " + DRIVER
                    + " is not on the classpath: the in-world test is not part of a released jar."
                    + " Run it from a checkout with CHEESECAKE_AUTO_TEST=true ./gradlew runClient.");
            return Optional.empty();
        } catch (ReflectiveOperationException e) {
            // The harness is present but unusable, which is a broken build rather than a mode the
            // mod should start up in.
            throw new IllegalStateException("could not start " + DRIVER, e);
        }
    }
}
