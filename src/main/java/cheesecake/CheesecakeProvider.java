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

package cheesecake;

import cheesecake.api.ICheesecake;
import cheesecake.api.ICheesecakeProvider;
import cheesecake.api.cache.IWorldScanner;
import cheesecake.api.command.ICommandSystem;
import cheesecake.api.schematic.ISchematicSystem;
import cheesecake.cache.FasterWorldScanner;
import cheesecake.command.CommandSystem;
import cheesecake.command.ExampleCheesecakeControl;
import cheesecake.utils.schematic.SchematicSystem;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.MinecraftClient;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class CheesecakeProvider implements ICheesecakeProvider {

    private final List<ICheesecake> all;
    private final List<ICheesecake> allView;

    public CheesecakeProvider() {
        this.all = new CopyOnWriteArrayList<>();
        this.allView = Collections.unmodifiableList(this.all);

        // Setup chat control, just for the primary instance
        final Cheesecake primary = (Cheesecake) this.createCheesecake(MinecraftClient.getInstance());
        primary.registerBehavior(ExampleCheesecakeControl::new);
    }

    @Override
    public ICheesecake getPrimaryCheesecake() {
        return this.all.get(0);
    }

    @Override
    public List<ICheesecake> getAllCheesecakes() {
        return this.allView;
    }

    @Override
    public synchronized ICheesecake createCheesecake(MinecraftClient minecraft) {
        ICheesecake cheesecake = this.getCheesecakeForMinecraft(minecraft);
        if (cheesecake == null) {
            this.all.add(cheesecake = new Cheesecake(minecraft));
        }
        return cheesecake;
    }

    @Override
    public synchronized boolean destroyCheesecake(ICheesecake cheesecake) {
        return cheesecake != this.getPrimaryCheesecake() && this.all.remove(cheesecake);
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return FasterWorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
