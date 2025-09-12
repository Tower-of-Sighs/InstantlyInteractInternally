package com.mafuyu404.instantlyinteractinternally.network;

import com.mafuyu404.instantlyinteractinternally.utils.BlockUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerInteractBlock {
    private final ItemStack itemStack;

    public ServerInteractBlock(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public static void encode(ServerInteractBlock msg, FriendlyByteBuf buffer) {
        buffer.writeItem(msg.itemStack);
    }

    public static ServerInteractBlock decode(FriendlyByteBuf buffer) {
        return new ServerInteractBlock(buffer.readItem());
    }

    public static void handle(ServerInteractBlock msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            InteractionResult interactionResult = BlockUtils.interactBlockFromItem(msg.itemStack, player);
//            System.out.print(interactionResult+"\n");
        });
        ctx.get().setPacketHandled(true);
    }
}
