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

package cheesecake.launch.mixins;

import cheesecake.Cheesecake;
import cheesecake.api.CheesecakeAPI;
import cheesecake.api.ICheesecake;
import cheesecake.api.event.events.BlockChangeEvent;
import cheesecake.api.event.events.ChatEvent;
import cheesecake.api.event.events.ChunkEvent;
import cheesecake.api.event.events.type.EventState;
import cheesecake.api.utils.Pair;
import cheesecake.cache.CachedChunk;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
// import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Brady
 * @since 8/3/2018
 */
@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetHandler {

    // unused lol
    /*
     * @Inject(
     * method = "handleChunkData",
     * at = @At(
     * value = "INVOKE",
     * target =
     * "net/minecraft/client/multiplayer/ChunkProviderClient.func_212474_a(IILnet/minecraft/network/PacketBuffer;IZ)Lnet/minecraft/world/chunk/Chunk;"
     * )
     * )
     * private void preRead(SPacketChunkData packetIn, CallbackInfo ci) {
     * for (ICheesecake icheesecake :
     * CheesecakeAPI.getProvider().getAllCheesecakes()) {
     * ClientPlayerEntity player = icheesecake.getPlayerContext().player();
     * if (player != null && player.connection == (ClientPlayNetHandler) (Object)
     * this) {
     * icheesecake.getGameEventHandler().onChunkEvent(
     * new ChunkEvent(
     * EventState.PRE,
     * packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL :
     * ChunkEvent.Type.POPULATE_PARTIAL,
     * packetIn.getChunkX(),
     * packetIn.getChunkZ()
     * )
     * );
     * }
     * }
     * }
     */

    @Inject(method = "sendChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void sendChatMessageMessage(String string, CallbackInfo ci) {
        ChatEvent event = new ChatEvent(string);
        ICheesecake cheesecake = CheesecakeAPI.getProvider()
                .getCheesecakeForPlayer(MinecraftClient.getInstance().player);
        if (cheesecake == null) {
            return;
        }
        cheesecake.getGameEventHandler().onSendChatMessage(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onChunkData", at = @At("RETURN"))
    private void postHandleChunkData(ChunkDataS2CPacket packetIn, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            ClientPlayerEntity player = icheesecake.getPlayerContext().player();
            if (player != null && player.networkHandler == (ClientPlayNetworkHandler) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.POST,
                                !packetIn.isWritingErrorSkippable() ? ChunkEvent.Type.POPULATE_FULL
                                        : ChunkEvent.Type.POPULATE_PARTIAL,
                                packetIn.getChunkX(),
                                packetIn.getChunkZ()));
            }
        }
    }

    @Inject(method = "onUnloadChunk", at = @At("HEAD"))
    private void preChunkUnload(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            ClientPlayerEntity player = icheesecake.getPlayerContext().player();
            if (player != null && player.networkHandler == (ClientPlayNetworkHandler) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.PRE, ChunkEvent.Type.UNLOAD, packet.pos().x, packet.pos().z));
            }
        }
    }

    @Inject(method = "onUnloadChunk", at = @At("RETURN"))
    private void postChunkUnload(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            ClientPlayerEntity player = icheesecake.getPlayerContext().player();
            if (player != null && player.networkHandler == (ClientPlayNetworkHandler) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.POST, ChunkEvent.Type.UNLOAD, packet.pos().x, packet.pos().z));
            }
        }
    }

    @Inject(method = "onBlockUpdate", at = @At("RETURN"))
    private void postHandleBlockChange(BlockUpdateS2CPacket packetIn, CallbackInfo ci) {
        if (!Cheesecake.settings().repackOnAnyBlockChange.value) {
            return;
        }
        if (!CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(packetIn.getState().getBlock())) {
            return;
        }
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            ClientPlayerEntity player = icheesecake.getPlayerContext().player();
            if (player != null && player.networkHandler == (ClientPlayNetworkHandler) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.POST,
                                ChunkEvent.Type.POPULATE_FULL,
                                packetIn.getPos().getX() >> 4,
                                packetIn.getPos().getZ() >> 4));
            }
        }
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("RETURN"))
    private void postHandleMultiBlockChange(ChunkDeltaUpdateS2CPacket packetIn, CallbackInfo ci) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider()
                .getCheesecakeForConnection((ClientPlayNetworkHandler) (Object) this);
        if (cheesecake == null) {
            return;
        }

        List<Pair<BlockPos, BlockState>> changes = new ArrayList<>();
        packetIn.visitUpdates((mutPos, state) -> {
            changes.add(new Pair<>(mutPos.toImmutable(), state));
        });
        if (changes.isEmpty()) {
            return;
        }
        cheesecake.getGameEventHandler().onBlockChange(new BlockChangeEvent(
                new ChunkPos(changes.get(0).first()),
                changes));
    }

    @Inject(method = "onDeathMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;showsDeathScreen()Z"))
    private void onPlayerDeath(DeathMessageS2CPacket packetIn, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            ClientPlayerEntity player = icheesecake.getPlayerContext().player();
            if (player != null && player.networkHandler == (ClientPlayNetworkHandler) (Object) this) {
                icheesecake.getGameEventHandler().onPlayerDeath();
            }
        }
    }

    /*
     * @Inject(
     * method = "handleChunkData",
     * at = @At(
     * value = "INVOKE",
     * target =
     * "net/minecraft/world/chunk/Chunk.read(Lnet/minecraft/network/PacketBuffer;IZ)V"
     * )
     * )
     * private void preRead(SPacketChunkData packetIn, CallbackInfo ci) {
     * ICheesecake cheesecake =
     * CheesecakeAPI.getProvider().getCheesecakeForConnection((NetHandlerPlayClient)
     * (Object) this);
     * if (cheesecake == null) {
     * return;
     * }
     * cheesecake.getGameEventHandler().onChunkEvent(
     * new ChunkEvent(
     * EventState.PRE,
     * packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL :
     * ChunkEvent.Type.POPULATE_PARTIAL,
     * packetIn.getChunkX(),
     * packetIn.getChunkZ()
     * )
     * );
     * }
     * 
     * @Inject(
     * method = "handleChunkData",
     * at = @At("RETURN")
     * )
     * private void postHandleChunkData(SPacketChunkData packetIn, CallbackInfo ci)
     * {
     * ICheesecake cheesecake =
     * CheesecakeAPI.getProvider().getCheesecakeForConnection((NetHandlerPlayClient)
     * (Object) this);
     * if (cheesecake == null) {
     * return;
     * }
     * cheesecake.getGameEventHandler().onChunkEvent(
     * new ChunkEvent(
     * EventState.POST,
     * packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL :
     * ChunkEvent.Type.POPULATE_PARTIAL,
     * packetIn.getChunkX(),
     * packetIn.getChunkZ()
     * )
     * );
     * }
     * 
     * @Inject(
     * method = "handleBlockChange",
     * at = @At("RETURN")
     * )
     * private void postHandleBlockChange(SPacketBlockChange packetIn, CallbackInfo
     * ci) {
     * ICheesecake cheesecake =
     * CheesecakeAPI.getProvider().getCheesecakeForConnection((NetHandlerPlayClient)
     * (Object) this);
     * if (cheesecake == null) {
     * return;
     * }
     * 
     * final ChunkPos pos = new ChunkPos(packetIn.getBlockPosition().getX() >> 4,
     * packetIn.getBlockPosition().getZ() >> 4);
     * final Pair<BlockPos, IBlockState> changed = new
     * Pair<>(packetIn.getBlockPosition(), packetIn.getBlockState());
     * cheesecake.getGameEventHandler().onBlockChange(new BlockChangeEvent(pos,
     * Collections.singletonList(changed)));
     * }
     * 
     * @Inject(
     * method = "handleMultiBlockChange",
     * at = @At("RETURN")
     * )
     * private void postHandleMultiBlockChange(SPacketMultiBlockChange packetIn,
     * CallbackInfo ci) {
     * 
     * }
     * 
     */
}
