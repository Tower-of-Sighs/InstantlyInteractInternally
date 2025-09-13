package com.mafuyu404.instantlyinteractinternally.mixin;

import com.mafuyu404.instantlyinteractinternally.client.ClientKeybinds;
import com.mafuyu404.instantlyinteractinternally.network.NetworkHandler;
import com.mafuyu404.instantlyinteractinternally.network.ServerInventoryUse;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class)
public class InventoryScreenMixin extends Screen {
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    protected InventoryScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void i3_quickUseOnKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!hasShiftDown() || !ClientKeybinds.QUICK_USE.matches(keyCode, scanCode)) return;

        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem()) return;

        ServerInventoryUse.ActionType action =
                (slot.getItem().getItem() instanceof BlockItem)
                        ? ServerInventoryUse.ActionType.BLOCK_INTERACT
                        : ServerInventoryUse.ActionType.ITEM_USE;

        NetworkHandler.CHANNEL.sendToServer(new ServerInventoryUse(slot.index, action));
        cir.setReturnValue(true);
    }
}