package love.ikaros.minecraft;

import love.ikaros.minecraft.block.ModBlocks;
import love.ikaros.minecraft.item.ModItemGroups;
import love.ikaros.minecraft.item.ModItems;
import love.ikaros.minecraft.logic.FlightHandler;
import love.ikaros.minecraft.sound.ModSoundEvents;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SynapseMod implements ModInitializer {
	public static final String MOD_ID = "synapsemod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModItems.registerItems();
		ModItemGroups.registerGroups();
		ModBlocks.registerModBlocks();
		ModSoundEvents.registerSounds();
		// 注册飞行逻辑
		FlightHandler.register();

		LOGGER.info("SYNAPSE MOD INITIALIZED");
	}
}