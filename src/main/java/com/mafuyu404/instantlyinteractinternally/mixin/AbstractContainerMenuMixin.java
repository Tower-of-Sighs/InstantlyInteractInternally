package com.mafuyu404.instantlyinteractinternally.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z", at = @At("HEAD"), cancellable = true)
    private static void stillTrue(ContainerLevelAccess access, Player player, Block block, CallbackInfoReturnable<Boolean> cir) {
//        System.out.print(Arrays.toString(Thread.currentThread().getStackTrace())+"\n");
        access.execute((level, blockPos) -> {
            if (blockPos.equals(player.blockPosition())) {
                cir.setReturnValue(true);
            }
        });
    }

    private static boolean isChecking() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();

            if (className.contains("ServerInteractBlock")) {
                return true;
            }
        }
        return false;
    }
}
