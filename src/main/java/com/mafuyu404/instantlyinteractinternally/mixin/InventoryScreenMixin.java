package com.mafuyu404.instantlyinteractinternally.mixin;

import com.mafuyu404.instantlyinteractinternally.network.NetworkHandler;
import com.mafuyu404.instantlyinteractinternally.network.ServerInteractBlock;
import com.mafuyu404.instantlyinteractinternally.utils.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.datafix.fixes.ChunkPalettedStorageFix;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InventoryScreen.class)
public class InventoryScreenMixin extends Screen {
    protected InventoryScreenMixin(Component p_96550_) {
        super(p_96550_);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void betterAmmoBox(Slot slot, int index, int key, ClickType clickType, CallbackInfo ci) {
        if (!hasShiftDown() || key != 1) return;
        if (slot.getItem().getItem() instanceof BlockItem) {
            Player player = Minecraft.getInstance().player;
//            BlockUtils.interactBlockFromItem(slot.getItem(), player);
            NetworkHandler.CHANNEL.sendToServer(new ServerInteractBlock(slot.getItem()));
            ci.cancel();
        }
    }
}
