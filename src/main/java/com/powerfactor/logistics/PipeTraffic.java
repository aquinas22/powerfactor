package com.powerfactor.logistics;

import com.powerfactor.PowerFactor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Server to client: something just moved through a pipe network along {@code path} (source block,
 * the pipes in order, target block). Items carry the stack; fluids the fluid id. Visual only.
 */
public record PipeTraffic(List<BlockPos> path, ItemStack item, Identifier fluid) implements CustomPacketPayload {
    public static final Type<PipeTraffic> TYPE = new Type<>(PowerFactor.id("pipe_traffic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeTraffic> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(1100)), PipeTraffic::path,
            ItemStack.OPTIONAL_STREAM_CODEC, PipeTraffic::item,
            Identifier.STREAM_CODEC, PipeTraffic::fluid,
            PipeTraffic::new);

    public static java.util.function.Consumer<PipeTraffic> clientHandler = t -> {};

    @Override
    public Type<PipeTraffic> type() {
        return TYPE;
    }
}
