package love.ikaros.minecraft.block;

import love.ikaros.minecraft.SynapseMod;
import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block CRYSTAL_OF_THE_RUINS = register("crystal_of_the_ruins",new AmethystBlock(AbstractBlock.Settings.create().mapColor(MapColor.PURPLE).strength(1.5F).sounds(BlockSoundGroup.AMETHYST_BLOCK).requiresTool()));

    public static Block register(String id,Block block) {
        registerBlockItems(id,block);
        return Registry.register(Registries.BLOCK,new Identifier(SynapseMod.MOD_ID,id),block);
    }

    public static void registerBlockItems(String id,Block block){
        Registry.register(Registries.ITEM,new Identifier(SynapseMod.MOD_ID,id),new BlockItem(block,new Item.Settings()));
    }

    public static void registerModBlocks(){

    }

}
