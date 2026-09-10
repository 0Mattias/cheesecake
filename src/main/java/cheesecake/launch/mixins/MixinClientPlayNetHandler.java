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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author Brady
 * @since 8/3/2018
 */
@Mixin(ClientPacketListener.class)
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

    @Inject(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void sendChatMessageMessage(String string, CallbackInfo ci) {
        ChatEvent event = new ChatEvent(string);
        ICheesecake cheesecake = CheesecakeAPI.getProvider()
                .getCheesecakeForPlayer(Minecraft.getInstance().player);
        if (cheesecake == null) {
            return;
        }
        cheesecake.getGameEventHandler().onSendChatMessage(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void postHandleChunkData(ClientboundLevelChunkWithLightPacket packetIn, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            LocalPlayer player = icheesecake.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.POST,
                                !packetIn.isSkippable() ? ChunkEvent.Type.POPULATE_FULL
                                        : ChunkEvent.Type.POPULATE_PARTIAL,
                                packetIn.getX(),
                                packetIn.getZ()));
            }
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"))
    private void preChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            LocalPlayer player = icheesecake.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.PRE, ChunkEvent.Type.UNLOAD, packet.pos().x(), packet.pos().z()));
            }
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"))
    private void postChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            LocalPlayer player = icheesecake.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.POST, ChunkEvent.Type.UNLOAD, packet.pos().x(), packet.pos().z()));
            }
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void postHandleBlockChange(ClientboundBlockUpdatePacket packetIn, CallbackInfo ci) {
        if (!Cheesecake.settings().repackOnAnyBlockChange.value) {
            return;
        }
        if (!CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(packetIn.getBlockState().getBlock())) {
            return;
        }
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            LocalPlayer player = icheesecake.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                icheesecake.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.POST,
                                ChunkEvent.Type.POPULATE_FULL,
                                packetIn.getPos().getX() >> 4,
                                packetIn.getPos().getZ() >> 4));
            }
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void postHandleMultiBlockChange(ClientboundSectionBlocksUpdatePacket packetIn, CallbackInfo ci) {
        ICheesecake cheesecake = CheesecakeAPI.getProvider()
                .getCheesecakeForConnection((ClientPacketListener) (Object) this);
        if (cheesecake == null) {
            return;
        }

        List<Pair<BlockPos, BlockState>> changes = new ArrayList<>();
        packetIn.runUpdates((mutPos, state) -> {
            changes.add(new Pair<>(mutPos.immutable(), state));
        });
        if (changes.isEmpty()) {
            return;
        }
        cheesecake.getGameEventHandler().onBlockChange(new BlockChangeEvent(
                ChunkPos.containing(changes.get(0).first()),
                changes));
    }

    @Inject(method = "handlePlayerCombatKill", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;shouldShowDeathScreen()Z"))
    private void onPlayerDeath(ClientboundPlayerCombatKillPacket packetIn, CallbackInfo ci) {
        for (ICheesecake icheesecake : CheesecakeAPI.getProvider().getAllCheesecakes()) {
            LocalPlayer player = icheesecake.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
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
