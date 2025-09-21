package com.mafuyu404.instantlyinteractinternally.mixin;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.utils.VirtualContainerGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    private static boolean isContainerSlot(Slot slot) {
        return slot != null && !(slot.container instanceof Inventory);
    }

    private static Player getPlayerFromMenu(AbstractContainerMenu self) {
        for (Slot slot : self.slots) {
            if (slot != null && slot.container instanceof Inventory inv) {
                return inv.player;
            }
        }
        return null;
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void i3_blockSelfAndNestedOnClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;

        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;

        if (slotId < 0 || slotId >= self.slots.size()) return;
        Slot target = self.slots.get(slotId);
        if (!isContainerSlot(target)) return;

        ItemStack carried = self.getCarried();
        if (carried == null || carried.isEmpty()) return;

        boolean intendsPlace =
                (clickType == ClickType.PICKUP) ||
                        (clickType == ClickType.QUICK_CRAFT) ||
                        (clickType == ClickType.SWAP);
        if (!intendsPlace) return;

        var s = VirtualContainerGuard.getSession(sp);
        if (s != null && !s.isContainerSession && s.containerMenu == self && carried.hasTag()) {
            String sid = carried.getTag().getString("i3_session");
            if (sid != null && !sid.isEmpty() && sid.equals(s.sessionId)) {
                Instantlyinteractinternally.LOGGER.warn(
                        "[Mixin] 阻止自包含: 在 {} 中尝试把 sid={} 放入其自身GUI槽位 玩家={}",
                        self.getClass().getSimpleName(), sid, sp.getGameProfile().getName());
                ci.cancel();
            }
        }
    }


    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void i3_blockSelfAndNestedOnMove(ItemStack stack, int startIndex, int endIndex, boolean reverse, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) return;

        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;

        boolean targetIncludesContainerSlot = false;
        int from = Math.min(startIndex, endIndex);
        int to = Math.max(startIndex, endIndex);
        if (from < 0) from = 0;
        if (to > self.slots.size()) to = self.slots.size();
        for (int i = from; i < to; i++) {
            if (isContainerSlot(self.slots.get(i))) {
                targetIncludesContainerSlot = true;
                break;
            }
        }
        if (!targetIncludesContainerSlot) return;

        Player p = getPlayerFromMenu(self);
        if (!(p instanceof ServerPlayer sp)) return;

        if (stack.hasTag()) {
            String sid = stack.getTag().getString("i3_session");
            var sActive = VirtualContainerGuard.getSession(sp);
            if (sActive != null && !sActive.isContainerSession && self == sActive.containerMenu
                    && sid != null && !sid.isEmpty() && sid.equals(sActive.sessionId)) {
                Instantlyinteractinternally.LOGGER.warn(
                        "[Mixin] 阻止自包含(快速移动): 在 {} 中尝试把 sid={} 快速移入其自身GUI",
                        self.getClass().getSimpleName(), sid);
                cir.setReturnValue(false);
            }
        }
    }
}