package com.mafuyu404.instantlyinteractinternally.mixin;

import com.mafuyu404.instantlyinteractinternally.client.ClientKeybinds;
import com.mafuyu404.instantlyinteractinternally.network.NetworkHandler;
import com.mafuyu404.instantlyinteractinternally.network.ServerInventoryUse;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class)
public class AbstractContainerScreenMixin extends Screen {
    @Shadow
    @Nullable
    protected Slot hoveredSlot;
    @Final
    @Shadow
    protected AbstractContainerMenu menu;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    private boolean tryQuickUse(InputConstants.Key key) {
        if (!hasShiftDown() || !ClientKeybinds.matches(ClientKeybinds.QUICK_USE, key)) {
            return false;
        }
        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem()) return false;

        ServerInventoryUse.ActionType action =
                (slot.getItem().getItem() instanceof BlockItem)
                        ? ServerInventoryUse.ActionType.BLOCK_INTERACT
                        : ServerInventoryUse.ActionType.ITEM_USE;

        NetworkHandler.CHANNEL.sendToServer(new ServerInventoryUse(slot.index, action));
        return true;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void i3_quickUseOnKey(int keyCode, int scanCode, int modifiers,
                                  CallbackInfoReturnable<Boolean> cir) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (tryQuickUse(key)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void i3_quickUseOnMouse(double mouseX, double mouseY, int button,
                                    CallbackInfoReturnable<Boolean> cir) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        if (this.hoveredSlot != null && tryQuickUse(key)) {
            cir.setReturnValue(true);
        }
    }
}