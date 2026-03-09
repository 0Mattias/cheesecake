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

package cheesecake.event;

import cheesecake.Cheesecake;
import cheesecake.api.event.events.*;
import cheesecake.api.event.events.type.EventState;
import cheesecake.api.event.listener.IEventBus;
import cheesecake.api.event.listener.IGameEventListener;
import cheesecake.api.utils.Helper;
import cheesecake.api.utils.Pair;
import cheesecake.cache.CachedChunk;
import cheesecake.cache.WorldProvider;
import cheesecake.utils.BlockStateInterface;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * @author Brady
 * @since 7/31/2018
 */
public final class GameEventHandler implements IEventBus, Helper {

    private final Cheesecake cheesecake;

    private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

    public GameEventHandler(Cheesecake cheesecake) {
        this.cheesecake = cheesecake;
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.IN) {
            try {
                cheesecake.bsi = new BlockStateInterface(cheesecake.getPlayerContext(), true);
            } catch (Exception ex) {
                ex.printStackTrace();
                cheesecake.bsi = null;
            }
        } else {
            cheesecake.bsi = null;
        }
        listeners.forEach(l -> l.onTick(event));
    }

    @Override
    public void onPostTick(TickEvent event) {
        listeners.forEach(l -> l.onPostTick(event));
    }

    @Override
    public final void onPlayerUpdate(PlayerUpdateEvent event) {
        listeners.forEach(l -> l.onPlayerUpdate(event));
    }

    @Override
    public final void onSendChatMessage(ChatEvent event) {
        listeners.forEach(l -> l.onSendChatMessage(event));
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        listeners.forEach(l -> l.onPreTabComplete(event));
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        EventState state = event.getState();
        ChunkEvent.Type type = event.getType();

        World world = cheesecake.getPlayerContext().world();

        // Whenever the server sends us to another dimension, chunks are unloaded
        // technically after the new world has been loaded, so we perform a check
        // to make sure the chunk being unloaded is already loaded.
        boolean isPreUnload = state == EventState.PRE
                && type == ChunkEvent.Type.UNLOAD
                && world.getChunkManager().getChunk(event.getX(), event.getZ(), null, false) != null;

        if (event.isPostPopulate() || isPreUnload) {
            cheesecake.getWorldProvider().ifWorldLoaded(worldData -> {
                WorldChunk chunk = world.getChunk(event.getX(), event.getZ());
                worldData.getCachedWorld().queueForPacking(chunk);
            });
        }


        listeners.forEach(l -> l.onChunkEvent(event));
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (Cheesecake.settings().repackOnAnyBlockChange.value) {
            final boolean keepingTrackOf = event.getBlocks().stream()
                    .map(Pair::second).map(BlockState::getBlock)
                    .anyMatch(CachedChunk.BLOCKS_TO_KEEP_TRACK_OF::contains);

            if (keepingTrackOf) {
                cheesecake.getWorldProvider().ifWorldLoaded(worldData -> {
                    final World world = cheesecake.getPlayerContext().world();
                    ChunkPos pos = event.getChunkPos();
                    worldData.getCachedWorld().queueForPacking(world.getChunk(pos.x, pos.z));
                });
            }
        }

        listeners.forEach(l -> l.onBlockChange(event));
    }

    @Override
    public final void onRenderPass(RenderEvent event) {
        listeners.forEach(l -> l.onRenderPass(event));
    }

    @Override
    public final void onWorldEvent(WorldEvent event) {
        WorldProvider cache = cheesecake.getWorldProvider();

        if (event.getState() == EventState.POST) {
            cache.closeWorld();
            if (event.getWorld() != null) {
                cache.initWorld(event.getWorld());
            }
        }

        listeners.forEach(l -> l.onWorldEvent(event));
    }

    @Override
    public final void onSendPacket(PacketEvent event) {
        listeners.forEach(l -> l.onSendPacket(event));
    }

    @Override
    public final void onReceivePacket(PacketEvent event) {
        listeners.forEach(l -> l.onReceivePacket(event));
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        listeners.forEach(l -> l.onPlayerRotationMove(event));
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        listeners.forEach(l -> l.onPlayerSprintState(event));
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        listeners.forEach(l -> l.onBlockInteract(event));
    }

    @Override
    public void onPlayerDeath() {
        listeners.forEach(IGameEventListener::onPlayerDeath);
    }

    @Override
    public void onPathEvent(PathEvent event) {
        listeners.forEach(l -> l.onPathEvent(event));
    }

    @Override
    public final void registerEventListener(IGameEventListener listener) {
        this.listeners.add(listener);
    }
}
