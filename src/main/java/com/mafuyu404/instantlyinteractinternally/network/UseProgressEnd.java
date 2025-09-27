package com.mafuyu404.instantlyinteractinternally.network;

import com.mafuyu404.instantlyinteractinternally.client.ClientUseProgressOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UseProgressEnd {

    public static void encode(UseProgressEnd msg, FriendlyByteBuf buf) {
    }

    public static UseProgressEnd decode(FriendlyByteBuf buf) {
        return new UseProgressEnd();
    }

    public static void handle(UseProgressEnd msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientUseProgressOverlay::end));
        ctx.get().setPacketHandled(true);
    }
}