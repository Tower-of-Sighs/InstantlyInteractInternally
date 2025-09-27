package com.mafuyu404.instantlyinteractinternally.network;

import com.mafuyu404.instantlyinteractinternally.client.ClientUseProgressOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UseProgressStart {
    private final int durationTicks;

    public UseProgressStart(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public static void encode(UseProgressStart msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.durationTicks);
    }

    public static UseProgressStart decode(FriendlyByteBuf buf) {
        return new UseProgressStart(buf.readVarInt());
    }

    public static void handle(UseProgressStart msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientUseProgressOverlay.start(msg.durationTicks)));
        ctx.get().setPacketHandled(true);
    }
}