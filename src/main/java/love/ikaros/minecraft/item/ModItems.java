package love.ikaros.minecraft.item;

import love.ikaros.minecraft.SynapseMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {

    public static final Item COMMON_CARD = registerItems("tool/common_card", new Item(new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item ALMIGHTY_CARD = registerItems("tool/almighty_card", new Item(new Item.Settings().rarity(Rarity.RARE).food(new FoodComponent.Builder()
            .hunger(10)
            .saturationModifier(100.2F)
            .statusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 24000, 8), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 24000, 64), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 24000, 64), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 24000, 64), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 24000, 4), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 24000, 4), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 24000, 4), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 24000, 4), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 24000, 64), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.LUCK, 24000, 1024), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 24000, 64), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.HASTE, 24000, 64), 1.0F)
            .statusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 24000, 64), 1.0F)
            .alwaysEdible()
            .build())));
    public static final Item APOLLON_ARROWS = registerItems("weapon/apollon_arrows", new ArrowItem(new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item CHRYSAOR_SWORD = registerItems("weapon/chrysaor_sword", new SwordItem(ToolMaterials.DIAMOND, 800, -0.2F, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item APOLLON = registerItems("weapon/apollon", new ModBowItem(new Item.Settings().maxDamage(38400).rarity(Rarity.EPIC)));

    // 补全法杖定义，设置稀有度为史诗
    public static final Item HIYOLI_WAND = registerItems("weapon/hiyoli_wand",
            new RandomStaffItem(new Item.Settings()
                    .rarity(Rarity.EPIC)
                    .maxCount(1)
                    .maxDamage(500)
            ));

    public static final Item NYMPH_WAND = registerItems("weapon/nymph_wand",
            new SphereChaosStaffItem(new Item.Settings()
                    .rarity(Rarity.EPIC)
                    .maxCount(1)
                    .maxDamage(500)
            ));

    public static Item registerItems(String id, Item item) {
        return Registry.register(Registries.ITEM, RegistryKey.of(Registries.ITEM.getKey(), new Identifier(SynapseMod.MOD_ID, id)), item);
    }

    private static void addItemToToolItemGroup(FabricItemGroupEntries entries){
        entries.add(COMMON_CARD);
        entries.add(ALMIGHTY_CARD);
    }

    private static void addItemToWeaponItemGroup(FabricItemGroupEntries entries){
        entries.add(APOLLON_ARROWS);
        entries.add(CHRYSAOR_SWORD);
        entries.add(APOLLON);
        entries.add(HIYOLI_WAND);
        entries.add(NYMPH_WAND);
    }

    public static void registerItems(){
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(ModItems::addItemToToolItemGroup);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::addItemToWeaponItemGroup);
    }
}