package love.ikaros.minecraft.item;

import love.ikaros.minecraft.SynapseMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item COMMON_CARD = registerItems("tool/common_card",new Item(new Item.Settings()));
    public static final Item ALMIGHTY_CARD = registerItems("tool/almighty_card",new Item(new Item.Settings()));
    public static final Item APOLLON_ARROWS = registerItems("weapon/apollon_arrows",new TippedArrowItem(new Item.Settings()));
    public static final Item CHRYSAOR_SWORD = registerItems("weapon/chrysaor_sword", new SwordItem(ToolMaterials.DIAMOND, 500, -0.2F, new Item.Settings()));


    public static Item registerItems(String id,Item item) {
        return Registry.register(Registries.ITEM,RegistryKey.of(Registries.ITEM.getKey(),new Identifier(SynapseMod.MOD_ID,id)),item);
    }



    private static void addItemToToolItemGroup(FabricItemGroupEntries entries){

        entries.add(COMMON_CARD);
        entries.add(ALMIGHTY_CARD);
    }

    private static void addItemToWeaponItemGroup(FabricItemGroupEntries entries){

        entries.add(APOLLON_ARROWS);
        entries.add(CHRYSAOR_SWORD);
    }
    public static void registerItems(){
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(ModItems::addItemToToolItemGroup);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::addItemToWeaponItemGroup);

    }



}
