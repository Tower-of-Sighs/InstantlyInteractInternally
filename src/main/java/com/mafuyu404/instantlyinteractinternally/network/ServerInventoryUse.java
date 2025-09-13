package com.mafuyu404.instantlyinteractinternally.network;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.utils.Utils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerInventoryUse {
    public enum ActionType {
        BLOCK_INTERACT,
        ITEM_USE
    }

    private final int containerSlotIndex;
    private final ActionType actionType;

    public ServerInventoryUse(int containerSlotIndex, ActionType actionType) {
        this.containerSlotIndex = containerSlotIndex;
        this.actionType = actionType;
    }

    public static void encode(ServerInventoryUse msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.containerSlotIndex);
        buffer.writeEnum(msg.actionType);
    }

    public static ServerInventoryUse decode(FriendlyByteBuf buffer) {
        int idx = buffer.readVarInt();
        ActionType type = buffer.readEnum(ActionType.class);
        return new ServerInventoryUse(idx, type);
    }

    public static void handle(ServerInventoryUse msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.containerMenu == null) return;

            Slot slot;
            try {
                slot = player.containerMenu.getSlot(msg.containerSlotIndex);
            } catch (IndexOutOfBoundsException e) {
                return;
            }
            if (slot == null || !slot.hasItem()) return;

            switch (msg.actionType) {
                case BLOCK_INTERACT -> {
                    Utils.interactBlockInSandbox(slot.getItem(), player);
                }
                case ITEM_USE -> {
                    var stack = slot.getItem();
                    if (stack.getItem() instanceof BlockItem) {
                        Utils.interactBlockInSandbox(stack, player);
                        return;
                    }
                    boolean used = Utils.useUsableItemInstant(slot, player);
                    if (!used) {
                        Utils.consumeItemInstant(slot, player);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
        Instantlyinteractinternally.LOGGER.info("成功发包");
    }
}