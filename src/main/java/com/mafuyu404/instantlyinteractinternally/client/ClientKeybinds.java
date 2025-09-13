package com.mafuyu404.instantlyinteractinternally.client;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientKeybinds {
    public static final KeyMapping QUICK_USE = new KeyMapping(
            "key." + Instantlyinteractinternally.MODID + ".quick_use",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_2,
            "key.categories.gameplay"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(QUICK_USE);
    }

    public static boolean matches(KeyMapping km, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return km.getKey().getType() == InputConstants.Type.KEYSYM && km.getKey().getValue() == key.getValue();
        } else if (key.getType() == InputConstants.Type.SCANCODE) {
            return km.getKey().getType() == InputConstants.Type.SCANCODE && km.getKey().getValue() == key.getValue();
        } else if (key.getType() == InputConstants.Type.MOUSE) {
            return km.getKey().getType() == InputConstants.Type.MOUSE && km.getKey().getValue() == key.getValue();
        }
        return false;
    }
}