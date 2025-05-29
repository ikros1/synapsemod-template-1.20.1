package love.ikaros.minecraft.item;

import love.ikaros.minecraft.SynapseMod;
import love.ikaros.minecraft.block.ModBlocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static final RegistryKey<ItemGroup> SYNAPSE_GROUP = register("synapse_group");

    private static RegistryKey<ItemGroup> register(String id) {
        return RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier(SynapseMod.MOD_ID, id));
    }

    public static void registerGroups() {
        Registry.register(
                Registries.ITEM_GROUP,
                SYNAPSE_GROUP,
                ItemGroup.create(ItemGroup.Row.TOP, 7)
                        .displayName(Text.translatable("itemGroup.synapse_group"))
                        .icon(() -> new ItemStack(ModItems.ALMIGHTY_CARD))
                        .entries((displayContext, entries) -> {
                            entries.add(ModBlocks.CRYSTAL_OF_THE_RUINS);
                            entries.add(ModItems.COMMON_CARD);
                            entries.add(ModItems.ALMIGHTY_CARD);
                            entries.add(ModItems.CHRYSAOR_SWORD);
                            entries.add(ModItems.APOLLON_ARROWS);
                            entries.add(ModItems.APOLLON);

                        })
                        .build());

    }


}
