package com.mcprotector;

import com.mcprotector.command.FactionCommands;
import com.mcprotector.command.FactionRelationCommands;
import com.mcprotector.data.FactionData;
import com.mcprotector.dynmap.DynmapBridge;
import com.mcprotector.protection.ClaimProtectionHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.common.MinecraftForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(McProtectorMod.MOD_ID)
public class McProtectorMod {
    public static final String MOD_ID = "mcprotector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public McProtectorMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.register(new ClaimProtectionHandler());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DynmapBridge::init);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        FactionCommands.register(event.getDispatcher());
        FactionRelationCommands.register(event.getDispatcher());
    }
}
