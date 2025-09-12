package com.mafuyu404.instantlyinteractinternally;

import com.mafuyu404.instantlyinteractinternally.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Instantlyinteractinternally.MODID)
public class Instantlyinteractinternally {

    public static final String MODID = "instantlyinteractinternally";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Instantlyinteractinternally() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        NetworkHandler.register();
//        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
