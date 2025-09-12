package com.mafuyu404.instantlyinteractinternally.network;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Instantlyinteractinternally.MODID, "sync_data"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int packetId = 0;
        CHANNEL.registerMessage(packetId++, ServerInteractBlock.class, ServerInteractBlock::encode, ServerInteractBlock::decode, ServerInteractBlock::handle);
    }

    public static void sendToClient(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}