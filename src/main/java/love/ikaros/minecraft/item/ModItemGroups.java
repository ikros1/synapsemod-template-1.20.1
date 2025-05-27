package love.ikaros.minecraft.item;

import love.ikaros.minecraft.SynapseMod;
import love.ikaros.minecraft.block.ModBlocks;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
                            entries.add(ModItems.APOLLON_ARROWS);})
                        .build());

    }


}
